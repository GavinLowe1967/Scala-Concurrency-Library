/** A bag of tasks for numerical integration from a to b, using n intervals 
  * and nTasks tasks. */
class BagOfTasksMonitor(a: Double, b: Double, n: Int, nTasks: Int){
  require(n%nTasks == 0)

  type Task = (Double, Double, Int, Double)

  /** The size of each interval. */
  private val delta = (b-a)/n

  /** The number of intervals in each task. */
  private val taskSize = n/nTasks

  /** The size of each task. */
  private val taskRange = (b-a)/nTasks
  
  /** The left-hand of the next interval. */
  private var left = a

  /** Get a task.
    * @throws Stopped exception if there are no more tasks. */
  def getTask: Task = synchronized{
    if(left < b-(taskRange/2)){ // protect against rounding errors
      val oldLeft = left; left = (left + taskRange) min b
      (oldLeft, left, taskSize, delta)
    }
    else throw new ox.scl.Stopped
  }
}

/** A collector, to sum up partial results. */
class Collector{
  private var result = 0.0

  /** Add x to the result. */
  def add(x: Double) = synchronized{ result += x }

  /** Get the result. */
  def get: Double = synchronized{ result }
}
