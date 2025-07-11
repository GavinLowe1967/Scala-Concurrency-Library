package tests

import ox.scl._
import scala.util.Random

object CombiningBarrierTest{
  val iters = 100 // # iterations per test
  val reps = 500

  // Events to place in Log
  abstract class LogEvent
  // Thread id calls sync or returns from sync
  case class Arrive(id: Int) extends LogEvent
  case class Leave(id: Int, res: Int) extends LogEvent

  /** Run a single test. */
  def doTest = {
    val p = 1+Random.nextInt(20) // # threads
    val barrier = new CombiningBarrier[Int](p, _+_)
    val log = new debug.Log[LogEvent](p)
    val xs = Array.fill(p)(Random.nextInt(20))
    def worker(me: Int) = thread{
      for(_ <- 0 until iters){ 
        log.add(me, Arrive(me)); val res = barrier.sync(me, xs(me)); 
        log.add(me, Leave(me, res))
      }
    }
    run(|| (for (i <- 0 until p) yield worker(i)))
    checkLog(log.get, p, xs.sum)
  }

  /** Check that es represents a valid history for p threads, where each is
    * expected to give result sum. */
  def checkLog(es: Array[LogEvent], p: Int, sum: Int) = {
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
        case Leave(id, res) =>
          assert(res == sum)
          if(!leaving(id)){
            println(s"Error with $p threads.")
            println(es.take(i+1).mkString("\n")); sys.exit()
          }
          leaving(id) = false; numLeaving -= 1
      }
    }
  }

  def main(args: Array[String]) = {
    for(r <- 0 until reps){ doTest; if(r%10 == 0) print(".") }
    println()
  }


}
