import ox.gavin.experiments.{Experiments,Graphs}
// import ox.gavin.profiling.{SamplingProfiler,ProfilerSummaryTree}


/** The main experiment program. */
object TrapeziumExperiment{
  val Million = 1000000L
  val Billion = 1000L*Million

  /** Get the value of environment variable arg. */
  def getEnv(arg: String): String = {
    val proc = java.lang.Runtime.getRuntime().exec(arg)
    val isr = new java.io.InputStreamReader(proc.getInputStream, "ISO-8859-1")
    val br = new java.io.BufferedReader(isr)
    br.readLine
  }

  val hostname = getEnv("hostname") // java.lang.System.getenv("hostname")
  println(s"hostname: $hostname")

  /** Classpath.  Note: this needs to be edited. */
  val cp =
    if(hostname == "Gavin--T5500") "-cp .:/home/gavin/Scala/SCL"
    else if(hostname == "casteret") "-cp .:/home/gavinl/Scala/SCL"
    else{ 
      println(s"Hostname $hostname not recognised.  Using default classpath.")
      ""
    }

  println(s"classpath: $cp")

  // Basic command
  val cmd0 = s"scala $cp TrapeziumRun "

  /** Parameters defining statistical analysis of experiments: repeat for at
    * least 5 observations, and until 95% confidence half-interval is at most
    * 1% of mean or until 50 repetitions done. */
  val strictParams = new Experiments.Params(5, 50, 0.05, 0.01)
  // Very strict version
  val veryStrictParams = new Experiments.Params(5, 100, 0.05, 0.005)
  // less accurate version; useful for getting the idea of the graph
  val normalParams = new Experiments.Params(4, 10, 0.05, 0.02)
  // Very rough version; useful for testing the program
  val quickParams = new Experiments.Params(2, 0.05, 0.1)

  // var syncChan = false
  var server = false

  /** Amount of buffering to use (in experiments not concerning buffering). */
  var buffering = 0

  /** Number of workers to use in linear experiments. */
  def numsWorkers =
    if(server) ((32 to 60 by 4)++(61 to 67)++(68 to 96 by 4)).toArray
    else ((10 to 16 by 2)++(17 to 23)++(24 to 32 by 2)).toArray

  // (logSize, logNumTasks) pairs, indicating the experiment should be run
  // on an integral of size 2^logSize, and with 2^logNumTasks tasks.
  def pairs =
      if(server) Array((22,8), (24,10), (26,12), (28,14))
        // Array((29,12), (29,13), (29,14) /*, (29,15), (29,16)*/) 
        // Array((22,8), (24,10), (26,12), (26,11), (28,14), (28,13))
      else Array((26, 13), (24, 12), (22, 11))


  /** Do a measurement corresponding to each command in cmds.
    * @param params the statistical parameters to use.
    * @param maxTime the maximum time, in ms, to allow; if one choice of command
    * takes longer than maxTime ms, then subsequent ones aren't performed.
    * @return array of means and confidence intervals. */
  def doMeasurements(
    cmds: Array[String], params: Experiments.Params,
    maxTime: Long = Long.MaxValue)
      : Array[(Double,Double)] = {
    val n = cmds.length
    // Array for results, in millis
    val results = Array.ofDim[(Double,Double)](n)
    var continue = true
    
    // Do measurements
    for(i <- 0 until n; if continue){
      val cmd = cmds(i); println(cmd)
      // A single measurement
      def measure : Double = { // returns time in nanos
        val t = Experiments.timeProc(cmd, verbose=true)
        print(s"${t/Million} ms ") // print time in millis
        t.toDouble
      }
      val (m, s) = Experiments.iterateMeasurement(measure, params)
      println("\n("+m/Million+"ms, "+s/Million+"ms)")
      results(i) = (m/Million,s/Million)
      continue = m/Million <= maxTime
    }
    results
  }

  /** Common options for the graphs. */
  val commonOptions = 
    Array[String](
      "title = Timing experiment on the numerical integration example",
      "ylabel = Time (ms)",
      "legend pos = north east",
      "height = 0.98\\textheight", "width = 0.98\\textwidth",
      "scaled ticks = false"
    )

  /** Output graphString to screen and to file fname. */
  def outputToFile(fname: String, graphString: String) = {
    println(graphString)
    Graphs.writeStandAloneFile(fname, graphString)
    println("Output written to "+fname)
  }

  /* ---------------- Actual experiments, first example----------------- */

  /** Run experiment where the number of workers is all powers of two up to
    * maxLogP. */
  def logScaleExperiment(maxLogP: Int, params: Experiments.Params) = {
    val maxTime = if(server) 12000 else 16000 // ms
    val ps = (0 to maxLogP).map(1 << _).reverse.toArray

    val logSizes = 
      if(server) Array(18, 20, 24, 28) else Array(16, 18, 20, 24)
    val sizes = logSizes.map(k => 1L << k)
    val plots = sizes.length
    val results = new Array[Array[(Double,Double)]](plots)
    for(i <- 0 until plots){
      val size = sizes(i); 
      val reps = if(server) (1L << 28)/size else (1L << 27)/size
      val cmd1 = s"$cmd0 --size $size --reps $reps --buffering $buffering "
      val cmds = ps.map(p => cmd1+" -p "+p)
      results(i) = doMeasurements(cmds, params, maxTime*10)
    }

    // Produce graphs
    val options =
      commonOptions++
        Array("xlabel = Number of workers", "legend pos = north west",
          "xmin = 1", "ymin = 0", "ymax = "+maxTime, "log basis x=2")
    val labels = logSizes.map(k => "$n = 2^{"+k+"}$")
    val graphString = 
      Graphs.makeLogXGraph(options, labels, ps, results)
      // Graphs.makeLogXGraph(options, Array("Time"), ps, Array(results))
    outputToFile("trapeziumExperimentLogScale.tex", graphString)
  }

  /** Run experiment for each number of workers in numsWorkers, with a linear
    * scale of workers. */
  def linearScaleExperiment(params: Experiments.Params) = {
    val total = if(server) 1<<29 else 1<<26
    val cmd1 =  cmd0+s" --buffering $buffering "
    val plots = pairs.length
    val results = new Array[Array[(Double,Double)]](plots)

    // Do measurements
    for(i <- 0 until plots){
      val (logSize, _) = pairs(i)
      val size = 1<<logSize; val reps = total/size
      val cmd2 =  cmd1+" --size "+size+" --reps "+reps
      val cmds = numsWorkers.map(p => cmd2+" -p "+p)
      results(i) = doMeasurements(cmds, params)
    }

    // Produce graphs
    val options = commonOptions++
      Array("title = Experiment on the numerical integration example.",
            "xlabel = Number of workers")
    val plotLabels = pairs.map{ case (logSize, _) => "$n = 2^{"+logSize+"}$" }
    val graphString = 
      Graphs.makeLinearGraph(options, plotLabels, numsWorkers, results)
    // val logSize = 28 
    // val cmds = numsWorkers.map(p => cmd0+" -p "+p+" --size "+(1<<logSize))
    // val results = doMeasurements(cmds, params)

    // // Produce graphs
    // val options = commonOptions++Array("xlabel = Number of workers")
    // val graphString = 
    //   Graphs.makeLinearGraph(
    //     options, Array("$n = 2^{"+logSize+"}$"), numsWorkers, Array(results))
    outputToFile("trapeziumExperimentLinearScale.tex", graphString)
  }

  /*  -----------  Bag of tasks experiments ----------------- */

  /** Run experiment considering the number of tasks for the bag-of-tasks
    * example. */
  def numTasksExperiment(
      numWorkers: Int, params: Experiments.Params, monitors: Boolean) = {
    val TMax = 4000 // Maximum time we'll consider, in ms
    // numsTasks gives the numbers of tasks
    val tasksPerWorker = (0 to 8).map(1<<_).toArray 
    val numsTasks = tasksPerWorker.map(_*numWorkers)
    val cmd1 = cmd0+(if(monitors) "--bagOfTasksMonitors" else "--bagOfTasks")+ 
      s" -p $numWorkers --buffering $buffering "

    // sizes gives the number of intervals in the integral
    val logSizes = Array(20, 22, 24, 26, 28)
    val sizes = logSizes.map(k => 1L << k)
    val n = numsTasks.length
    // Array for results, in millis
    val results = Array.ofDim[(Double,Double)](sizes.length, n)
    for(j <- 0 until sizes.length){
      val size = sizes(j); val reps = (1L <<  28)/size
      val cmd2 = cmd1+" --size "+size+" --reps "+reps
      val cmds = numsTasks.map(cmd2+" --numTasks "+_)
      results(j) = doMeasurements(cmds, params, TMax)
    }

    // Produce graphs
    val options =
      commonOptions++
        Array("title = Experiment on the numerical integration bag-of-tasks "+
                "example considering the number of tasks.",
              "xlabel = Tasks per worker", "log basis x=2", "ymax = "+TMax)
    val plotLabels = logSizes.map("$2^{"+_+"}$")
    val graphString = 
      Graphs.makeLogXGraph(options, plotLabels, tasksPerWorker, results)
    outputToFile("trapeziumBagExperiment.tex", graphString)
  }

  /** Experiment to find optimal number of workers in the bag-of-tasks
    * example. */
  def bagOfTasksNumWorkers(params: Experiments.Params, monitors: Boolean) = {
    val TMax = 2000 
    val total = if(server) 1<<29 else 1<<26
    val cmd1 = cmd0+(if(monitors) " --bagOfTasksMonitors" else " --bagOfTasks")+
      s" --buffering $buffering "
    // Numbers of workers; it might be better to start from 4 = 1<<2 workers
    val numsWorkers = (0 to 8).map(1 << _).toArray
    val plots = pairs.length
    val results = new Array[Array[(Double,Double)]](plots)

    // Do measurements
    for(i <- 0 until plots){
      val (logSize, logNumTasks) = pairs(i)
      val size = 1<<logSize; val reps = total/size
      val cmd2 =
        cmd1+" --size "+size+" --reps "+reps+" --numTasks "+(1<<logNumTasks)
      val cmds = numsWorkers.map(p => cmd2+" -p "+p)
      results(i) = doMeasurements(cmds, params)
    }

    // Produce graphs
    val options = commonOptions++
      Array("title = Experiment on the numerical integration bag-of-tasks "+
                "example.",
            "xlabel = Number of workers", "log basis x=2", "ymax = "+TMax)
    val plotLabels = pairs.map{ case (logSize, logNumTasks) =>
      "$n = 2^{"+logSize+"}$; $2^{"+logNumTasks+"}$ tasks" }
    val graphString = 
      Graphs.makeLogXGraph(options, plotLabels, numsWorkers, results)
    outputToFile("trapeziumBagNumsWorkersExperiment.tex", graphString)
  }

  /** Experiment to find optimal number of workers in the bag-of-tasks
    * example. */
  def bagOfTasksNumWorkersLinear(
      params: Experiments.Params, monitors: Boolean) = {
    // val TMax = 4000
    /* With server, (29,12) and (29,13) work well.  (29,14) is noticeably slower
     * (why?) and has a big improvement at 63 workers (why?)  */
    val total = if(server) 1<<29 else 1<<26
    val cmd1 =  cmd0+(if(monitors) " --bagOfTasksMonitors" else " --bagOfTasks")+
      s" --bagOfTasks --buffering $buffering "
    // Numbers of workers; it might be better to start from 4 = 1<<2 workers
    val plots = pairs.length
    val results = new Array[Array[(Double,Double)]](plots)

    // Do measurements
    for(i <- 0 until plots){
      val (logSize, logNumTasks) = pairs(i)
      val size = 1<<logSize; val reps = total/size
      val cmd2 =
        cmd1+" --size "+size+" --reps "+reps+" --numTasks "+(1<<logNumTasks)
      val cmds = numsWorkers.map(p => cmd2+" -p "+p)
      results(i) = doMeasurements(cmds, params)
    }

    // Produce graphs
    val options = commonOptions++
      Array("title = Experiment on the numerical integration bag-of-tasks "+
                "example.",
            "xlabel = Number of workers")
    val plotLabels = pairs.map{ case (logSize, logNumTasks) =>
      "$n = 2^{"+logSize+"}$; $2^{"+logNumTasks+"}$ tasks" }
    val graphString = 
      Graphs.makeLinearGraph(options, plotLabels, numsWorkers, results)
    outputToFile("trapeziumBagNumsWorkersLinearExperiment.tex", graphString)
  }

  /* -------------------- Concerning buffering --------------- */

  /** Do experiment corresponding to `cmd`, with no buffering, or buffering of
    * elements of sizes. */
  // def singlePlotBufferHelper(cmd: String, sizes: Array[Int], 
  //   params: Experiments.Params)
  //     : String = {
  //   // val results = new Array[Array[(Double,Double)]](1)
  //   val cmds = cmd +: sizes.map(size => cmd+" --buffering "+size)
  //   val result = doMeasurements(cmds, params, 20000)
  //   val options = commonOptions++
  //     Array("xlabel = Amount of buffering", "ymin = 0", "xtick = data",
  //       "symbolic x coords="+(0+:sizes).map(_.toString).mkString("{",",","}") )
  //   Graphs.makeLinearGraph(options, Array("Time"), 0 +: sizes, Array(result))
  // }

  /** For each command in `cmds`, produce a plot against the amount of buffering
    * (either none or a value from `sizes`), labelled with the corresponding
    * element of `labels`. */
  def bufferHelper(cmds: Array[String], labels: Array[String], 
    sizes: Array[Int], params: Experiments.Params)
      : String = {
    assert(cmds.length == labels.length)
    val results = new Array[Array[(Double,Double)]](cmds.length)
    for(i <- 0 until cmds.length){
      val cmd1 = cmds(i)
      val cmds1 = cmd1 +: sizes.map(size => cmd1+" --buffering "+size)
      results(i) = doMeasurements(cmds1, params, 30000)
    }
    val options = commonOptions++
      Array("title = Experiment on the benefits of buffering", 
        "xlabel = Amount of buffering",  "xtick = data",
        "symbolic x coords="+(0+:sizes).map(_.toString).mkString("{",",","}") )
    Graphs.makeLinearGraph(options, labels, 0 +: sizes, results)
  }


  /** Experiment with different amounts of buffering for different sizes (fixed
    * number of workers). */
  def bufferingExperiment(params: Experiments.Params) = {
    val logIntervals = if(server) Array(18,19,20,24) else Array(15,16,17,18)
    val intervals = logIntervals.map(1L << _) // (_ << 12)
    val p = if(server) 64 else 8
    val cmd1 = s"$cmd0 -p $p "
    val total = if(server) 1<<28 else 1<<26 // size * reps
    val cmds = intervals.map(n => s"$cmd1 --size $n --reps "+(total/n))
    val buffs = (0 to 5).toArray.map(1 << _)   // Amounts of buffering
    val plotLabels = logIntervals.map("$n = 2^{"+_+"}$")
    val graphString = bufferHelper(cmds, plotLabels, buffs, params)
    outputToFile("trapeziumBuffering.tex", graphString)
  }

  def bagOfTasksBufferingExperiment(params: Experiments.Params) = {
    val logTasksPerWorker = Array(2,8,10,11,12)
    val tasksPerWorker = logTasksPerWorker.map(1 << _)
    val p = if(server) 64 else 8 // # workers
    val numsTasks = tasksPerWorker.map(_ * p)  // numbers of tasks
    val size = 1L << 24; val reps = (1L << 26)/size
    val cmd1 = s"$cmd0 --bagOfTasks -p $p --size $size --reps $reps "
    val cmds = numsTasks.map(n => s"$cmd1 --numTasks $n")
    val buffs = (0 to 4).toArray.map(1 << _)   // Amounts of buffering
    val plotLabels = logTasksPerWorker.map(n => "$2^{"+n+"}$ tasks per worker")
    val graphString = bufferHelper(cmds, plotLabels, buffs, params)
    outputToFile("trapeziumBagOfTasksBuffering.tex", graphString)
  }

  /* --------------------- Main function --------------------- */

  val helpString =
    "Usage: scala TrapeziumExperiment <options>.\n"+
      "The options setting the experiment are as follows:\n"+
      "--doLog: do an experiment with a logarithmic scale;\n"+
      "--doLinear: do an experiment with a linear number of workers;\n"+
      "--doBagNumTasks: do an experiment on the number of tasks per worker in the bag-of-tasks example;\n"+
      "--doBagNumWorkers: do an experiment on the number of workers in the bag-of-tasks example, using a logarithmic scale\n"+
      "--doBagNumWorkersLinear: do an experiment on the number of workers in the bag-of-tasks example, using a linear scale.\n"+
      "--doBuffer: do an experiment on the use of buffering.\n"+
      "--doBagBuffer: do an experiment on the use of buffering in the bag-of-tasks example.\n"+
  // 
      "Other options are as follows:\n"+
      "--quick: do a small number of samples for each choice of parameters;\n"+
      "--strict: do a large number of samples for each choice of parameters;\n"+
      "--veryStrict: do an larger number of samples for each choice of parameters;\n"+
      "--server: choose the parameters suitable for use on a server machine;\n"+
      "--help: print this help."

  /** Main function. */
  def main(args: Array[String]) = {
    // Parse arguments
    var doLog = false; var doLinear = false; var params = normalParams
    var doNumTasks = false; var doBagNumWorkers = false
    var doBagNumWorkersLinear = false; var doSyncChanExperiment = false
    var doBufferExperiment = false; var doBagBufferExperiment = false
    var useMonitors = false // for bag of tasks
    var i = 0
    while(i < args.length) args(i) match{
      case "--doLog" => doLog = true; i += 1
      case "--doLinear" => doLinear = true; i += 1

      case "--doBagNumTasks" => doNumTasks = true; i += 1
      case "--doBagNumWorkers" => doBagNumWorkers = true; i += 1
      case "--doBagNumWorkersLinear" => doBagNumWorkersLinear = true; i += 1
      case "--useMonitors" => useMonitors = true; i += 1

      //case "--doSyncChanExperiment" => doSyncChanExperiment = true; i += 1

      case "--doBuffer" => doBufferExperiment = true; i += 1
      case "--doBagBuffer" => doBagBufferExperiment = true; i += 1

      case "--quick" => params = quickParams; i += 1
      case "--strict" => params = strictParams; i += 1
      case "--veryStrict" => params = veryStrictParams; i += 1
      case "--server" => server = true; i += 1
      case "--buffering" => buffering = args(i+1).toInt; i += 2
      // case "--syncChan" => syncChan = true; i += 1
      case "--help" => println(helpString); sys.exit()
      case arg =>
        println("Unrecognised argument: "+arg+"\n"+helpString); sys.exit()
    }


    // Now run the experiments
    if(!doLog && !doLinear && !doNumTasks && !doBagNumWorkers &&
         !doBagNumWorkersLinear && !doSyncChanExperiment && 
         !doBufferExperiment && !doBagBufferExperiment)
      println("No experiment selected.\n"+helpString)
    // Buffering
    if(doBufferExperiment) bufferingExperiment(params)
    if(doBagBufferExperiment) bagOfTasksBufferingExperiment(params)
    // Number of workers, log scale
    if(doLog) logScaleExperiment(if(server) 9 else 6, params)
    // Number of workers, linear scale
    if(doLinear){
      // val ps =
      //   if(server)
      //     ((48 to 60 by 4)++(61 to 67)++(68 to 124 by 4)++(125 to 132)++(136 to 148 by 4)).toArray
      //     // ((20 to 32 by 4)++(33 to 35)++(36 to 60 by 4)++
      //     //    (61 to 67)++(68 to 96 by 4)
      //     // ).toArray
      //   else ((8 to 16 by 2)++(18 to 20)++(22 to 28 by 2)).toArray
      linearScaleExperiment(params)
    }
    // Experiment comparing synchronous channels
    //if(doSyncChanExperiment) syncChanExperiment(if(server) 64 else 16)
    // Number of tasks per worker in the bag of tasks example
    if(doNumTasks) numTasksExperiment(if(server) 64 else 16, params, useMonitors)
    // Number of workers in the bag of tasks example
    if(doBagNumWorkers) bagOfTasksNumWorkers(params, useMonitors)
    if(doBagNumWorkersLinear){
      // val pairs =
      //   if(server)
      //     (for(l <- 11 to 15) yield (28,l)).toArray
      //     // Array((28,15), (26,15), (26,13), (26,11), (24, 11), (22, 9))
      //   else Array((26, 13), (24, 12), (22, 11))
      bagOfTasksNumWorkersLinear(params, useMonitors)
    }

  }


}
