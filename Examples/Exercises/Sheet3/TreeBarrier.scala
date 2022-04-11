/** Barrier synchronisation between n processes.  Each process should have an
identity me in [0..N).

A new Barrier object is created as, e.g.:
  val barrier = new Barrier(N);

The individual processes then perform
  barrier.sync(me);
in order to perform a global synchronisation.

This implementation is based on the barrier tree synchronisation algorithm in
Section 3.4.2 of Foundations of Multithreaded, Parallel, and Distributed
Programming, by Gregory Andrews.  The processes are arranged in a heap.  Each
leaf process sends a "ready" message to its parent.  Each intermediate node
receives "ready" messages from its children, and sends a "ready" message to
its parent.  The root node receives "ready" messages from its children, and
sends a "go" message back.  The "go" messages are propogated by the reverse
routes.
*/

import ox.scl._

class TreeBarrier(n: Int){
  // Channels:
  private val ready = Array.fill(n)(new SyncChan[Unit])
  private val go = Array.fill(n)(new SyncChan[Unit])
  // The channels are indexed by the *child's* identity; so ready is indexed
  // by the sender's identity, and go is indexed by the receiver's identity

  // Barrier protocol for node with identity me, in a system on n nodes
  def sync(me : Int) = {
    require(0 <= me && me < n)
    val child1 = 2*me+1; val child2 = 2*me+2
    // Wait for ready signals from both children
    if(child1 < n) ready(child1)?() 
    if(child2 < n) ready(child2)?() 
    // Send ready signal to parent, and wait for go reply, unless this is the
    // root
    if(me!=0){ ready(me)!(); go(me)?() }
    // Send go signals to children
    if(child1 < n) go(child1)!()
    if(child2 < n) go(child2)!()
  }

}

// ==================================================================

import scala.util.Random

object BarrierTest{
  val iters = 100 // # iterations per test
  val reps = 500

  // Events to place in Log
  abstract class LogEvent
  // Thread id calls sync or returns from sync
  case class Arrive(id: Int) extends LogEvent
  case class Leave(id: Int) extends LogEvent

  /** Run a single test. */
  def doTest = {
    val p = 1+Random.nextInt(20) // # threads
    val barrier = new TreeBarrier(p)
    val log = new debug.Log[LogEvent](p)
    def worker(me: Int) = thread{
      for(_ <- 0 until iters){ 
        log.add(me, Arrive(me)); barrier.sync(me); log.add(me, Leave(me))
      }
    }
    run(|| (for (i <- 0 until p) yield worker(i)))
    checkLog(log.get, p)
  }

  /** Check that es represents a valid history for p threads. */
  def checkLog(es: Array[LogEvent], p: Int) = {
    // We traverse the log, keeping track of which threads are currently
    // within a sync, respectively waiting to synchronise or leaving; we use a
    // bitmap for each of these sets.
    var waiting, leaving = new Array[Boolean](p)
    var numWaiting, numLeaving = 0 // # currently waiting, leaving
    for(i <- 0 until es.length){
      es(i) match{
        case Arrive(id) => 
          assert(!waiting(id)); waiting(id) = true; numWaiting += 1
          if(numWaiting == p){ // all now can leave
            assert(numLeaving == 0); leaving = waiting; numLeaving = p
            waiting = new Array[Boolean](p); numWaiting = 0
          }
        case Leave(id) =>
          assert(leaving(id)); leaving(id) = false; numLeaving -= 1
      }
    }
  }

  def main(args: Array[String]) = {
    for(r <- 0 until reps){ doTest; if(r%10 == 0) print(".") }
    println
  }


}
