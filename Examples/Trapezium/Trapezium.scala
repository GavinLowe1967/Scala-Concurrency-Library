import ox.scl._


/** Class to calculated the integral of `f` from `a` to `b`, using `n`
  * intervals.  The calculation uses `nWorkers` worker threads, each of whom
  * considers a single range.  If `buffered` > 0, use buffered channels with
  * that capacity. */
class Trapezium(
  f: Double => Double, a: Double, b: Double, n: Long, 
  nWorkers: Int, buffering: Int = 0)
    extends TrapeziumT(f, a, b){
  require(n >= nWorkers)

  /** Type of tasks to send to client.  The Task (left, right, taskSize, delta)
    * represents the task of calculating the integral from left to right,
    * using taskSize intervals of size delta. */
  private type Task = (Double, Double, Int, Double)

  private def mkChan[A: scala.reflect.ClassTag]: Chan[A] = 
    if(buffering > 0) new BuffChan[A](buffering) else new SyncChan[A]

  /** Channel from the controller to the workers, to distribute tasks. */
  private val toWorkers: Chan[Task] = mkChan[Task]

  /** Channel from the workers to the controller, to return sub-results. */
  private val toController: Chan[Double] = mkChan[Double]

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
  def apply(): Double = { run(system); result } 
}


