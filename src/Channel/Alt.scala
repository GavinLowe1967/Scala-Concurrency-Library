package ox.scl.channel

class AltAbort  extends ox.scl.Stopped

/** An `alt` construct, with branches corresponding to `branches`. */ 
class Alt(branches: Array[AtomicAltBranch]) extends AltT{
  private val size = branches.length

  /** Is this still registering with other branches? */
  private var registering = true

  /** Has the alt fired? */
  private var done = false

  /** Which branches are enabled? */
  val enabled = new Array[Boolean](size)

  /** How many branches are enabled? */
  var numEnabled = 0

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
                println(s"alt $this received $x on branch $i while registering")
                ipb.body(x); done = true
              case RegisterInWaiting => enabled(i) = true; numEnabled += 1
            }
          }
      } // end of match
      i += 1
    } // end of while

    registering = false; notifyAll() // wake up any call-backs from channels

    if(!done){
      if(numEnabled == 0) throw new AltAbort
      while(!done) wait()              // wait for something to happen (1)
    }

    // Deregister 
    println("deregistering")
    i = 0
    while(i < size){
      branches(i) match{
        case ipb: InPortBranch[_] =>
          if(enabled(i)) ipb.inPort.deregisterIn(this, i)
      } // end of match
      i += 1
    } // end of while loop
  }

  /** Potentially receive value from the InPort of branch `i`. */
  def maybeReceive[A](value: A, i: Int): Boolean = synchronized{
    // println(s"maybeReceive($value, $i)")
    while(registering) wait()
    if(done) false
    else{
      assert(enabled(i))
      branches(i) match{
        case ipb: InPortBranch[A @unchecked] => 
          println(s"alt $this received $value on branch $i post-registration")
          ipb.body(value); done = true; notify // signal to apply() at (1)
      }
      true
    }

  }

// NOTE: AltTest gives some sort of deadlock

// TODO: allow ports to signal they have been closed

}
