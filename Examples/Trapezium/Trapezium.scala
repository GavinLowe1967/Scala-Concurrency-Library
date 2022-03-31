import ox.scl._

/** Abstract class, representing the problem of calculating the integral of f
  * from a to b. */
abstract class TrapeziumT(f: Double => Double, a: Double, b: Double){
  require(a <= b)

  /** Calculate the integral. */
  def apply(): Double

  /** Use trapezium to calculate integral of f from left to right, using n
    * intervals of size delta.  Pre: n*delta = right-left. */
  @inline protected 
  def integral(left: Double, right: Double, n: Int, delta: Double): Double = {
    require(n > 0)
    // assert(n*delta == right-left); this fails because of rounding errors!
    require(Math.abs(n*delta - (right-left)) < 0.000000001)
    var sum: Double = (f(right)+f(left))/2.0
    for(i <- 1 until n) sum += f(left+i*delta)
    sum*delta
  }
}

// ==================================================================

/** Sequential implementation. */
class SeqTrapezium(f: Double => Double, a: Double, b: Double, n: Int)
    extends TrapeziumT(f, a, b){
  require(n > 0)

  def apply() = integral(a, b, n, (b-a)/n)
}

// ==================================================================

/** Class to calculated the integral of f from a to b, using n intervals.  The
  * calculation uses using nWorkers worker threads, each of whom considers
  * a single range.  Buffered channels are used if buffChan is true. */
class Trapezium(
  f: Double => Double, a: Double, b: Double, n: Long, 
  nWorkers: Int, buffChan: Boolean = false)
    extends TrapeziumT(f, a, b){
  require(n >= nWorkers)

  /** Type of tasks to send to client.  The Task (left, right, taskSize, delta)
    * represents the task of calculating the integral from left to right,
    * using taskSize intervals of size delta. */
  private type Task = (Double, Double, Int, Double)

  /** Channel from the controller to the workers, to distribute tasks. */
  private val toWorkers: Chan[Task] =
    if(buffChan) new BuffChan[Task](nWorkers) else new SyncChan[Task]

  /** Channel from the workers to the controller, to return sub-results. */
  private val toController: Chan[Double] =
    if(buffChan) new BuffChan[Double](nWorkers) else new SyncChan[Double]

  /** A worker, which receives arguments from the controller, estimates the
    * integral, and returns the results. */
  private def worker = thread("worker"){
    val (left, right, taskSize, delta) = toWorkers?()
    val result = integral(left, right, taskSize, delta)
    toController!result
  }

  /** This variable ends up holding the result. */
  private var result = 0.0

  /** A controller, who distributes tasks to the clients, and accumulates the
    * sub-results into result. */
  private def controller = thread("controller"){
    // size of each interval
    val delta = (b-a)/n
    // Number of intervals not yet allocated.
    var remainingIntervals = n
    var left = a // left hand boundary of next task
    for(i <- 0 until nWorkers){
      // Number of intervals in the next task; the ceiling of
      // remainingIntervals/(nWorkers-i).
      val taskSize = ((remainingIntervals-1) / (nWorkers-i) + 1).toInt
      remainingIntervals -= taskSize
      val right = left+taskSize*delta
      toWorkers!(left, right, taskSize, delta)
      left = right
    }

    // Receive results, and add them up
    result = 0.0
    for(i <- 0 until nWorkers) result += (toController?())
  }    
    
  /** The main system. */
  private def system = {
    val workers = || (for (i <- 0 until nWorkers) yield worker)
    workers || controller
  }

  /** Calculate the integral, and return the result. */
  def apply: Double = { run(system); result } 
}

// ==================================================================

/** Class to calculated the integral of f from a to b using n intervals.  The
  * calculation uses using nWorkers workers threads, and nTasks tasks.
  * Buffered channels are used if buffChan is true. */
class TrapeziumBag(
  f: Double => Double, a: Double, b: Double, n: Long, 
  nWorkers: Int, nTasks: Int, buffChan: Boolean = false)
    extends TrapeziumT(f, a, b){
  require(0 < nTasks && nTasks <= n && n/nTasks < (1<<31)-1 )

  /** Type of tasks to send to client.  The Task (left, right, taskSize, delta)
    * represents the task of calculating the integral from left to right,
    * using taskSize intervals of size delta. */
  private type Task = (Double, Double, Int, Double)

  /** Channel from the controller to the workers, to distribute tasks. */
  private var toWorkers: Chan[Task] = 
    if(buffChan) new BuffChan[Task](nWorkers) else new SyncChan[Task]

  /** Channel from the workers to the controller, to return sub-results. */
  private val toController: Chan[Double] =
    if(buffChan) new BuffChan[Double](nWorkers) else new SyncChan[Double]

  /** A worker, which repeatedly receives arguments from the distributor,
    * estimates the integral, and sends the result to the collector. */
  private def worker = thread("worker"){
    repeat{
      val (left, right, taskSize, delta) = toWorkers?()
      assert(taskSize > 0)
      val result = integral(left, right, taskSize, delta)
      toController!result
    }
  }

  /** A distributor, who distributes tasks to the clients. */
  private def distributor = thread("distributor"){
    // size of each interval
    val delta = (b-a)/n
    // Number of intervals not yet allocated.
    var remainingIntervals = n
    var left = a // left hand boundary of next task
    for(i <- 0 until nTasks){
      // Number of intervals in the next task; the ceiling of
      // remainingIntervals/(nTasks-i).
      val taskSize = ((remainingIntervals-1) / (nTasks-i) + 1).toInt
      assert(taskSize > 0, s"$n; $nTasks")
      remainingIntervals -= taskSize
      val right = left+taskSize*delta
      toWorkers!(left, right, taskSize, delta)
      left = right
    }
    toWorkers.closeOut
  }

  /** This variable ends up holding the result. */
  private var result = 0.0

  /** A collector, that accumulates the sub-results into result. */
  private def collector = thread("collector"){
    result = 0.0
    for(i <- 0 until nTasks) result += toController?()
  }

  /** The main system. */
  private def system = {
    val workers = || (for (i <- 0 until nWorkers) yield worker)
    workers || distributor || collector
  }

  def apply: Double = { run(system); result } 
}

// =======================================================

/**  Class to calculated the integral of f from a to b using n intervals.  The
  * calculation uses using nWorkers workers threads, and nTasks tasks.
  * Buffered channels are used if buffChan is true.  This version
  * encapsulates the concurrency within objects. */
class TrapeziumBagObjects(
  f: Double => Double, a: Double, b: Double, n: Long, 
  nWorkers: Int, nTasks: Int, buffChan: Boolean = false)
    extends TrapeziumT(f, a, b){
  require(0 < nTasks && nTasks <= n && n/nTasks < (1<<31)-1 )

  /** Type of tasks to send to client.  The Task (left, right, taskSize, delta)
    * represents the task of calculating the integral from left to right,
    * using taskSize intervals of size delta. */
  private type Task = (Double, Double, Int, Double)

  /** The bag of tasks object. */
  private class BagOfTasks{
    /** Channel from the controller to the workers, to distribute tasks. */
    private val toWorkers = 
      if(buffChan) new BuffChan[Task](nWorkers) else new SyncChan[Task]

    /** Get a task.  
      * @throws Stopped exception if there are no more tasks. */
    def getTask: Task = toWorkers?()

    /** A server process, that distributes tasks. */
    private def server = thread{
      // size of each interval
      val delta = (b-a)/n
      // Number of intervals not yet allocated.
      var remainingIntervals = n
      var left = a // left hand boundary of next task
      for(i <- 0 until nTasks){
        // Number of intervals in the next task; the ceiling of
        // remainingIntervals/(nTasks-i).
        val taskSize = ((remainingIntervals-1) / (nTasks-i) + 1).toInt
        assert(taskSize > 0, s"$n; $nTasks")
        remainingIntervals -= taskSize
        val right = left+taskSize*delta
        toWorkers!(left, right, taskSize, delta)
        left = right
      }
      toWorkers.closeOut
    }

    // Start the server running
    server.fork
  }

  /** A collector object that receives sub-results from the workers, and adds
    * them up. */
  private class Collector{
    /** Channel from the workers to the controller, to return sub-results. */
    private val toController = 
      if(buffChan) new BuffChan[Double](nWorkers) else new SyncChan[Double]

    /** Channel that sends the final result. */
    private val resultChan = new SyncChan[Double]

    /** A collector, that accumulates the sub-results. */
    private def server = thread{
      var result = 0.0
      for(i <- 0 until nTasks) result += toController?()
      resultChan!result
    }

    // Start the server running
    server.fork

    /** Add x to the result. */
    def add(x: Double) = toController!x

    /** Get the result. */
    def get: Double = resultChan?()
  }


  /** A worker, which repeatedly receives tasks from the BagOfTasks, estimates
    * the integral, and adds the result to the Collector. */
  private def worker(bag: BagOfTasks, collector: Collector) = thread{
    repeat{
      val (left, right, taskSize, delta) = bag.getTask
      assert(taskSize > 0)
      val result = integral(left, right, taskSize, delta)
      collector.add(result)
    }
  }

  /** Calculate the integral. */
  def apply: Double = {
    val bag = new BagOfTasks; val collector = new Collector
    val workers = || (for (i <- 0 until nWorkers) yield worker(bag, collector))
    run(workers)
    collector.get
  }
}
