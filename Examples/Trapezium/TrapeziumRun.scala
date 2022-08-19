import ox.gavin.profiling.{SamplingProfiler,ProfilerSummaryTree}

// Profile using something like 
// scala -cp .:/home/gavin/Scala/CSO:/home/gavin/Scala/Util TrapeziumRun -p 16 --numTasks 6400 --bagOfTasks --profile

/** An object that performs a single observation. */
object TrapeziumRun{
  // The function to integrate
  def f(x: Double) = x*x*Math.cos(x)

  // Limits of integration
  val a = -100000.0; val b = 100000.0

  val Million = 1000000
  val Billion = 1000*Million

  def main(args: Array[String]) = {
    var p = -1 // # workers
    var reps = 1 // # repetitions
    var buffering = -1 // amount of buffering
    var bagOfTasks = false; var numTasks = -1
    var bagOfTasksMonitors = false
    var size: Long = 1L<<28 // Number of intervals
    // var syncChan = false; 
    var profiling = false

    var i = 0
    while(i < args.length) args(i) match{
      case "-p" => p = args(i+1).toInt; i += 2
      case "--reps" => reps = args(i+1).toInt; i += 2
      case "--bagOfTasks" => bagOfTasks = true; i += 1
      case "--bagOfTasksMonitors" => bagOfTasksMonitors = true; i += 1
      case "--numTasks" => numTasks = args(i+1).toInt; i += 2
      case "--size" => size = args(i+1).toLong; i += 2
      case "--buffering" => buffering = args(i+1).toInt; i += 2
      // case "--syncChan" => syncChan = true; i += 1
      case "--profile" => profiling = true; i += 1
      case arg => println("Unrecognised argument: "+arg); sys.exit()
    }
    assert(p > 0)
    if(bagOfTasks || bagOfTasksMonitors) assert(numTasks > 0)
 
    val start = System.nanoTime
    def trap =
      if(bagOfTasks)
        new TrapeziumBag(f, a, b, n=size, nWorkers=p, nTasks=numTasks, buffering)
      else if(bagOfTasksMonitors)
        new TrapeziumBagObjects(f, a, b, n=size, nWorkers=p, nTasks=numTasks, 
          useMonitor=true)
      else new Trapezium(f, a, b, size, p, buffering)
    if(profiling){      
      def filter(frame: StackTraceElement) : Boolean = {
        val className = frame.getClassName
        SamplingProfiler.defaultFilter(frame) &&
          !className.contains("jdk.internal") && 
          !className.contains("ox.scl.lock")
      }
      // val printer = SamplingProfiler.print(filter = filter, length = 60) _    
      val printer = SamplingProfiler.printTree(
          filter = filter,
          expand = ProfilerSummaryTree.expandToThreshold(0.05)) _
      val profiler = new SamplingProfiler(interval = 50, printer)
      profiler{ for(_ <- 0 until reps) trap() }
      val duration = (System.nanoTime - start)/Million
      println(s"$duration ms")
    }
    else{
      for(_ <- 0 until reps) trap()
      val duration = System.nanoTime - start
      println(duration)
    }
  }
}
