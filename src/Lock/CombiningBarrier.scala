package ox.scl.lock

/** A combining barrier synchronisation to be used by `n` threads.  The
  * threads must have distinct identities in the range [0..`n`).  Each
  * provides a piece of data, and all receive back the result of combining the
  * data using `f`. */
class CombiningBarrier[A](n: Int, f: (A,A) => A){
  /* The threads are logically arranged into a binary heap. */

  /** An object for signalling and passing data between a child and its parent
    * in the heap. */
  private class Signal{
    /** The state of this object.  true represents that the child has signalled,
      * but not yet received a signal back. */
    private var state = false

    /** A slot that holds the data passed up the heap, and the result passed back
      * down. */
    private var slot: A = _

    /** Pass x to the parent, and wait for a result back. */
    def signalUpAndWait(x: A): A = synchronized{
      require(!state, 
        "Illegal state of Barrier: this might mean that it is\n"+
        "being used by two threads with the same identity.");
      state = true; slot = x; notify()
      while(state) wait()
      slot
    }

    /** Wait for a value from the child. */
    def waitForSignalUp: A = synchronized{ while(!state) wait(); slot }

    /** Pass x to the child. */
    def signalDown(x: A) = synchronized{ state = false; slot = x; notify() }
  } // end of Signal

  /** The objects used for signalling.  signals(i) is used for signalling
    * between thread i and its parent. */
  private val signals = Array.fill(n)(new Signal)

  /** Perform a barrier synchronisation.
    * @param me the unique identity of this thread. */
  def sync(me: Int, x: A): A  = {
    require(0 <= me && me < n, 
      s"Illegal parameter $me for sync: should be in the range [0..$n).")
    val child1 = 2*me+1; val child2 = 2*me+2
    // In the upwards phase, result holds the accumulation of the values from
    // this subtree; in the downwards phase it holds the overall result
    var result = x
    // Wait for children
    if(child1 < n) result = f(result, signals(child1).waitForSignalUp)
    if(child2 < n) result = f(result, signals(child2).waitForSignalUp)
    // Pass accumulated value to parent, and wait for result back
    if(me != 0) result = signals(me).signalUpAndWait(result)
    // Pass result to children
    if(child1 < n) signals(child1).signalDown(result)
    if(child2 < n) signals(child2).signalDown(result)
    result
  }
}

// ==================================================================

/** A combining barrier that calculates the conjunction if its inputs. */
class AndBarrier(n: Int) extends CombiningBarrier[Boolean](n, _ && _)

/** A combining barrier that calculates the conjunction if its inputs. */
class OrBarrier(n: Int) extends CombiningBarrier[Boolean](n, _ || _)
