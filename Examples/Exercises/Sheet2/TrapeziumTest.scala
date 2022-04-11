// import io.threadcso._
/** Abstract class, representing the problem of calculating the integral of f
  * from a to b. */
abstract class TrapeziumT(f: Double => Double, a: Double, b: Double){
  require(a <= b)

  /** Calculate the integral. */
  def apply(): Double

  /** Use trapezium to calculate integral of f from left to right, using n
    * intervals of size delta.  Pre: n*delta = right-left. */
  protected def integral(left: Double, right: Double, n: Int, delta: Double)
      : Double = {
    require(n > 0)
    // assert(n*delta == right-left); this fails because of rounding errors!
    require(Math.abs(n*delta - (right-left)) < 0.000000001)
    var sum: Double=(f(right)+f(left))/2.0
    for(i <- 1 until n) sum += f(left+i*delta)
    sum*delta
  }
}

class SeqTrapezium(f: Double => Double, a: Double, b: Double, n: Int)
    extends TrapeziumT(f, a, b){
  require(n > 0)

  def apply() = integral(a, b, n, (b-a)/n)
}

/** Object to test the concurrent Trapezium rule code. */
object TrapeziumTest{
  /** We will test the Trapezium class by selecting random polynomials.  Each
    * polynomial will be represented by an array of its coefficients.  The
    * polynomial p represents the sum of p(i)*x^i, where i ranges over p's
    * indices. */ 
  type Polynomial = Array[Double]

  /* We'll create random polynomials, with degree uniform in [0..MaxDegree), and
    * coefficients uniform in [-MaxCoeff..MaxCoeff). */
  val random = scala.util.Random
  val MaxDegree = 5
  val MaxCoeff = 100

  /** Random Double in [-max, max). */
  def uniform(max: Double): Double = max*(2*random.nextDouble-1)

  /** Create a random polynomial. */
  def mkPoly: Polynomial = 
    Array.fill(1+random.nextInt(MaxDegree))(uniform(MaxCoeff))

  /** Convert a poly to a string. */
  def toString(poly: Polynomial): String =
    (0 until poly.length).map(i => poly(i).toString+"x^"+i).mkString(" + ")

  /** Evaluate poly at x. */
  def evalPoly(poly: Polynomial)(x: Double): Double = {
    // Use Horner's rule
    var result = 0.0
    for(i <- poly.size-1 to 0 by -1) result = result*x + poly(i)
    result
  }

  /** Pick parameters for a test.
    * @return a tuple (f, p, a, b, nWorkers, n) indicating that the integral of f
    * from a to b should be estimated using n intervals and nWorkers workers,
    * and that f corresponds to p. */
  def pickParams: (Double => Double, Polynomial, Double, Double, Int, Int) = {
    // function to evaluate
    val p = mkPoly; val f = evalPoly(p)(_)
    // limits
    val a = uniform(10); val b = a+10*random.nextDouble
    // Number of workers
    val nWorkers = 1+random.nextInt(16)
    // Number of intervals
    val n = nWorkers + random.nextInt(1000)
    (f, p, a, b, nWorkers, n)
  }

  // /** Example showing the two methods might not always reach the same result,
  //   * and the concurrent version might give inconsistent results. */
  // def compareExact = {
  //   val square = evalPoly(Array(0.0, 0.0, 1.0))(_)
  //   println("sequential result = "+new SeqTrapezium(square, 0.0, 3.0, 100)())
  //   for(i <- 0 until 5)
  //     println("concurrent result = "+new Trapezium(square, 0.0, 3.0, 100, 10)())
  // }
  // /* Typical result:
  //  * sequential result = 9.000449999999995
  //  * concurrent result = 9.000449999999999
  //  * concurrent result = 9.000449999999997
  //  * concurrent result = 9.000449999999999
  //  * concurrent result = 9.000449999999999
  //  * concurrent result = 9.000449999999997 */


  // def main(args: Array[String]) = {
  //   // parse arguments
  //   var doCompareExact = false
  //   var doBagOfTasks = false; var doBagOfTasksObjects = false
  //   var reps = 20_000; var syncChan = false
  //   var i = 0
  //   while(i < args.length) args(i) match{
  //     case "--compareExact" => doCompareExact = true; i += 1
  //     case "--bagOfTasks" => doBagOfTasks = true; i += 1
  //     case "--bagOfTasksObjects" => doBagOfTasksObjects = true; i += 1
  //     case "--reps" => reps = args(i+1).toInt; i += 2
  //     case "--syncChan" => syncChan = true; i += 1
  //     case arg => println("Argument not recognised: "+arg); sys.exit
  //   }
  //   if(doCompareExact) compareExact

  //   for(i <- 0 until reps){
  //     val (f, p, a, b, nWorkers, n) = pickParams
  //     val seqResult = new SeqTrapezium(f, a, b, n)()
  //     val concResult =
  //       if(doBagOfTasks || doBagOfTasksObjects){
  //         val nTasks = 1+random.nextInt(n)
  //         assert(0 < nTasks && nTasks <= n)
  //         if(doBagOfTasksObjects)
  //           new TrapeziumBagObjects(f, a, b, n, nWorkers, nTasks)()
  //         else new TrapeziumBag(f, a, b, n, nWorkers, nTasks)()
  //       }
  //       else new Trapezium(f, a, b, n, nWorkers)()
  //     // println(seqResult+"; "+concResult)
  //     assert(
  //       seqResult != 0.0 && Math.abs((seqResult-concResult)/seqResult) < 1E-7 ||
  //         Math.abs(seqResult-concResult) < 1E-10,
  //       "failed\nf = "+toString(p)+"\n"+
  //         "a = "+a+"; b = "+b+"; n = "+n+"; nWorkers = "+nWorkers+"\n"+
  //         "seqResult = "+seqResult+"; concResult = "+concResult)
  //     if(i%1000 == 0 || doBagOfTasks && i%100 == 0){
  //       print("."); if (i%10_000 == 0) print(i)
  //     }
  //   }
  //   println
  // } 

}
