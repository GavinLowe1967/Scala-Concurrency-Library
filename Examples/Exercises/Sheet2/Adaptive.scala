import ox.scl._

/** Calculating integral, using trapezium rule, adaptive quadrature, and bag
  * of tasks pattern. */
class Adaptive(
  f: Double => Double, a: Double, b: Double, Epsilon: Double, nWorkers: Int){
  require(a <= b)

  // Interval on which to work
  private type Task = (Double, Double)

  /** Channel from the controller to the workers, to distribute tasks.  We
    * create the channel freshly for each run of the system. */
  private val toWorkers = new BuffChan[Task] (nWorkers)

  /** Channel from the workers to the bag, to return subtasks. */
  private val toBag = new BuffChan[(Task,Task)](nWorkers)

  /** Channel from the workers to the adder process, to add up subresults. */
  private val toAdder = new BuffChan[Double](nWorkers)

  /** Channel to indicate to the bag that a worker has completed a task. */
  private val done = new BuffChan[Unit](nWorkers)

  /** A client, who receives arguments from the server, either estimates the
    * integral directly or returns new tasks to the bag. */
  private def worker = thread("worker"){
    repeat{
      val (a,b) = toWorkers?()
      val mid = (a+b)/2.0
      val fa = f(a); val fb = f(b); val fmid = f(mid)
      val larea = (fa+fmid)*(mid-a)/2
      val rarea = (fmid+fb)*(b-mid)/2
      val area = (fa+fb)*(b-a)/2
      if (Math.abs(larea+rarea-area) < Epsilon) toAdder!area
      else toBag!((a,mid), (mid,b)) 
      done!(())
    }
  }

  /** The bag, that keeps track of jobs pending. */
  private def bag = thread("bag"){
    val stack = new scala.collection.mutable.Stack[Task]
    stack.push((a,b))
    var busyWorkers = 0 // # workers with tasks

    serve(
      stack.nonEmpty && toWorkers =!=> { busyWorkers += 1; stack.pop }
        | busyWorkers > 0 && toBag =?=> { case (t1,t2) =>
          stack.push(t1); stack.push(t2) }
        | busyWorkers > 0 && done =?=> { _ => busyWorkers -= 1 }
    )
    // busyWorkers == 0 && stack.isEmpty
    toWorkers.endOfStream; toAdder.endOfStream
  }

  private var result = 0.0

  // Process to receive results from workers and add up the results
  private def adder = thread("adder"){ repeat{ result += (toAdder?()) } }

  def apply(): Double = {
    val workers = || (for (i <- 0 until nWorkers) yield worker)
    run(workers || bag || adder)
    result
  }
}
