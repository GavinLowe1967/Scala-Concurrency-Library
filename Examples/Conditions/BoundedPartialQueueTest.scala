import scala.collection.immutable.Queue
import ox.scl._

/** A linearizability tester for bounded partial queues. */
object BoundedPartialQueueTest{
  var iters = 200  // Number of iterations by each worker
  val MaxVal = 20 // Maximum value placed in the queue
  var enqueueProb = 0.3 // probability of doing an enqueue
  var bound = 10 // the bound on the length of the queue

  type SeqQueue = scala.collection.immutable.Queue[Int]
  type ConcQueue = PartialQueue[Int]

  def seqEnqueue(x: Int)(q: SeqQueue) : (Unit, SeqQueue) = {
    require(q.length < bound); ((), q.enqueue(x))
  }
  def seqDequeue(q: SeqQueue) : (Int, SeqQueue) = {
    require(q.nonEmpty); q.dequeue
  }

  /** A worker for the LinTesters */
  def worker(me: Int, log: LinearizabilityLog[SeqQueue, ConcQueue]) = {
    val random = new scala.util.Random(scala.util.Random.nextInt+me*45207)
    for(i <- 0 until iters){
      if(me%2 == 0){
        val x = random.nextInt(MaxVal)
        log(_.enqueue(x), "enqueue("+x+")", seqEnqueue(x))
      }
      else log(_.dequeue, "dequeue", seqDequeue)
    }
  }

  def main(args: Array[String]) = {
    // parse arguments
    var i = 0; var queueType = "monitor"
    val p = 4      // Number of workers 
    var reps = 10000  // Number of repetitions
    while(i < args.length) args(i) match{
      case "--iters" => iters = args(i+1).toInt; i += 2 
      case "--reps" => reps = args(i+1).toInt; i += 2 
      case "--enqueueProb" => enqueueProb = args(i+1).toDouble; i += 2
      case "--monitor" => queueType = "monitor"; i += 1
      case "--bound" => bound = args(i+1).toInt; i += 2
      case arg => println("Unrecognised argument: "+arg); sys.exit
    }

    for(r <- 0 until reps){
      // The shared concurrent queue
      val concQueue = queueType match{
        case "monitor" => new BoundedMonitorPartialQueue[Int](bound)
      }
      val seqQueue = Queue[Int]()
      val tester = LinearizabilityTester[SeqQueue,ConcQueue](
        seqQueue, concQueue, p, worker _)
      assert(tester() > 0)
      concQueue.shutdown

      if(r%50 == 0) print(".")
    } // end of for loop
    println
  }
}
