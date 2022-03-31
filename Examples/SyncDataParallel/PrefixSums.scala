import ox.scl._

/** Calculate prefix sums of an array a of size n in poly-log n (parallel)
  *  steps.  Based on Andrews Section 3.5.1. */
class PrefixSums(n: Int, a: Array[Int]){
  require(n == a.size)

  /** Shared array, in which sums are calculated. */
  private val sum = new Array[Int](n) 

  /** Barrier synchronisation object. */
  private val barrier = new Barrier(n)

  /** Channels on which values are sent, indexed by receiver's identity. */
  private val toSummers = Array.fill(n)(new BuffChan[Int](1))

  /** An individual thread.  summer(me) sets sum[me] equal to sum(a[0..me]). */
  private def summer(me: Int) = thread(s"summer($me)"){
    // Invariant: gap = 2^r and s = sum a(me-gap .. me]
    // (with fictious values a(i) = 0 for i < 0).  r is the round number.
    var r = 0; var gap = 1; var s = a(me)

    while(gap < n){
      if(me+gap < n) toSummers(me+gap)!s // pass my value up the line
      if(gap <= me){                // receive from me-gap,
	val inc = toSummers(me)?()  // inc = sum a(me-2*gap .. me-gap]
	s = s + inc                 // s = sum a(me-2*gap .. me]
      }                             // s = sum a(me-2*gap .. me]
      r += 1; gap += gap            // s = sum a(me-gap .. me]
      barrier.sync(me)
    }
    sum(me) = s
  }

  /** Calculate the prefix sums. */
  def apply(): Array[Int] = {
    run(|| (for (i <- 0 until n) yield summer(i)))
    sum
  }
}

// -------------------------------------------------------

import scala.util.Random

object PrefixSumsTest{
  val reps = 10000

  /** Do a single test. */
  def doTest = {
    // Pick random n and array
    val n = 1+Random.nextInt(20)
    val a = Array.fill(n)(Random.nextInt(100))
    // Calculate prefix sums sequentially
    val mySum = new Array[Int](n)
    var s = 0
    for(i <- 0 until n){ s += a(i); mySum(i) = s }
    // Calculate them concurrently
    val sum = new PrefixSums(n, a)()
    // Compare
    assert(sum.sameElements(mySum),
           "a = "+a.mkString(", ")+"\nsum = "+sum.mkString(", ")+
             "\nmySum = "+mySum.mkString(", "))
  }

  def main(args : Array[String]) = {
    for(r <- 0 until reps){ doTest; if(r%100 == 0) print(".") }
    println
  }
}

