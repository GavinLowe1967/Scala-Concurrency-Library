package ox.scl.debug

/** The trait of the two logs. */
trait LogT[A]{
  /** Add x to the log, by thread me, if (mask & bits) == bits. */
  def add(me: Int, x: A, bits: Int = 0): Unit

  /** Get the log events, in the order they were added. */
  def get: Array[A]

  /** Write the log to a file. */
  def toFile(fname: String = "/tmp/logFile") = {
    val writer = new java.io.PrintWriter(fname)
    val events = get
    for(e <- events) writer.write(s"$e\n")
    writer.flush(); writer.close()
  }

  def writeToFileOnShutdown(fname: String = "/tmp/logFile") = {
    val thread = new Thread(new Runnable{ def run = { toFile(fname) } } )
    java.lang.Runtime.getRuntime().addShutdownHook(thread)
  }
}

// =======================================================

import scala.collection.mutable.ArrayBuffer

/** A log object, to be used by p threads, to store log objects of type A.  
  * 
  * Internally, each thread uses a thread-local log, storing each logged item
  * together with a timestamp.  Thus this class assumes that the operating
  * system provides timestamps that are consistent across cores.  This
  * assumption is believed to be sound on Linux, but *not* on Windows.
  * Windows users should either install Linux, or use the less efficient
  * SharedLog class.
  * 
  * @param p the number of threads using the log.
  * @param mask a bitmask, indicating which logged events to store.
  */
class Log[A: scala.reflect.ClassTag](p: Int, mask: Int = 0xFFFFFFFF) 
    extends LogT[A]{
  Log.checkOS

  /** Thread t stores its date in theLogs(t).  Each item is stored together with
    * a Long, giving the time elapsed since this Log object was created. */
  private val logs = Array.fill(p)(new ArrayBuffer[(Long, A)])

  /** The time this Log object was created. */
  private val startTime = System.nanoTime

  /** Add x to the log, by thread me, if (mask & bits) == bits. */
  def add(me: Int, x: A, bits: Int = 0) =
    if((mask & bits) == bits)
      logs(me) += ((System.nanoTime-startTime, x))

  /** Have sentinels been added to the log yet?  This is necessary to
    * avoid adding them again during the call to toFile. */
  private var sentinelsAdded = false

  /** Get the log events, in the order they were added. */
  def get: Array[A] = {
    // Add sentinels to each thread's log
    val Sentinel = Long.MaxValue
    // Clone logs in case another thread is working on them. 
    val myLogs = Array.tabulate(p)(i => logs(i).clone)
    // Perform sanity check that timestamps are increasing
    for(log <- myLogs; i <- 0 until log.length-1)
      assert(log(i)._1 <= log(i+1)._1,
        "\nError: ill-formed log.\n"+
          "This might be because two threads are using the same log identities.")
    if(true || !sentinelsAdded){
      for(log <- myLogs) log += ((Sentinel, null.asInstanceOf[A]))
      sentinelsAdded = true
    }
    else assert(myLogs.forall(_.last._1 == Sentinel))
    var size = myLogs.map(_.length).sum - p // total # elements, ignoring sentinels
                                     
    val result = new Array[A](size)
    val indices = Array.fill(p)(0)
    // Invariant: result[0..next) is the result of merging
    // { myLogs(i)[0..indices(i)) | i <- [0..p) }.

    for(next <- 0 until size){
      // Find minimum of next values
      val minIx = (0 until p).minBy(i => myLogs(i)(indices(i))._1)
      val (ts, x) = myLogs(minIx)(indices(minIx))
      assert(ts < Sentinel)
      result(next) = x; indices(minIx) += 1
      // Very defensive checks. 
      assert(indices(minIx) < myLogs(minIx).length)
      assert(myLogs(minIx)(indices(minIx)) != null)
    }

    result
  }

}

// =======================================================

object Log{
  /** Has checkOS been called previously? */
  private var givenWarning = false

  /** Give a warning if the operating system is a variant of Windows.  Called
    * when an Log object is created. */
  def checkOS = if(!givenWarning){
    val osName = System.getProperty("os.name").map(_.toLower)
    val pattern = "windows"
    // println(osName)
    var i = 0; var found = false
    while(i+pattern.length <= osName.length && !found){
      // Test if pattern appears in osName starting from index i
      var j = 0
      // Inv: osName[i..i+j) = pattern[0..j)
      while(j < pattern.length && osName(i+j) == pattern(j)) j += 1
      found = j == pattern.length; i += 1
    }
    if(found) println(
      "Warning: You seem to be running a version of Windows.  However,\n"+
        "objects from debug.Log may not work correctly on such operating\n"+
        "systems, because of the timestamping mechanism.  It is recommended\n"+
        "that you use an instance of debug.SharedLog instead.")
    givenWarning = true
  }
}

// =======================================================

/** A log object.
  * 
  * Internally, this log uses a shared buffer.  Thus it is likely to perform
  * poorly.  However, it avoids the reliance on the timestamping mechanism of
  * the Log class.
  * 
  * @param mask a bitmask, indicating which logged events to store. */
class SharedLog[A: scala.reflect.ClassTag](mask: Int = 0xFFFFFFFF) 
    extends LogT[A]{
  /** The buffer storing the logged items. */
  private val buffer = new ArrayBuffer[A]

  /** Add x to the log, by thread me, if (mask & bits) == bits. */
  def add(me: Int, x: A, bits: Int = 0) =
    if((mask & bits) == bits) synchronized{ buffer += x }

  /** Get the log events, in the order they were added.
    * 
    * This call should not be concurrent to any add operation call. */
  def get: Array[A] = buffer.toArray
}
