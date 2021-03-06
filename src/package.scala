package ox

package object scl{
  /* Creating and running Computations. */

  /** Create a thread that will execute `comp`. */
  def thread(comp: => Unit) = new Thread(null, comp)

  /** Create a thread with name `name` that will execute `comp`. */
  def thread(name: String)(comp: => Unit) = new Thread(name, comp)

  /** Run the computation `comp`. */
  def run(comp: Computation) = comp.run

  /** Fork off the computation `comp`. */
  def fork(comp: Computation) = comp.fork

  /** Create a parallel computation of the `Computation`s `comps`. */
  def || (comps: Seq[Computation]): Computation = Computation.||(comps)

  /** Repeatedly perform `body`. */
  @inline def repeat(body: => Unit): Unit = {
    try{ while(true)(body) }
    catch{ case _: Stopped   => {}; case t: Throwable => throw t }
  }

  /** Repeatedly perform `body` while `guard` is true. */
  @inline def repeat(guard: => Boolean)(body: => Unit): Unit = {
    try{ while(guard)(body) }
    catch{ case _: Stopped   => {}; case t: Throwable => throw t }
  }

  /** Attempt to evaluate `body`; if that throws a `Stopped` exception, evaluate
    * `alternative`. */
  @inline def attempt[A](body: => A)(alternative: => A): A = {
    try{ body }
    catch{ case _: Stopped   => alternative; case t: Throwable => throw t }
  }

  // ====================== Alternation ======================
 
  /** Implicit conversion to allow a guard on a branch of an alt. 
    * 
    * Code adapted from Bernard Sufrin's CSO. */ 
  implicit class Guarded(guard: => Boolean){
    /** Add a guard to an InPort branch. */
    def &&[A](uipb: channel.UnguardedInPortBranch[A]) = 
      new channel.InPortBranch(() => guard, uipb.inPort, uipb.body)

    /** Add a guard to an OutPort branch. */
    def &&[A](uopb: channel.UnguardedOutPortBranch[A]) =
      new channel.OutPortBranch(() => guard, uopb.outPort, uopb.value, uopb.cont)
  }

  /** Construct an `alt` from `branches`. */
  def alt(body: channel.AltBranch) = 
    new channel.Alt(body.unpack.toArray)()

  /** Construct a `serve` from `branches`. */
  def serve(body: channel.AltBranch) =
    new channel.Alt(body.unpack.toArray).repeat

  // =======================================================
  /* Make various classes available without full qualification. */

  // Locks, semaphores and barriers
  type Lock = lock.Lock
  type Semaphore = lock.Semaphore
  type MutexSemaphore = lock.MutexSemaphore
  type SignallingSemaphore = lock.SignallingSemaphore
  type CountingSemaphore = lock.CountingSemaphore
  type Barrier = lock.Barrier
  type CombiningBarrier[A] = lock.CombiningBarrier[A]
  type AndBarrier = lock.AndBarrier
  type OrBarrier = lock.OrBarrier

  // Channels
  type Chan[A] = channel.Chan[A]
  type SyncChan[A] = channel.SyncChan[A] 
  type BuffChan[A] = channel.BuffChan[A] 
  type ?[A] = channel.InPort[A]
  type ![A] = channel.OutPort[A]

  type Log[A] = debug.Log[A]

  // Linearizability testing
  import ox.cads.testing.LinearizabilityTester.{WorkerType,JITGraph}
  def LinearizabilityTester[S,C](seqObj: S, concObj: C, p: Int, 
      worker: WorkerType[S,C], tsLog: Boolean = true) =
    JITGraph(seqObj, concObj, p, worker, tsLog)
  type LinearizabilityLog[S,C] = ox.cads.testing.GenericThreadLog[S,C]
 
}
