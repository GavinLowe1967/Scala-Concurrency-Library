package ox.scl.channel

class AltAbort  extends ox.scl.Stopped

/** An `alt` construct, with branches corresponding to `branches`. */ 
class Alt(branches: Array[AtomicAltBranch]) extends AltT{
  private val size = branches.length

  /** Is this still registering with other branches? */
  private var registering = true

  /** Has the alt fired? */
  private var done = false

  /** Is this throwing an AltAbort because the final port has closed? */
  private var aborting = false

  /** Which branches are enabled? */
  private val enabled = new Array[Boolean](size)

  /** How many branches are enabled? */
  private var numEnabled = 0

  /** The index of the branch for the main thread to run when awoken. */
  private var toRun = -1

  /** The index of this iteration. */
  private var iter = 0

  /** Run this alt once. */
  def apply(): Unit = synchronized{
    require(numEnabled == 0 && enabled.forall(_ == false) && !done)
    var i = 0; registering = true
    // Register with each branch
    while(i < size && !done){
      branches(i) match{
        case ipb: InPortBranch[_] => 
          val guard = ipb.guard()
          if(guard){
            ipb.inPort.registerIn(this, i, iter) match{
              case RegisterInClosed => enabled(i) = false
              case RegisterInSuccess(x) => 
                // println(s"alt $this received $x on branch $i while registering")
                deregisterAllExcept(-1) // deregister all previous
                ipb.body(x); done = true
              case RegisterInWaiting => enabled(i) = true; numEnabled += 1
            }
          }
      } // end of match
      i += 1
    } // end of while
    registering = false

    if(!done){
      if(numEnabled == 0) throw new AltAbort 
      // Wait for something to happen 
      while(!done && !aborting) wait()     // wait for something to happen (1)
      if(aborting) throw new AltAbort
      else{ 
        // An instance of maybeReceived set toRun and then notified this thread
        deregisterAllExcept(toRun) // Deregister other branches
        branches(toRun) match{
          case ipb: InPortBranch[_] => ipb.body(ipb.valueReceived)
        }
      }
    }
  }

  /** Deregister from all branches except the one we're firing. */
  @inline private def deregisterAllExcept(omit: Int) = {
    var i = 0
    while(i < size){
      if(i != omit && enabled(i)) branches(i) match{
        case ipb: InPortBranch[_] => ipb.inPort.deregisterIn(this, i, iter)
      } // end of match
      i += 1
    } // end of while loop
  }

  /** Potentially receive value from the InPort of branch `i`. */
  def maybeReceive[A](value: A, i: Int, iter: Int): Boolean = synchronized{
    assert(!registering) 
    // Note: we might have iter != this.iter if the InPort has not been
    // registered on this iteration (if the guard is false) and if the channel
    // read the alt registration information before deregistration on the
    // previous round.
    if(done || iter != this.iter) false
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
// FIXME: check iter
    assert(!registering && enabled(i) && iter == this.iter, 
      s"$registering, $iter, ${this.iter}")
    if(!done){ 
      enabled(i) = false; numEnabled -= 1
      if(numEnabled == 0){
        aborting = true; notify()        // signal to apply() at (1)
      }
    }
  }

  /** Reset for the next round. */
  @inline private def reset = {
    assert(done && !aborting)
    for(i <- 0 until size) enabled(i) = false
    numEnabled = 0; toRun = -1; done = false; iter += 1
    println(s"$this $iter")
  }

  /** Run this repeatedly. */
  def repeat = synchronized{ ox.scl.repeat{ apply(); reset } }


}
