package ox.scl.channel

class AltAbort  extends ox.scl.Stopped

/** An `alt` construct, with branches corresponding to `branches`. */ 
class Alt(branches: Array[AtomicAltBranch]) extends AltT{
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
   * function, which causes the main thread to be woken.  If a port of closed,
   * it calls the `portClosed` function; if subsequently no branch is
   * feasible, then the main port is again woken, and it throws an AltAbort.
   * 
   * 4. Deregistration: all ports are deregistered. 
   * 
   * 5. Completion: The relevant code for the branch is executed.
   * 
   * Code that involves calling a function in a port is executed *outside* a
   * synchronized block, to avoid deadlocks.  */

  private val size = branches.length

  /** Is this still registering with other branches? */
  private var registering = true

  /** Has the alt fired? */
  private var done = false

  /** Which branches are enabled? */
  private val enabled = new Array[Boolean](size)

  /** How many branches are enabled? */
  private var numEnabled = 0

  /** The index of the branch for the main thread to run. */
  private var toRun = -1

  /** The index of this iteration. */
  private var iter = 0
  // Note: iter is used only for assertions, so could be removed.

  /** Run this alt once. */
  def apply(): Unit = {
    require(numEnabled == 0 && enabled.forall(_ == false) && !done)
    var i = 0; synchronized{ registering = true }
    // Register with each branch
    while(i < size && !done){
      branches(i) match{
        case ipb: InPortBranch[_] => 
          val guard = ipb.guard()
          if(guard){
            ipb.inPort.registerIn(this, i, iter) match{
              case RegisterInClosed => enabled(i) = false
              case RegisterInSuccess(x) => 
                 ipb.valueReceived = x; toRun = i; done = true
              case RegisterInWaiting => enabled(i) = true; numEnabled += 1
            }
          }
      } // end of match
      i += 1
    } // end of while

    synchronized{
      registering = false; notifyAll() // wake up blocked call-backs

      if(!done){
        // Wait for something to happen
        while(!done && numEnabled > 0) wait() // wait for something to happen (1)
        if(numEnabled == 0) throw new AltAbort
        done = true
      }
    } // end of synchronized block

    // Either a ready branch was identified during registration, or one called
    // maybeReceive.  Deregister all other branches, and run the ready branch.
    // Do this without locking, to avoid blocking call-backs.
    i = 0
    while(i < size){
      if(i != toRun && enabled(i)) branches(i) match{
        case ipb: InPortBranch[_] => ipb.inPort.deregisterIn(this, i, iter)
      } // end of match
      i += 1
    } // end of while loop
    // Run ready branch.
    branches(toRun) match{
      case ipb: InPortBranch[_] => ipb.body(ipb.valueReceived)
    }
  }

  /** Potentially receive value from the InPort of branch `i`. */
  def maybeReceive[A](value: A, i: Int, iter: Int): Boolean = synchronized{
    assert(iter == this.iter)
    while(registering) wait() // Wait for registration to finish
    assert(iter == this.iter && numEnabled > 0 && enabled(i))
    if(done) false
    else{
      assert(enabled(i))
      branches(i) match{
        case ipb: InPortBranch[A @unchecked] =>
          ipb.valueReceived = value; toRun = i // Store value in the branch
          done = true; notify()                // signal to apply() at (1)
      }
      true
    }
  }

  /** Receive indication from branch `i` that the port has closed. */
  def portClosed(i: Int, iter: Int) = synchronized{
    assert(iter == this.iter)
    while(registering) wait() // Wait for registration to finish
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

  /** Run this repeatedly. */
  def repeat = ox.scl.repeat{ apply(); reset } 
// FIXME: store place to start registration from one iteration to the next. 
}
