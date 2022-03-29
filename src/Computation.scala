package ox.scl

/** A Computation represents a system composed of one of more threads.  Each
  * pair `(name, comp)` in `comps` corresponds to a thread with name `name`
  * (if non-null) that will execute `comp`. */
class Computation(private val comps: List[(String, Unit => Unit)]){
  private val p = comps.length

  /** A class encapsulating a thread with name `name` that performs `comp`.  Any
    * non-stopped Throwable is printed.  Any Stopped exception is stored in
    * `thrown`.  Any other exception is printed and the system halted. */  
  private class ThreadObject(name: String, comp: Unit => Unit){
    var thrown: Stopped = null
    val thread: java.lang.Thread = new java.lang.Thread(new Runnable{ 
      def run = try{ comp(()) } catch{ 
        case th: Stopped => thrown = th // just store
        case th: Throwable => 
          println(s"Thread ${thread.getName} terminated by throwing")
          th.printStackTrace(); sys.exit
      }
    })
    if(name != null) thread.setName(name)
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
    val threads = comps.map { case (name, comp) => new ThreadObject(name, comp) }
    threads.foreach(_.thread.start)
    threads.foreach(_.thread.join)
    // Check if any thread threw a Stopped exception: if so, re-throw it.
    for(thread <- threads) if(thread.thrown != null) throw(thread.thrown)
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
