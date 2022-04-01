import ox.scl._
// import io.threadcso.debug.Log
import scala.util.Random

object ReadersWritersTest{
  val p = 20 // Number of threads
  val reps = 2000 // Number of tests to run
  val iters = 100 // Number of iterations per run

  // Events for logging
  abstract class LogEvent
  case object ReaderEnter extends LogEvent
  case object ReaderLeave extends LogEvent
  case object WriterEnter extends LogEvent
  case object WriterLeave extends LogEvent

  /** A worker, which randomly tries to act like a reader or writer. */
  def worker(me: Int, rw: ReadersWritersLock, log: Log[LogEvent]) = thread{
    for(i <- 0 until iters) if(Random.nextInt(2) == 0){
      rw.readerEnter; log.add(me, ReaderEnter)
      log.add(me, ReaderLeave); rw.readerLeave
    }
    else{
      rw.writerEnter; log.add(me, WriterEnter)
      log.add(me, WriterLeave); rw.writerLeave
    }
  }

  /** Check that events satisfies the invariant for a readers-writers lock. */ 
  def checkLog(events: Array[LogEvent]): Boolean = {
    assert(events.length == p*iters*2, events.length)
    var readers = 0; var writers = 0
    var error = false; var i = 0
    while(i < events.size && !error){
      events(i) match{
        case ReaderEnter => error = writers > 0; readers += 1
        case ReaderLeave => assert(readers > 0); readers -= 1
        case WriterEnter => error = readers > 0 || writers > 0; writers += 1
        case WriterLeave => assert(writers == 1); writers = 0
      }
      i += 1
    }
    !error
  }

  /** Run a single test. 
    * @param theType String indicating which type of ReadersWritersLock to use.
    */
  def runTest(theType: String) = {
    val rw: ReadersWritersLock =
      if(theType == "fair") new FairReadWriteLock
      else{ assert(theType == "monitor"); new ReadersWritersMonitor }
      // else if(theType == "semaphore") new ReadersWritersLockSemaphore
      // else{
      //   assert(theType == "fairSemaphore")
      //   new FairReadersWritersLockSemaphore
      // }
    val log = new Log[LogEvent](p)
    run(|| (for(i <- 0 until p) yield worker(i, rw, log))) // run system
    if(!checkLog(log.get)) sys.exit
  }

  def main(args : Array[String]) = {
    var theType = "monitor"
    var i = 0
    while(i < args.length) args(i) match{
      case "--fair" => theType = "fair"; i += 1
      // case "--semaphore" => theType = "semaphore"; i += 1
      // case "--fairSemaphore" => theType = "fairSemaphore"; i += 1
    }

    for(i <- 0 until reps){ runTest(theType); if(i%10 == 0) print(".") }
    println
  }
}

