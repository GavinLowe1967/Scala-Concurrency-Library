package ox.scl.lock

/** A barrier synchronisation to be used by `n` threads.  The threads must
  * have distinct identities in the range [0..`n`). */
class Barrier(n: Int){
  /** An object for signalling between a child and its parent in the heap. */
  private class Signal{
    /** The state of this object.  true represents that the child has signalled,
      * but not yet received a signal back. */
    private var state = false

    /** Signal to the parent, and wait for a signal back. */
    def signalUpAndWait = synchronized{
      require(!state, 
        "Illegal state of Barrier: this might mean that it is\n"+
        "being used by two threads with the same identity.");
      state = true; notify()
      while(state) wait()
    }

    /** Wait for a signal from the child. */
    def waitForSignalUp = synchronized{ while(!state) wait() }

    /** Signal to the child. */
    def signalDown = synchronized{ state = false; notify() }
  } // end of Signal

  /** The objects used for signalling.  signals(i) is used for signalling
    * between thread i and its parent. */
  private val signals = Array.fill(n)(new Signal)

  /** Perform a barrier synchronisation.
    * @param me the unique identity of this thread. */
  def sync(me: Int) = {
    require(0 <= me && me < n, 
      s"Illegal parameter $me for sync: should be in the range [0..$n).")
    val child1 = 2*me+1; val child2 = 2*me+2
    // Wait for children
    if(child1 < n) signals(child1).waitForSignalUp
    if(child2 < n) signals(child2).waitForSignalUp
    // Signal to parent and wait for signal back
    if(me != 0) signals(me).signalUpAndWait
    // Signal to children
    if(child1 < n) signals(child1).signalDown
    if(child2 < n) signals(child2).signalDown
  }
}
