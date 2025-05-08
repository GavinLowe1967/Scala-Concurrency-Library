package ox

/** The main SCL package object, making most of the library available.
  * 
  * Version 1.2. */
package object scl{
  /* Creating and running ThreadGroups. */

  /** Create a thread that will execute `comp`. */
  @inline def thread(comp: => Unit) = new ThreadGroup(List((null, _ => comp)))

  /** Create a thread with name `name` that will execute `comp`. */
  def thread(name: String)(comp: => Unit) = 
    new ThreadGroup(List((name, _ => comp)))

  /** Run the computation `comp`. */
  def run(comp: ThreadGroup) = comp.run

  /** Fork off the computation `comp`. */
  def fork(comp: ThreadGroup) = comp.fork

  /** Create a parallel computation of the `ThreadGroup`s `comps`. */
  def || (comps: Seq[ThreadGroup]): ThreadGroup = ThreadGroup.||(comps)

  /** Repeatedly perform `body`. */
  @inline def repeat[A](body: => A): Unit = {
    try{ while(true)(body) }
    catch{ case _: Stopped   => {}; case t: Throwable => throw t }
  }

  /** Repeatedly perform `body` while `guard` is true. */
  @inline def repeat[A](guard: => Boolean)(body: => A): Unit = {
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
  implicit class Guarded(guard: Boolean){
    /** Add a guard to an InPort branch. */
    def &&[A](uipb: channel.UnguardedInPortBranch[A]) = 
      new channel.InPortBranch(guard, uipb.inPort, uipb.body)

    /** Add a guard to an OutPort branch. */
    def &&[A](uopb: channel.UnguardedOutPortBranch[A]) =
      new channel.OutPortBranch(guard, uopb.outPort, uopb.value, uopb.cont)
  }

  /** Construct an `alt` from `body`. */
  def alt(body: => channel.AltBranch) = new channel.Alt(body)()
    // new channel.Alt(body.unpack.toArray)()

  /** Construct a `serve` from `body`, which repeats until no branch is
    * feasible. */
  def serve(body: => channel.AltBranch) = new channel.Alt(body).repeat(true)

  /** Construct a `serve` from `body`, which repeats while `guard` is true or
    * until no branch is feasible. */
  def serve(guard: => Boolean)(body: => channel.AltBranch) = 
    new channel.Alt(body).repeat(guard)

  /** Construct the body of an `alt` or `serve` from a sequence of alt
    * branches. */
  def | (bs: Seq[channel.AltBranch]): channel.AltBranch =
    new channel.SeqAltBranch(bs)

  // =======================================================
  /* Make various classes available without full qualification. */

  // Locks, semaphores and barriers

  /** A class of locks. */
  type Lock = lock.Lock
  /** Binary semaphores. */
  type Semaphore = lock.Semaphore
  /** Binary semaphores, initially in the up state, suitable for mutual
    * exclusion. */
  type MutexSemaphore = lock.MutexSemaphore
  /** Binary semaphores, initially in the down position, suitable for
    * signalling. */
  type SignallingSemaphore = lock.SignallingSemaphore
  /** Counting semaphores. */
  type CountingSemaphore = lock.CountingSemaphore
  /** Barrier synchronisation objects. */
  type Barrier = lock.Barrier
  /** Combining barrier synchronisation objects. */
  type CombiningBarrier[A] = lock.CombiningBarrier[A]
  /** Conjunctive combining barrier synchronisation objects. */
  type AndBarrier = lock.AndBarrier
  /** Disjunctive combining barrier synchronisation objects. */
  type OrBarrier = lock.OrBarrier

  // Channels
  /** The supertype of channels. */
  type Chan[A] = channel.Chan[A]
  /** Synchronous channels. */
  type SyncChan[A] = channel.SyncChan[A] 
  /** Buffered channels. */
  type BuffChan[A] = channel.BuffChan[A]

  /** Inports of channels. */
  type InPort[A] = channel.InPort[A]
  /** Inports of channels. */
  type ??[A] = InPort[A]
  /** Inports of channels.  For versions of Scala from 2.13.9, it is necessary
    * to place the ? in backticks. */
  @deprecated("\"?\" type constructor no longer supported: use \"??\"",
    "07-05-2025")
  type `?`[A] = InPort[A]

  /** Outports of channels. */
  type OutPort[A] = channel.OutPort[A]
  /** Outports of channels. */
  type !![A] = OutPort[A]
  /** Outports of channels. */
  @deprecated("\"!\" type constructor no longer supported: use \"!!\"",
    "07-05-2025")
  type ![A] = OutPort[A]

  // Logging
  /** A log, for use on operating systems that support timestamps properly (not
    * Windows). */
  type Log[A] = debug.Log[A]
  /** A log, for use on operating systems that do not support timestamps
    * properly (e.g. Windows). */
  type SharedLog[A] = debug.SharedLog[A]

  // Linearizability testing
  import ox.cads.testing.LinearizabilityTester.{WorkerType,JITGraph}
  /** Produce a linearizability tester. 
    * @tparam S the type of the sequential specification datatype.
    * @tparam C the type of concurrent datatypes.   
    * @param seqObj the sequential specification datatype. 
    * @param concObj the concurrent object. 
    * @param p the number of threads.
    * @param worker a function that produces a worker.
    * @param tsLog should a timestamp-based log be used? */
  def LinearizabilityTester[S,C](seqObj: S, concObj: C, p: Int, 
      worker: WorkerType[S,C], tsLog: Boolean = true) =
    JITGraph(seqObj, concObj, p, worker, tsLog)
  /** Type of logs to be used with linearizability testing. 
    * @tparam S the type of the sequential specification datatype.
    * @tparam C the type of concurrent datatypes.   */
  type LinearizabilityLog[S,C] = ox.cads.testing.GenericThreadLog[S,C]
 
}
