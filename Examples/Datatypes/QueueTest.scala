import scala.collection.immutable.Queue
import ox.scl._

/** A linearizability tester for total queues (i.e. queues that do not 
  * block, and such that dequeues on the empty queue return None). */
object QueueTest{
  var iters = 200  // Number of iterations by each worker
  val MaxVal = 200 // Maximum value placed in the queue
  var enqueueProb = 0.3 // probability of doing an enqueue

  type SeqQueue = scala.collection.immutable.Queue[Int]
  type ConcQueue = TotalQueue[Int]

  def seqEnqueue(x: Int)(q: SeqQueue) : (Unit, SeqQueue) = 
    ((), q.enqueue(x))
  def seqDequeue(q: SeqQueue) : (Option[Int], SeqQueue) =   
    if(q.isEmpty) (None, q) 
    else{ val (r,q1) = q.dequeue; (Some(r), q1) }

  /** A worker for the LinTesters */
  def worker(me: Int, log: LinearizabilityLog[SeqQueue, ConcQueue]) = {
    val random = new scala.util.Random(scala.util.Random.nextInt+me*45207)
    for(i <- 0 until iters)
      // println(s"$me: $i")
      if(random.nextFloat <= enqueueProb){
        val x = random.nextInt(MaxVal)
        log(_.enqueue(x), "enqueue("+x+")", seqEnqueue(x))
      }
      else log(_.dequeue, "dequeue", seqDequeue)
  }

  def main(args: Array[String]) = {
    // parse arguments
    var i = 0; var queueType = "server"
    val p = 4      // Number of workers 
    var reps = 10000  // Number of repetitions
    while(i < args.length) args(i) match{
      case "--iters" => iters = args(i+1).toInt; i += 2 
      case "--reps" => reps = args(i+1).toInt; i += 2 
      case "--enqueueProb" => enqueueProb = args(i+1).toDouble; i += 2
      case "-2" => queueType = "2"; i += 1
      case arg => println("Unrecognised argument: "+arg); sys.exit
    }

    for(r <- 0 until reps){
      // println(r)
      // The shared concurrent queue
      val concQueue = queueType match{
        case "server" => new ServerTotalQueue[Int]
        case "2" => new ServerTotalQueue2[Int]
      }
      val seqQueue = Queue[Int]()
      val tester = LinearizabilityTester[SeqQueue,ConcQueue](
        seqQueue, concQueue, p, worker _)
      assert(tester() > 0)
      concQueue.shutdown

      // println
      if(r%20 == 0) print(".")
    } // end of for loop
    println
  }
}
