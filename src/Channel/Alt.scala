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
  val enabled = new Array[Boolean](size)

  /** How many branches are enabled? */
  var numEnabled = 0

  /** The index of the branch for the main thread to run when awoken. */
  var toRun = -1

  /** Run this alt. */
  def apply(): Unit = synchronized{
    var i = 0
    // Register with each branch
    while(i < size && !done){
      branches(i) match{
        case ipb: InPortBranch[_] => 
          val guard = ipb.guard()
          if(guard){
            ipb.inPort.registerIn(this, i) match{
              case RegisterInClosed => enabled(i) = false
              case RegisterInSuccess(x) => 
                // println(s"alt $this received $x on branch $i while registering")
                deregisterAll
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
        deregisterAll // Deregister other branches
        branches(toRun) match{
          case ipb: InPortBranch[_] => ipb.body(ipb.valueReceived)
        }
      }
    }
  }

  /** Deregister from all branches except the one we're firing. */
  @inline def deregisterAll = {
    var i = 0
    while(i < size){
      if(i != toRun && enabled(i)) branches(i) match{
        case ipb: InPortBranch[_] => ipb.inPort.deregisterIn(this, i)
      } // end of match
      i += 1
    } // end of while loop
  }

  /** Potentially receive value from the InPort of branch `i`. */
  def maybeReceive[A](value: A, i: Int): Boolean = synchronized{
    assert(!registering)
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
  def portClosed(i: Int) = synchronized{
    assert(!registering && enabled(i))
    if(!done){ 
      enabled(i) = false; numEnabled -= 1
      if(numEnabled == 0){
        aborting = true; notify()        // signal to apply() at (1)
      }
    }
  }


}
