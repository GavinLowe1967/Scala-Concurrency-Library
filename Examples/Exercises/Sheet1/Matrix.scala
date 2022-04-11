import ox.scl._

class Matrix(a: Array[Array[Int]], b: Array[Array[Int]],
             numWorkers: Int, taskSize: Int){
  private val n = a.size

  /** Array to store the result. */
  private var c = Array.ofDim[Int](n,n)

  /** A task.  The pair (start,end) represents the task of calculating rows
    * [start..end). */
  private type Task = (Int,Int) 

  /** Channel for sending tasks. */
  private val toWorkers = new BuffChan[Task](numWorkers)

  /** A worker: repeatedly receive tasks, and calculate the relevant rows. */
  private def worker = thread{
    repeat{
      val(start,end) = toWorkers?()
      for(i <- start until end; j <- 0 until n){
	// Calculate value for c(i)(j)
	var sum = 0
	for(k <- 0 until n) sum += a(i)(k)*b(k)(j)
	c(i)(j) = sum
      }
    }
  }

  /** The controller: repeatedly allocate tasks of size taskSize. */
  private def controller = thread{
    var current = 0
    while(current < n){
      toWorkers!(current, current+taskSize min n)
      current += taskSize
    }
    toWorkers.endOfStream
  }

  /** calculate the product of a and b. */
  def apply(): Array[Array[Int]] = {
    val system = || (for(w <- 0 until numWorkers) yield worker) || controller
    run(system)
    c
  }

}

// -------------------------------------------------------

import scala.util.Random

object MatrixTest{

  /** Print matrix a. */
  def printArray(a : Array[Array[Int]]) = {
    val n = a.length
    for(i <- 0 until n){
      for(j <- 0 until n) print(a(i)(j).toString+"\t")
      println
    }
    println
  }

  def randomArray(n: Int) = Array.fill(n,n)(Random.nextInt(10))

  def simpleTest = {
    val N = 10
    // Generate a and b randomly
    val a = randomArray(N); val b = randomArray(N)
    // Print the initial arrays
    printArray(a); printArray(b)
    // Multiply them
    val c = new Matrix(a,b,20,2)()
    // Print the result
    printArray(c)
  }

  val Million = 1000000L
  val Billion = Million*1000

  /** Measure times for different task sizes, for matrices of size n and
    * numWorkers worker threads. */
  def timingTest(n: Int, numWorkers: Int) = {
    // # reps; chosen to spend ~3s per task size
    val reps = (300L*Million*numWorkers/(n.toLong*n*n)).toInt
    println("reps = "+reps)
    // Warm up
    for(_ <- 0 until reps){
      val a = randomArray(n); val b = randomArray(n)
      val c = new Matrix(a, b, numWorkers, 2)()
    }
    println("starting")
    for(taskSize <- 1 until 10){
      var elapsed = 0L
      for(_ <- 0 until reps){
        val a = randomArray(n); val b = randomArray(n)
        val start = System.nanoTime
        val c = new Matrix(a, b, numWorkers, taskSize)()
        elapsed += System.nanoTime-start
      }
      println(s"$taskSize\t"+elapsed/Million+"ms")
    }
  }


  // Main method
  def main(args: Array[String]) = {
    var n = 500; var numWorkers = 64
    var i = 0
    while(i < args.length) args(i) match{
      case "-n" => n = args(i+1).toInt; i += 2
      case "--numWorkers" => numWorkers = args(i+1).toInt; i += 2
    }

    timingTest(n, numWorkers)
  }
}
