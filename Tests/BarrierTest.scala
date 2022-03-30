package tests

import ox.scl._
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
    val barrier = new Barrier(p)
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
          if(!leaving(id)){
            println(s"Error with $p threads.")
            println(es.take(i+1).mkString("\n")); sys.exit
          }
          leaving(id) = false; numLeaving -= 1
      }
    }
  }

  def main(args: Array[String]) = {
    for(r <- 0 until reps){ doTest; if(r%10 == 0) print(".") }
    println
  }


}
