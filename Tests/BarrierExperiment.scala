package tests

import ox.scl._
import java.lang.System.nanoTime

/** Experiment to compare linear and logarithmic implementations of a
  * barrier. */
object BarrierExperiment{
  /** Should we use the linear version? */
  var useLinear = false

  /** Number of synchronisations per run. */
  var iters = 1_000

  /** Number of runs. */
  var reps = 100

  /** Number of threads. */
  var n = 8

  /* The barriers. */
  var logBarrier: ox.scl.lock.Barrier = null // = new Barrier(n)
  var linearBarrier: LinearBarrier = null // = new LinearBarrier(n)

  /** Worker that synchronises iters times. */
  def worker(id: Int) = thread{
    for(_ <- 0 until iters){
      if(useLinear) linearBarrier.sync else logBarrier.sync(id)
    }
  }

  def system: ThreadGroup = || (for(i <- 0 until n) yield worker(i))

  def main(args: Array[String]) = {
    var i = 0
    while(i < args.length) args(i) match{
      case "--linear" => useLinear = true; i += 1
      case "-n" => n = args(i+1).toInt; i += 2
    }

    val start = nanoTime
    if(useLinear) linearBarrier = new LinearBarrier(n) 
    else logBarrier = new Barrier(n)
    for(r <- 0 until reps){
      run(system); print(".")
    }
    val duration = (nanoTime-start)/1_000_000
    println
    println(s"$duration ms")
  }
}

/* Results on casteret:
 * scala -cp .:/home/gavinl/Scala/SCL tests.BarrierExperiment -n 64
 * 9934 ms
 * scala -cp .:/home/gavinl/Scala/SCL tests.BarrierExperiment -n 64 --linear
 * 96618 ms
 */

// ------------------------------------------------------------------

/** A linear barrier synchronisation.  This is basically Bernard's
  * implementation, which was based on mine, I think. */
class LinearBarrier(n: Int){
  assert(n > 1)
  private [this] var waiting = 0 // number of processes currently waiting
  private [this] val wait    = new SignallingSemaphore
  private [this] val enter   = new MutexSemaphore
  // enter is up iff a new batch of processes can enter the barrier
  // each entering process but the last is stalled by wait

  /** Wait until all `n` processes have called sync */ 
  def sync(): Unit = {
    enter.down
    if (waiting == n-1)    // the last process arrives
      wait.up          // everyone can proceed (but cannot re-enter)
    else{                 // a process arrives that isn't the last
      waiting += 1
      enter.up
      wait.down        // make it wait
      waiting -= 1        
      if (waiting == 0) 
         enter.up       // the last waiting process awoke
      else 
         wait.up        // pass the baton to another waiter
    }
  }
}
    
