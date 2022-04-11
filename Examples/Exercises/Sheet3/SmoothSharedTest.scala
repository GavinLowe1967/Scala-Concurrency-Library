

/** A sequential smoothing algorithm. */
class SmoothSequential(a: Array[Array[Boolean]], maxIters: Int){
  private val n = a.length; private val w = a(0).length
  assert(a.forall(_.length == w))

  def apply() = {
    var done = false; var iters = 0

    while(!done && iters < maxIters){
      done = true
      val newA = Array.ofDim[Boolean](n, w)
      for(i <- 0 until n; j <- 0 until w){
	newA(i)(j) = Smooth.majority(a, i, j)
	done &&= (newA(i)(j) == a(i)(j))
      }
      iters += 1
      for(i <- 0 until n) a(i) = newA(i) // update for next round
    }
  }
}
      
// -------------------------------------------------------

import scala.util.Random

/** Test of SmoothShared, comparing it with the sequential implementation. */
object SmoothSharedTest{
  val n = 40 // size of image
  val w = 30 // width of image
  val p = 10 // # workers
  val maxIters = 40 // max # iterations
  var use2 = true // Do we use SmoothShared2? 

  /** Do a single test. */
  def doTest = {
    val a = Array.fill(n,w)(Random.nextFloat >= 0.55)
    val a1 = a.map(_.clone)
    if(use2) new SmoothShared2(a, p, maxIters)()
    else new SmoothShared(a, p, maxIters)()
    new SmoothSequential(a1, maxIters)()
    assert((0 until n).forall(i => a(i).sameElements(a1(i))),
           { Smooth.printArray(a); Smooth.printArray(a1); n })
  }

  def main(args: Array[String]) = {
    var i = 0
    while(i < args.length) args(i) match{
      case "--use2" => use2 = true; i += 1
    }

    for(i <- 0 until 1000){
      doTest; if(true || i%200 == 0) print(".")
    }
    println
  }
}
