// Given arrays a, b, with no repetitions, count the number of distinct
// elements in both a and b.

import ox.scl._

/** Object to count the number of duplicates between a and b, using numWorkers
  * worker threads. */
class CountDups(a: Array[Int], b: Array[Int], numWorkers: Int){
  private val aSize = a.length

  /** Channel for workers to send results to controller. */
  private val toController = new BuffChan[Int](1)

  /** A single worker. */
  private def worker(me: Int) = thread{
    // This worker will deal with a[aStart..aEnd) and all of b
    val aStart = (me*aSize)/numWorkers
    val aEnd = (me+1)*aSize/numWorkers
    // Hash table storing elements of b.
    val bHash = new scala.collection.mutable.HashSet[Int]
    bHash ++= b
    // Iterate through segment of a, counting how many entries are in bHash
    var count = 0
    for(i <- aStart until aEnd; if bHash.contains(a(i))) count += 1 
    // Send result to controller
    toController!count
  }

  /** Variable that ends up holding the result.  While the system is running,
    * only the controller writes to this, and no other thread reads it, so
    * there are no races. */
  private var result = 0

  /** The controller. */
  private def controller = thread{
    for(i <- 0 until numWorkers) result += toController?()
  }

  /** Find the number of duplicates. */
  def apply(): Int = {
    result = 0
    // Run the system
    run(|| (for(i <- 0 until numWorkers) yield worker(i)) || controller)
    result
  }	
}

// -------------------------------------------------------

import scala.util.Random

object CountDupsTest{
  /** Create a random array of size n containing no repetitions. */
  def randomArray(n: Int) = {
    val max = 5*n // max value allowed
    val a = new Array[Int](n)
    val appeared = new Array[Boolean](max)
    var value = -1
    for(i <- 0 until n){
      // Find value s.t. !appeared(value)
      do{ value = Random.nextInt(max) } while(appeared(value));
      a(i) = value; appeared(value) = true
    }
    a
  }

  /** A very simple test. */
  def simpleTest = {
    val N = 10; val a = randomArray(N); val b = randomArray(N)
    println("a = "+a.mkString(", ")+"\nb = "+b.mkString(", "))
    val dups = new CountDups(a, b, 10)()
    println("dups = "+dups)
  }

  /** Run a single test on random arrays, conparing the result against a
    * sequential implementation. */
  def doTest = {
    val a = randomArray(Random.nextInt(100))
    val b = randomArray(Random.nextInt(100))
    val numWorkers = Random.nextInt(20)
    val cDups = new CountDups(a, b, 10)()
    val sDups = a.count(b.contains(_))
    assert(cDups == sDups,
           "a = "+a.mkString(", ")+"\nb = "+b.mkString(", ")+
             "\ncDups = "+cDups+"\nsDups = "+sDups)
  }

  def main(args: Array[String]) = {
    if(false) simpleTest
    else
      for(i <- 0 until 10000){
        doTest
        if(i%100 == 0) print(".")
      }
    println
  }

}
