import ox.scl._
import scala.util.Random

object MinMaxTest{
  /** Number of repetitions. */
  val reps = 10000

  /** Array that will hold values chosen by threads, indexed by thread IDs. */
  var xs: Array[Int] = null

  /** Array that will hold results obtained by threads, indexed by thread
    * IDs. */
  var results: Array[(Int, Int)] = null

  /** A worker thread. */
  def worker(me: Int, mm: MinMax) = thread("Thread"+me){
    // Choose value randomly
    val x = Random.nextInt(1000); xs(me) = x
    //println("Client "+me+" chooses value "+x)
    val (min, max) = mm(me, x)
    //println("Client "+me+" ends with values "+(min,max))
    results(me) = (min, max)
  }

  /** Run a single test. 
    * @param mkMinMax a function that, given n, creates the MinMax object for 
    * n threads.
    * @param minN the minimum value allowed for n. */
  def runTest(mkMinMax: Int => MinMax, minN: Int) = {
    val n = minN+Random.nextInt(20) // number of peers
    xs = new Array[Int](n); results = new Array[(Int, Int)](n)
    val mm = mkMinMax(n) // new Centralised(n)
    // Run threads
    run(|| (for (i <- 0 until n) yield worker(i, mm)))
    // Check results
    val min = xs.min; val max = xs.max
    assert(results.forall(_ == (min, max)),
           "xs = "+xs.mkString(", ")+"\nresults = "+results.mkString(", "))
  }

  def main(args : Array[String]) = {
    // Parse argument
    if(args.isEmpty){ println("Type of MinMax object not specified"); sys.exit }
    val (mkMinMax, minN): (Int => MinMax, Int) = args(0) match{
      case "--centralised" => (new Centralised(_), 1)
      case "--symmetric" => (new Symmetric(_), 1)
      case "--ring" => (new Ring(_), 2) 
      case "--ringSym" => (new RingSym(_), 1) 
      case "--tree" => (new Tree(_), 1) 
      case arg => (null, -1)
    }
    if(mkMinMax == null){
      println("Pattern argument not recognised: "+args(0)); sys.exit
    }

    // Run tests
    for(r <- 0 until reps){
      runTest(mkMinMax, minN)
      if(r%200 == 0) print(".")
    }
    println
  }
}

