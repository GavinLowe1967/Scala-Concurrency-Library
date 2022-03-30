package ox.scl

/** A Computation represents a system composed of one of more threads.  Each
  * pair `(name, comp)` in `comps` corresponds to a thread with name `name`
  * (if non-null) that will execute `comp`. */
class Computation(private val comps: List[(String, Unit => Unit)]){
  private val p = comps.length

  /** A class encapsulating a thread with name `name` that performs `comp`.  If
    * an exception is thrown, all peers are interrupted, and the exception is
    * stored in `thrown`.  */  
  private class ThreadObject(name: String, comp: Unit => Unit){
    /** The exception thrown, if any. */
    var thrown: Throwable = null

    private val thisTO = this

    /** The java.lang.Thread to run. */
    val thread: java.lang.Thread = new java.lang.Thread(new Runnable{ 
      def run = try{ comp(()) } catch{ 
        case _: InterruptedException => {} // Interrupted by another thread
        case st: Stopped => thrown = st // just store
        case th: Throwable => 
          for(to <- peers) if(to != thisTO) to.thread.interrupt// interrupt peers
          thrown = th
        // case th: Throwable => 
        //   println(s"Thread ${thread.getName} terminated by throwing")
        //   th.printStackTrace(); sys.exit
      }
    })

    if(name != null) thread.setName(name)

    /** The threads running in the same parallel composition as this. */
    private var peers: List[ThreadObject] = null

    /** Run this as part of the parallel composition with peers. */
    def runWith(peers: List[ThreadObject]) = {
      this.peers = peers; thread.start
    }
  }

  /** Run the threads. 
    * 
    * If any thread throws a non-Stopped exception, that gets printed
    * immediately, and the system halts when the parallel composition
    * terminates.  Otherwise, if any thread throws a Stopped exception, that
    * gets thrown when the parallel composition terminates.  */
  def run = {
    /*
    val threads = comps.map(mkThread)
    threads.foreach(_.start)
    threads.foreach(_.join)
 */
    val threads = comps.map{ case (name, comp) => new ThreadObject(name, comp) }
    // threads.foreach(_.peers = threads)
    // threads.foreach(_.thread.start)
    threads.foreach(_.runWith(threads))
    threads.foreach(_.thread.join)
    // Check if any thread threw an exception: if so, re-throw it,
    // prioritising non-Stopped exceptions.
    var stopped: Stopped = null
    for(thread <- threads) thread.thrown match{
      case st: Stopped => stopped = st
      case null => {}
      case thrown: Throwable => throw thrown
    }
    if(stopped != null) throw stopped
  }

  /** Create a thread with name `name` that performs `comp`, where `threadInfo`
    * = `(name, comp)`.  If the thread throws an exception, this halts the
    * program. */
  protected def mkThread(threadInfo: (String, Unit => Unit))
      : java.lang.Thread = {
    val (name, comp) = threadInfo
    val thread = new java.lang.Thread(new Runnable{ 
      def run = {
        try{ comp(()) } catch {
          case thrown: Throwable =>
            println(s"Thread ${name} terminated by throwing:")
            thrown.printStackTrace(); sys.exit
        }
      }
    })
    if(name != null) thread.setName(name)
    thread
  }

  /** Fork off a machine thread to execute the threads.  If any thread throws an
    * exception, that halts the program. */
  def fork =  comps.foreach(mkThread(_).start)

  /** Create the parallel composition of this with `that`. */
  def || (that: Computation) = new Computation(comps ++ that.comps)
}

// =======================================================

/** Companion object. */
object Computation{
  /** Create a parallel computation of the `Computation`s `comps`. */
  def || (comps: Seq[Computation]): Computation = 
    new Computation(comps.toList.map(_.comps).flatten)
}

// =======================================================

/** A thread with name `name` (if non-null) that will execute `comp`. */
class Thread(name: String, comp: => Unit) 
    extends Computation(List( (name, _ => comp) )){

  // override def fork = mkThread((name, _ => comp)).start

}
