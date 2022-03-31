import scala.util.Random

/** Testing object, testing the concurrent solution against a sequential one. */
object JacobiTest{
  val reps = 10000 // Number of repetitons.

  /** A simple test. */
  def test0(useConc: Boolean) = {
    val n = 8
    val a = Array.tabulate[Double](n, n){ (i,j) => if (i == j) (i+1)*n else 1.0 }
    val b = Array.fill[Double](n)(n)
    val solver = if(useConc) new ConcJacobi(8) else SeqJacobi
    val x = solver.solve(a, b)
    printMul(a, b, x, n)
  }

  /** Print a particular solution. */
  def printMul(a: Array[Array[Double]], b: Array[Double],
               x: Array[Double], n: Int) = {
    for(i <- 0 until n){
      for(j <- 0 until n) printf("%2.3f\t", a(i)(j))
      printf("|%2.3f| = |%2.3f|", x(i), b(i))
      println
    }
    println
  }

  /** Create a random array a of size n by n such that for all i, 
    * |a(i)(i)| > \sum_{j != i} |a(i)(j)|. */
  def mkA(n: Int): Array[Array[Double]] = {
    val a = Array.ofDim[Double](n, n)
    for(i <- 0 until n){
      var sum = 0.0 // sum of |a(i)(j)| so far
      for(j <- 0 until n; if j != i){
        a(i)(j) = 20*Random.nextDouble-10.0; sum += Math.abs(a(i)(j))
      }
      a(i)(i) = (sum + Random.nextDouble*20) * (if(Random.nextBoolean) 1 else -1)
    }
    a
  }

  // Values of switch passed to runTest, indicating the type of concurrent
  // solver to use.
  private val Nothing = -1; val Conc = 0; private val MP0 = 1; private val MP = 2

  /** Run a single test. */
  def runTest(switch: Int) = {
    // Create parameters
    val p = 2+Random.nextInt(8); val n = p*(1+Random.nextInt(5))
    val a = mkA(n); val b = Array.fill(n)(Random.nextDouble*20)
    // Sequential and concurrent solutions
    val xs = SeqJacobi.solve(a,b)
    val concSolver = 
      if(switch == Conc) new ConcJacobi(p)
      else if(switch == MP0) new JacobiMP0(p)
      else if(switch == MP) new JacobiMP(p)
      else null // this shouldn't happen
    val xc = concSolver.solve(a,b)
    assert(xs.sameElements(xc),
           { printMul(a, b, xs, n); printMul(a, b, xc, n); "Error"})
  }

  def main(args: Array[String]) = {
    // Parse arguments
    var switch = Nothing; var i = 0
    while(i < args.length) args(i) match{
      case "--conc" => switch = Conc; i += 1
      case "--MP0" => switch = MP0; i += 1
      case "--MP" => switch = MP; i += 1
    }
    if(switch == Nothing){
      println("Type of concurrent solver not specified"); sys.exit
    }

    // Run tests
    for(i <- 0 until reps){
      runTest(switch); if(i%50 == 0) print(".")
    }
    println
  }
}

