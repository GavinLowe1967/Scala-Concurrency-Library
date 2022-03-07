package ox

package object scl{
  /** Create a thread that will execute `comp`. */
  def thread(comp: => Unit) = new Thread(null, comp)

  /** Create a thread with name `name` that will execute `comp`. */
  def thread(name: String)(comp: => Unit) = new Thread(name, comp)

  /** Run the computation `comp`. */
  def run(comp: Computation) = comp.run

  /** Create a parallel computation of the `Computation`s `comps`. */
  def || (comps: Seq[Computation]): Computation = Computation.||(comps)

  /** Repeatedly perform `body`. */
  @inline def repeat(body: => Unit): Unit = {
    try{ while(true)(body) }
    catch{ case _: Stopped   => {}; case t: Throwable => throw t }
  }

  /** Attempt to perform `body`; if that throws a `Stopped` exception, perform
    * `alternative`. */
  @inline def attempt(body: => Unit)(alternative: => Unit): Unit = {
    try{ body }
    catch{ case _: Stopped   => alternative; case t: Throwable => throw t }
  }

  /* Alternation */
 
  implicit class Guarded(guard: => Boolean){
    def &&[T](port: channel.InPort[T]) =
      new channel.GuardedInPort[T](() => guard, port)
    /*
    def &&[T](port: alternation.channel.OutPort[T]) = 
      new alternation.channel.GuardedOutPort[T](()=>guard, port)
    def &&[T](chan: alternation.channel.Chan[T])    = 
      new alternation.channel.GuardedChan[T](()=>guard, chan) */
  }

  /** Construct an `alt` from `branches`. */
  def alt(branches: channel.AltBranch) = 
    new channel.Alt(branches.unpack.toArray)()

  /* Make various classes available without full qualification. */

  // Locks
  type Lock = lock.Lock
  type MutexSemaphore = lock.MutexSemaphore
  type SignallingSemaphore = lock.SignallingSemaphore

  // Channels
  type Chan[A] = channel.Chan[A]
  type SyncChan[A] = channel.SyncChan[A] 
  type BuffChan[A] = channel.BuffChan[A] 
  type ?[A] = channel.InPort[A]
  type ![A] = channel.OutPort[A]

  // Linearizability testing_
  import ox.cads.testing.LinearizabilityTester.{WorkerType,JITGraph}
  def LinearizabilityTester[S,C](seqObj: S, concObj: C, p: Int, 
      worker: WorkerType[S,C], tsLog: Boolean = true) =
    JITGraph(seqObj, concObj, p, worker, tsLog)
  type LinearizabilityLog[S,C] = ox.cads.testing.GenericThreadLog[S,C]
 
}
