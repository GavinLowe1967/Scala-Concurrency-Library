import ox.scl._

/** A process to sort n numbers, using a pipeline. */
class PipeSort(n: Int){
  /** A single node. */
  private def node(left: ?[Int], right: ![Int]) = thread{
    var current = left?()
    repeat{
      val next = left?()
      if(current > next) right!next
      else { right!current; current = next }
    }
    right!current; right.endOfStream
  }

  private val chans = Array.fill(n+1)(new SyncChan[Int])

  /** The input channel to the pipeline. */
  val left = chans(0)

  /** The output channel from the pipeline. */
  val right = chans(n)

  /** The sorting network. */
  def apply() = || (for(i <- 0 until n) yield node(chans(i), chans(i+1)))
}

// -------------------------------------------------------

import scala.util.Random

/** A testing rig for PipeSort. */
object PipeSortTest{
  /** Send values from xs on right. */
  def generator(right: ![Int], xs: Array[Int]) = thread{
    for(i <- 0 until xs.length) right!xs(i)
    right.endOfStream
  }

  /** Receive random numbers on left, and store in ys. */
  def collector(left: ?[Int], ys: Array[Int]) = thread{
    for(i <- 0 until ys.length) ys(i) = left?()
  }

  /** Perform a single test: pass in random numbers; record output; check output
    * is sorted version of input. */
  def doTest = {
    val N = 100
    val xs = Array.fill(N)(Random.nextInt)
    val ys = new Array[Int](N)
    val ps = new PipeSort(N)
    // Run system
    run(generator(ps.left, xs) || ps() || collector(ps.right, ys))
    assert(xs.sorted.sameElements(ys))
  }


  def main(args : Array[String]) = {
    for(i <- 0 until 500){
      doTest
      if(i%10 == 0) print(".")
    }
    println
  }
}
