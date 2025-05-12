package tests 
import scala.collection.immutable.Queue
import ox.scl._

/** A test of a BuffChan. */
object BuffChanTest{
  var iters = 50  // Number of iterations by each worker
  val MaxVal = 20 // Maximum value sent
  var size = 3    // capacity of the channel

  type SeqChan = scala.collection.immutable.Queue[Int]
  type ConcChan = BuffChan[Int]

  /* Operations on the specification. */
  def seqSend(x: Int)(q: SeqChan) : (Unit, SeqChan) = {
    require(q.length < size); ((), q.enqueue(x))
  }
  def seqReceive(q: SeqChan) : (Int, SeqChan) = {
    require(q.nonEmpty); q.dequeue
  }

  /** A worker for the LinTesters */
  def worker(me: Int, log: LinearizabilityLog[SeqChan, ConcChan]) = {
    val random = new scala.util.Random(scala.util.Random.nextInt()+me*45207)
    for(i <- 0 until iters){
      if(me%2 == 0){
        val x = random.nextInt(MaxVal)
        log(_!x, "send("+x+")", seqSend(x))
      }
      else log(_?(), "receive", seqReceive)
    }
  }

  def main(args: Array[String]) = {
    // parse arguments
    var i = 0; val p = 4      // Number of workers 
    var reps = 10000  // Number of repetitions
    while(i < args.length) args(i) match{
      case "--iters" => iters = args(i+1).toInt; i += 2 
      case "--reps" => reps = args(i+1).toInt; i += 2 
      case "--size" => size = args(i+1).toInt; i += 2
      case arg => println("Unrecognised argument: "+arg); sys.exit()
    }

    for(r <- 0 until reps){
      val concChan = new BuffChan[Int](size); val seqChan = Queue[Int]()
      val tester =
        LinearizabilityTester[SeqChan,ConcChan](seqChan, concChan, p, worker _)
      assert(tester() > 0)
      if(r%50 == 0) print(".")
    } // end of for loop
    println()
  }
}
