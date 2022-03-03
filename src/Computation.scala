package ox.scl

/** A Computation represents a system composed of one of more threads.  Each
  * pair `(name, comp)` in `comps` corresponds to a thread with name `name`
  * (if non-null) that will execute `comp`. */
class Computation(private val comps: List[(String, Unit => Unit)]){
  private val p = comps.length

  /** Create a thread with name `name` that performs `comp`, where `threadInfo`
    * = `(name, comp)`. */
  private def mkThread(threadInfo: (String, Unit => Unit)): java.lang.Thread = {
    val (name, comp) = threadInfo
    val th = new java.lang.Thread(new Runnable{ def run = comp(()) })
    if(name != null) th.setName(name)
    th
  }

  /** Run the threads. */
  def run = {
    val threads = comps.map(mkThread)
    threads.foreach(_.start)
    threads.foreach(_.join)
  }

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
    extends Computation(List( (name, _ => comp) ))
