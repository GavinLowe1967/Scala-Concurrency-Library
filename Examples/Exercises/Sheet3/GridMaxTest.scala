
import scala.util.Random

/** Test for GridMax and LogGridMax. */
object GridMaxTest{
  // Which version to use: useLog -> LogGridMax; useLog2 -> LogGridMax2;
  // neither -> GridMax
  var useLog = false; var useLog2 = false

  /** Run a single test. */
  def doTest() = {
    val n = 1+Random.nextInt(10)
    val xss = Array.fill[Int](n, n)(Random.nextInt(1000))
    val results = 
      if(useLog) new LogGridMax(n, xss)() 
      else if(useLog2) new LogGridMax2(n, xss)() else new GridMax(n, xss)()
    val expected = xss.map(_.max).max
    assert(results.forall(_.forall(_ == expected)))
  }

  /** Main method. */
  def main(args: Array[String]) = {
    useLog = args.nonEmpty && args(0) == "--useLog"
    useLog2 = args.nonEmpty && args(0) == "--useLog2"
    for(i <- 0 until 10000){
      doTest()
      if(i%100 == 0) print(".")
    }
    println
  }
}
