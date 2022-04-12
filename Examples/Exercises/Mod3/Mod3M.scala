import ox.scl._

/** A trait describing the mod-3 example. */
trait Mod3{
  def enter(id: Int): Unit

  def exit(id: Int): Unit
}

// -------------------------------------------------------

/** An implementation using JVM monitors. */
class Mod3M extends Mod3{
  var current = 0
  // sum of identites of processes currently in critical region

  def enter(id: Int) = synchronized{
    while(current%3 != 0) wait()
    current += id
    if(current%3 == 0) notify()
  }

  def exit(id: Int) = synchronized{
    current -= id
    if(current%3 == 0) notify()
  }

  // Alternatively, one can note that the following is invariant: at most
  // one process whose identity is not divisible by 3 is present at any
  // time.  Therefore it would be enough to store a boolean recording
  // whether such a process is currently present.
}

// -------------------------------------------------------


/** An implementation using semaphores. */
class Mod3S extends Mod3{
  /** sum of identites of processes currently in critical region. */
  private var current = 0

  /** Number of processes waiting. */
  private var waiting = 0

  /** Semaphore for blocked processes to wait on. */
  private val entry = new SignallingSemaphore

  /** Semaphore for mutual exclusion. */
  private val mutex = new MutexSemaphore
    
  /** Pass the baton, either signalling for another process to enter, or lifting
    * the mutex. */
  private def signal = {
    if(current%3 == 0 && waiting > 0) entry.up
    else mutex.up
  }

  def enter(id: Int) = {
    mutex.down
    if(current%3 != 0){
      waiting += 1; mutex.up; entry.down // wait for a signal
      waiting -= 1
    }
    current += id
    signal
  }

  def exit(id: Int) = {
    mutex.down
    current -= id
    signal
  }
}

// -------------------------------------------------------

/** Another solution using semaphores. */
class Mod3S2 extends Mod3{
  /** sum of identites of processes currently in critical region. */
  private var current = 0

  /** Semaphore to protect shared variable. */
  private val mutex = new MutexSemaphore

  /** Semaphore on which threads may have to wait. */
  private val canEnter = new MutexSemaphore
  /* Invariant: if canEnter is up then current%3 = 0.  A thread that does
   * canEnter.down will be the next to enter. */

  def enter(id: Int) = {
    canEnter.down 
    assert(current%3 == 0)
    // at most one thread can be waiting here (+)
    mutex.down
    current += id
    if(current%3 == 0) canEnter.up
    mutex.up
  }

  def exit(id: Int) = {
    mutex.down
    current -= id // does this maintain the invariant? (*)
    if(current%3 == 0 && id%3 != 0) canEnter.up
    mutex.up
  }
}

// -------------------------------------------------------


object Mod3Test{
  var iters = 200 // # iterations by each worker; each iteration is two ops
  var reps = 1000  // # runs
  var numWorkers = 4 // # workers

  // Sequential specification: the threads currently in the critical region
  type S = Set[Int]

  def seqEnter(id: Int)(current: S): (Unit, S) = {
    require(current.sum%3 == 0); ((), current+id)
  }

  def seqExit(id: Int)(current: S): (Unit, S) = {
    assert(current.contains(id)); ((), current-id)
  }

  /*
  // Sequential specification: the number currently in the critical region
  type S = Int

  def seqEnter(id: Int)(current: S): (Unit, S) = {
    require(current%3 == 0); ((), current+id)
  }

  def seqExit(id: Int)(current: S): (Unit, S) = {
    assert(id <= current); ((), current-id)
  }
   */

  // A worker thread
  def worker(me: Int, log: LinearizabilityLog[S,Mod3]) = {
    for(i <- 0 until iters){
      log(_.enter(me), "enter("+me+")", seqEnter(me))
      log(_.exit(me), "exit("+me+")", seqExit(me))
    }
  }

  // The main method
  def main(args: Array[String]) = {
    val useSemaphore = args.nonEmpty && args(0) == "--semaphore"
    val useSemaphore2 = args.nonEmpty && args(0) == "--semaphore2"
    for(i <- 0 until reps){
      val mon: Mod3 = 
        if(useSemaphore) new Mod3S 
        else if(useSemaphore2) new Mod3S2 else new Mod3M
      val tester = LinearizabilityTester[S, Mod3](
        Set[Int](), mon, numWorkers, worker _)
      assert(tester() > 0)
      if(i%10 == 0) print(".")
    }
    println
  }
}
