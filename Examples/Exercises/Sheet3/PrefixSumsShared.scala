import ox.scl._

/** Calculate prefix sums of an array of size n in poly-log n (parallel) steps.
  * Based on Andrews Section 3.5.1. */
class PrefixSumsShared(n: Int, a: Array[Int]){
  require(n == a.size)

  /** Shared array, in which sums are calculated. */
  private val sum = new Array[Int](n) 

  /** Barrier synchronisation object. */
  private val barrier = new Barrier(n)


  /** An individual thread.  summer(me) sets sum[me] equal to sum(a[0..me]). */
  def summer(me : Int) = thread("Summer "+me){
    // Invariant: gap = 2^r and sum(me) = sum a(me-gap .. me] 
    // (with fictious values a[i]=0 for i<0).  r is the round number.

    // Each round uses two barrier synchronisations, one at the start and one
    // in the middle: between the two synchronisations, all sum variables are
    // treated as read-only; after the second synchronisation, sum(k) may be
    // written by Summer(k).
    
    var r = 0; var gap = 1; sum(me) = a(me)

    while(gap<n){ 
      barrier.sync(me)
      if(gap<=me){                
	val inc = sum(me-gap)    // inc = sum a(me-2*gap .. me-gap]
	barrier.sync(me)
	sum(me) = sum(me) + inc  // s = sum a(me-2*gap .. me]
      }
      else barrier.sync(me)
      r += 1; gap += gap;        // s = sum a(me-gap .. me]
    }
  }

  // Put system together
  def apply(): Array[Int] = {
    run (|| (for (i <- 0 until n) yield summer(i)))
    sum
  }

}

// ==================================================================

/** This implementation uses two arrays, with the role swapping between
  * rounds. */ 
class PrefixSumsShared2(n: Int, a: Array[Int]){
  require(n == a.size)

  /** Shared arrays, in which sums are calculated. */
  private val sum1, sum2 = new Array[Int](n) 

  /** Holds the final result. */
  private var result: Array[Int] = null

  /** Barrier synchronisation object. */
  private val barrier = new Barrier(n)

  /** An individual thread.  summer(me) sets sum[me] equal to sum(a[0..me]). */
  def summer(me : Int) = thread("Summer "+me){
    // Invariant: gap = 2^r and oldSum(me) = sum a(me-gap .. me] 
    // (with fictious values a[i]=0 for i<0).  r is the round number.
    
    var oldSum = sum1; var newSum = sum2
    var r = 0; var gap = 1; oldSum(me) = a(me)

    while(gap < n){ 
      barrier.sync(me)
      val inc = if(gap <= me) oldSum(me-gap) else 0 
                                     // inc = sum a(me-2*gap .. me-gap]
      newSum(me) = oldSum(me) + inc  // newSum(me) = sum a(me-2*gap .. me]
      r += 1; gap += gap;            // newSum(me) = sum a(me-gap .. me]
      // Swap the arrays for the next round
      val t = oldSum; oldSum = newSum; newSum = t 
      // oldSum(me) = sum a(me-gap .. me]
    }

    if(me == 0) result = oldSum
  }

  // Put system together
  def apply(): Array[Int] = {
    run(|| (for (i <- 0 until n) yield summer(i)))
    result
  }
}

// -------------------------------------------------------

import scala.util.Random

/** Test on PrefixSumsShared. */
object PrefixSumsSharedTest{
  val reps = 10000
  var type2 = false // which one to test

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
    val sum = 
      if(type2) new PrefixSumsShared2(n, a)() else new PrefixSumsShared(n, a)()
    // Compare
    assert(sum.sameElements(mySum),
           "a = "+a.mkString(", ")+"\nsum = "+sum.mkString(", ")+
             "\nmySum = "+mySum.mkString(", "))
  }

  def main(args : Array[String]) = {
    var i = 0
    while(i < args.length) args(i) match{
      case "--type2" => type2 = true; i += 1
    }

    for(r <- 0 until reps){ doTest; if(r%100 == 0) print(".") }
    println
  }

}
