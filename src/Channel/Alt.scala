package ox.scl.channel

class AltAbort  extends ox.scl.Stopped

/** An `alt` construct, with branches corresponding to `branches`. */ 
// class Alt(branches: Array[AtomicAltBranch]) extends AltT{
class Alt(branches: => AltBranch) extends AltT{
  /* The execution of an Alt goes through several steps.
   * 
   * 1. Registration: the Alt registers itself with the ports of its branches.
   * If a port indicates at this point that it is ready, then execution skips
   * to the deregistration stage.
   * 
   * 2. Waiting: if at least one branch was feasible, but none was ready, then
   * the Alt waits.
   * 
   * 3. Call-backs: if a port becomes ready, it calls the `maybeReceive`
   * function, which causes the main thread to be woken.  If a port is closed,
   * it calls the `portClosed` function; if subsequently no branch is
   * feasible, then the main port is again woken, and it throws an AltAbort.
   * 
   * 4. Deregistration: all ports are deregistered. 
   * 
   * 5. Completion: The relevant code for the branch is executed.
   * 
   * Code that involves calling a function in a port is executed *outside* a
   * synchronized block, to avoid deadlocks.  However, it is executed using
   * the lock of the port: this implies that after deregistration of a port,
   * there can be no subsequent call-back from that port.  */

  /** Have we yet initialised size, enabled, ports? */
  private var initialised = false

  /** The number of branches.  Set on the first iteration. */
  private var size = -1 

  /** Is this still registering with other branches? */
  private var registering = true

  /** Has the alt fired? */
  private var done = false

  /** The branches.  Re-evaluated on each iteration. */
  private var theseBranches: Array[AtomicAltBranch] = null

  /** Which branches are enabled?  Initialised on the first iteration. */
  private var enabled: Array[Boolean] = null

  /** The ports for each branch.  Initialised on the first iteration.*/
  private var ports: Array[Port] = null

  /** How many branches are enabled? */
  private var numEnabled = 0

  /** The index of the branch for the main thread to run. */
  private var toRun = -1

  /** The index of this iteration. */
  private var iter = 0
  // Note: iter is used only for assertions, so could be removed.

  /** The index to register first on the next iteration. */
  private var registerFirst = 0

  /** Run this alt once. */
  private [scl] def apply(): Unit = { 
    // Unpack branches for this iteration.  This also evaluates the guards and
    // ports to use.
    theseBranches = branches.unpack.toArray
    if(initialised) assert(size == theseBranches.length)
    else{
      size = theseBranches.length; enabled = new Array[Boolean](size)
      ports = new Array[Port](size); initialised = true
    }
    assert(numEnabled == 0 && enabled.forall(_ == false) && !done)
    var offset = 0; 
    // Note: previous version did not hold lock during registration; 
    synchronized{
      registering = true
      // Register with each branch
      while(offset < size && !done){
        val i = (registerFirst+offset)%size
        theseBranches(i) match{
          case ipb: InPortBranch[_] =>
            if(ipb.guard && !alreadyRegistered(ipb.inPort, offset)){
              ipb.inPort.registerIn(this, i, iter) match{
                case RegisterInClosed => {} // enabled(i) = false - no need
                case RegisterInSuccess(x) =>
                  ipb.valueReceived = x; toRun = i; done = true
                // println(s"Alt: success on inport registration $i $iter")
                case RegisterInWaiting =>
                  enabled(i) = true; numEnabled += 1; ports(i) = ipb.inPort
                  // println(s"Alt: failure on inport registration $i $iter");
              }
            }
          case opb: OutPortBranch[_] =>
            if(opb.guard && !alreadyRegistered(opb.outPort, offset)){
              opb.outPort.registerOut(this, i, iter, opb.value) match{
                case RegisterOutClosed => enabled(i) = false
                case RegisterOutSuccess => toRun = i; done = true
                case RegisterOutWaiting =>
                  enabled(i) = true; numEnabled += 1; ports(i) = opb.outPort
              }
            }
        } // end of match
        offset += 1
      } // end of while

      registering = false 
      // notifyAll() // wake up blocked call-backs; no longer needed

      if(!done){
        // Wait for something to happen
        while(!done && numEnabled > 0) wait() // wait for something to happen (1)
        if(numEnabled == 0) throw new AltAbort
      }
    } // end of synchronized block

    // Either a ready branch was identified during registration, or one called
    // maybeReceive.  Deregister all other branches.  Do this without locking,
    // to avoid blocking call-backs.
    assert(done); var i = 0
    while(i < size){
      if(i != toRun && enabled(i)) theseBranches(i) match{
        case ipb: InPortBranch[_] => ipb.inPort.deregisterIn(this, i, iter)
        case opb: OutPortBranch[_] => opb.outPort.deregisterOut(this, i, iter)
      } // end of match
      i += 1
    } // end of while loop

    // Run ready branch.
    theseBranches(toRun) match{
      case ipb: InPortBranch[_] => ipb.body(ipb.valueReceived)
      case opb: OutPortBranch[_] => opb.cont()
    }
    registerFirst = toRun+1 // First branch to register next time
  }

  /** Is port p already registered, within theseBranches
    * [registerFirst..registerFirst+n) (mod size)? */
  private def alreadyRegistered(p: Port, n: Int): Boolean = {
    var i = 0; var found = false
    while(i < n && !found){
      val j = (registerFirst+i)%size; i += 1
      found = enabled(j) && ports(j) == p
    }
    found
  }

  // ================================= Call-backs from ports

  /** Try to get alt to receive `value` from the InPort of branch `index`.  This
    * corresponds to the current thread sending `value`. */
  private[channel] 
  def maybeReceive[A](value: A, index: Int, iter: Int): Boolean = synchronized{
    assert(!registering && iter == this.iter)
    // while(registering) wait() // Wait for registration to finish
    assert(iter == this.iter && numEnabled > 0 && enabled(index))
    enabled(index) = false
    if(done) false
    else{
      theseBranches(index) match{
        case ipb: InPortBranch[A @unchecked] =>
          ipb.valueReceived = value; toRun = index // Store value in the branch
          done = true; notify()                // signal to apply() at (1)
      }
      true
    }
  }

  /** Try to get alt to send on the OutPort of branch `index`.  This corresponds
    * to the current thread receiving. */
  private[channel] 
  def maybeSend[A](index: Int, iter: Int): Option[A] = synchronized{
    assert(!registering && iter == this.iter)
    // while(registering) wait() // Wait for registration to finish
    assert(iter == this.iter && numEnabled > 0 && enabled(index))
    enabled(index) = false;
    if(done) None
    else{
      theseBranches(index) match{
        case opb: OutPortBranch[A @unchecked] => 
          toRun = index; done = true; notify(); val result = opb.value()
          Some(result)
          // Note it's important to evaluate opb.value here, before the alt
          // executes the continuation or goes on to the next iteration, which
          // might change the state.
      }
    }
  }

  /** Receive indication from branch `i` that the port has closed. */
  private[channel] def portClosed(i: Int, iter: Int) = synchronized{
    assert(!registering && iter == this.iter)
    // while(registering) wait() // Wait for registration to finish
    assert(!registering && enabled(i) && iter == this.iter,
      s"$registering, $iter, ${this.iter}")
    if(!done){
      enabled(i) = false; numEnabled -= 1
      if(numEnabled == 0) notify()        // signal to apply() at (1)
    }
  }

  /** Reset for the next round. */
  @inline private def reset = synchronized{
    assert(done) 
    // might have numEnabled = 0 if first branch was ready during registration
    for(i <- 0 until size) enabled(i) = false
    numEnabled = 0; toRun = -1; iter += 1; done = false
  }

  /** Run this repeatedly while `guard` is true, or until no branch is
    * feasible. */
  private[scl] def repeat(guard: => Boolean) = {
    try{ while(guard){ apply(); reset } }
    catch{ case _: ox.scl.Stopped   => {}; case t: Throwable => throw t }
  }
}
