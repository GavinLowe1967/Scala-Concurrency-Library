import scala.util.Random

object JacobiObservation{
  val p = 8 // # threads
  val n = 6400 // size of matrix

  // Values of switch passed to runTest, indicating the type of concurrent
  // solver to use.
  private val Nothing = -1; val Conc = 0; private val MP0 = 1; private val MP = 2

  def makeObservation(switch: Int): Long = {
    val a = JacobiTest.mkA(n); val b = Array.fill(n)(Random.nextDouble*20)
    val start = System.nanoTime
    val concSolver = 
      if(switch == Conc) new ConcJacobi(p)
      else if(switch == MP0) new JacobiMP0(p)
      else if(switch == MP) new JacobiMP(p)
      else null // this shouldn't happen
    val xc = concSolver.solve(a,b)
    System.nanoTime - start
  }

  def main(args: Array[String]) = {
    var switch = Nothing; var i = 0
    while(i < args.length) args(i) match{
      case "--conc" => switch = Conc; i += 1
      case "--MP0" => switch = MP0; i += 1
      case "--MP" => switch = MP; i += 1
    }
    if(switch == Nothing){
      println("Type of concurrent solver not specified"); sys.exit
    }
    val obs = makeObservation(switch)
    println(obs)
  }

}

// -------------------------------------------------------

import ox.gavin.experiments.{Experiments,Graphs}

object JacobiExperiment{
  val cp = ".:/home/gavin/Scala/SCL" // class path
  val cmd0 = s"scala -cp $cp JacobiObservation "

  val Million = 1000000

  val switches = Array(
    ("shared variables", "--conc"),
    ("first message passing", "--MP0"),
    ("second message passing", "--MP")
  )

  val normalParams = new Experiments.Params(4, 10, 0.05, 0.02)
  val quickParams = new Experiments.Params(2, 0.05, 0.1)
  val params = normalParams

  def main(args: Array[String]) = {
    val numAlgs = switches.length
    val results = new Array[(Double,Double)](numAlgs)
    for(i <- 0 until numAlgs){
      val (desc, switch) = switches(i)
      println("Type "+desc)
      val cmd = cmd0+switch
      println(cmd)
      def measure: Double = { // returns time in nanos
        val t = Experiments.timeProc(cmd, verbose=true)
        print((t/Million).toString+"ms ") // print time in millis
        t.toDouble
      }
      val (m, s) = Experiments.iterateMeasurement(measure, params)
      println("\n("+m/Million+"ms, "+s/Million+"ms)")
      results(i) = (m/Million, s/Million)
    }
    // Print results
    for(i <- 0 until numAlgs){
      val (m,s) = results(i)
      println(switches(i)._1+"\t"+m+"ms\t"+s+"ms")
    }
  }

}
