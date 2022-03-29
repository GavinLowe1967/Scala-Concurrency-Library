package ox.scl

/** A Computation represents a system composed of one of more threads.  Each
  * pair `(name, comp)` in `comps` corresponds to a thread with name `name`
  * (if non-null) that will execute `comp`. */
class Computation(private val comps: List[(String, Unit => Unit)]){
  private val p = comps.length

/* This is an old version, with a different method of handling Throwables.
  /** A class encapsulating a thread with name `name` that performs `comp`.  Any
    * non-stopped Throwable is printed.  Any Throwable is stored in
    * `thrown`. */  private class ThreadObject(name: String, comp: Unit => Unit){
    var thrown: Throwable = null
    val thread: java.lang.Thread = new java.lang.Thread(new Runnable{ 
      def run = try{ comp(()) } catch{ 
        case th: Stopped => thrown = th // just store
        case th: Throwable => 
          println(s"Thread ${thread.getName} terminated by throwing $th")
          th.printStackTrace()
          thrown = th
      }
    })
    if(name != null) thread.setName(name)
  }
 */

  /** Create a thread with name `name` that performs `comp`, where `threadInfo`
    * = `(name, comp)`. */
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

  /** Run the threads. 
    * 
    * If any thread throws a non-Stopped exception, that gets printed
    * immediately, and the system halts when the parallel composition
    * terminates.  Otherwise, if any thread throws a Stopped exception, that
    * gets thrown when the parallel composition terminates.  */
  def run = {
    val threads = comps.map(mkThread)
    threads.foreach(_.start)
    threads.foreach(_.join)
  }

/*  old version
    val threads = comps.map { case (name, comp) => new ThreadObject(name, comp) }
    threads.foreach(_.thread.start)
    threads.foreach(_.thread.join)
    // Check if any thread threw an exception; if any non-Stopped exception,
    // exit; if any Stopped exception, re-throw it.
    var stopped: Stopped = null
    for(thread <- threads) thread.thrown match{
      case null => {}
      case st: Stopped => stopped = st
      case _ => println("Stopping because of earlier throw."); sys.exit
    }
    if(stopped != null) throw(stopped)
 */

  /** Fork of a machine thread to execute the threads. */
  def fork = comps.foreach(mkThread(_).start)

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
