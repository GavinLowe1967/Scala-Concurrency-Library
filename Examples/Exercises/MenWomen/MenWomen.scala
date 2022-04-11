import ox.scl._

/** The trait for the men-women problem. */
trait MenWomen{
  /** A man tries to find a partner. */
  def manSync(me: String): String

  /** A woman tries to find a partner. */
  def womanSync(me: String): String

  /** Shut down the object. */
  def shutdown: Unit = {}
}

// -------------------------------------------------------

/** A solution using a server. */
class MenWomenServer extends MenWomen{
  private type ReplyChan = Chan[String]

  /** Channels sending proposals from a man, resp., a woman. */
  private val manProp, womanProp = new BuffChan[(String, ReplyChan)](1)

  /** A man tries to find a partner. */
  def manSync(me: String): String = {
    val c = new BuffChan[String](1)
    manProp!(me,c)
    c?()
  }

  /** A woman tries to find a partner. */
  def womanSync(me: String): String = {
    val c = new BuffChan[String](1)
    womanProp!(me,c)
    c?()
  }

  /** The server. */
  private def server = thread{
    repeat{
      // Wait for a man and woman, and pair them off. 
      val (him,hisC) = manProp?(); val (her,herC) = womanProp?()
      hisC!her; herC!him
    }
    manProp.close; womanProp.close
  }

  fork(server)

  /** Shut down this object (so the server thread terminates). */
  override def shutdown = { manProp.close; womanProp.close }
}

// -------------------------------------------------------

/** A solution using a monitor. */
class MenWomenMonitor extends MenWomen{
  /** Optionally the man currently waiting. */
  private var oMan: Option[String] = None

  /** Optionally the woman currently waiting. */
  private var oWoman: Option[String] = None

  /** Monitor to control the synchronization. */
  private val lock = new Lock

  /** Condition on which a man writes before writing to oMan. */
  private val manProceed = lock.newCondition

  /** Condition on which a man writes before writing to oWoman. */
  private val womanProceed = lock.newCondition

  /** Condition on which a man waits for a woman.  A signal indicates oWoman has
    * been written. */
  private val manFinish = lock.newCondition

  /** Condition on which a woman waits for a man.  A signal indicates oMan has
    * been written. */
  private val womanFinish = lock.newCondition

  /** Clear both names, and signal to next man and woman that they can write
    * their names. */
  private def clearNames() = {
    oMan = None; manProceed.signal
    oWoman = None; womanProceed.signal
  }

  /** A man tries to find a partner. */
  def manSync(me: String): String = lock.mutex{
    // Wait to write my name
    manProceed.await(oMan.isEmpty); oMan = Some(me)
    if(oWoman.nonEmpty){
      womanFinish.signal() // signal to her
      oWoman.get // get her name
    }
    else{
      manFinish.await // wait for a signal from a woman
      assert(oWoman.nonEmpty)
      val her = oWoman.get // record her name
      clearNames() // clear names and signal to next man, woman
      her
    }
  }

  /** A woman tries to find a partner. */
  def womanSync(me: String): String = lock.mutex{
    // Wait to write my name
    womanProceed.await(oWoman.isEmpty); oWoman = Some(me)
    if(oMan.nonEmpty){
      manFinish.signal() // signal to him
      oMan.get // get his name
    }
    else{
      womanFinish.await // wait for a signal from a man
      assert(oMan.nonEmpty)
      val him = oMan.get // record his name
      clearNames() // clear names and signal to next man, woman
      him
    }
  }

  /* A typical execution of a couple (ignoring the initial waiting) will be
   * - man writes his name, waits for a signal
   * - woman writes her name, signals to man, finishes
   * - man clears names and signals to next man and woman, finishes.
   */
}

// -------------------------------------------------------

/** Another solution using a monitor. */
class MenWomenMonitor2 extends MenWomen{
  /** Information about who is currently waiting. */
  private abstract class Status

  /** The man "him" is currently waiting. */
  private case class ManWaiting(him: String) extends Status

  /** The woman "her" has just paired with the current man. */
  private case class WomanDone(her: String) extends Status

  /** Nobody is currently waiting. */
  private case object Nobody extends Status

  /** A slot which holds information about the current status of the exchange. */
  private var slot: Status = Nobody

  /** Monitor to control the synchronization. */
  private val lock = new Lock

  /** Condition to signal that the slot has been cleared, ready for the next
    * couple. */
  private val slotEmpty = lock.newCondition

  /** Condition to signal that a man's name is in the slot, and he is waiting
    * for a woman. */
  private val manWaiting = lock.newCondition

  /** Condition to signal that a man has written her name, and is paired with
    * the waiting man. */
  private val womanDone = lock.newCondition

  /* A typical execution of a couple will be:
   * - man waits for slot to become empty, writes his name, signals to woman, 
   *   (on manWaiting), and waits;
   * - woman waits for signal, reads man's name, writes her name, signals to
   *   man (on womanDone), and finishes;
   * - man reads woman's name, clears name, signals to next man (on SlotEmpty), 
   *   and finishes.
   */

  /** A man tries to find a partner. */
  def manSync(me: String): String = lock.mutex{
    // Wait to write my name
    slotEmpty.await(slot == Nobody)          // wait for signal (1)
    slot = ManWaiting(me); manWaiting.signal  // signal to woman waiting at (2)
    womanDone.await                          // wait for woman (3)
    val WomanDone(her) = slot
    slot = Nobody; slotEmpty.signal         // signal to man waiting at (1)
    her
  }

  /** A woman tries to find a partner. */
  def womanSync(me: String): String = lock.mutex{
    manWaiting.await(slot.isInstanceOf[ManWaiting]) // wait for signal (2)
    val ManWaiting(him) = slot
    slot = WomanDone(me); womanDone.signal   // signal to man waiting at (3)
    him
  }
}

// -------------------------------------------------------

/** A solution using semaphores. */
class MenWomenSemaphore extends MenWomen{
  /** Name of the current (or last) woman. */
  private var woman = "Alice"

  /** Name of the current (or last) man. */
  private var man = "Bob"

  /** Semaphore where a man waits initially. */
  private val manWait = new MutexSemaphore

  /** Semaphore where a woman waits until signalled. */
  private val womanWait = new SignallingSemaphore

  /** Semaphore where the current man waits for a woman. */
  private val manWait2 = new SignallingSemaphore

  def manSync(me: String): String = {
    manWait.down // wait my turn (1)
    man = me  // store my name
    womanWait.up // signal to a woman at (2)
    manWait2.down // wait for an acknowledgement (3)
    val her = woman // get her name
    manWait.up // signal to the next man at (1)
    her
  }

  def womanSync(me: String): String = {
    womanWait.down // wait for a man (2)
    woman = me; val him = man // store my name, get his
    manWait2.up // signal to him at (3)
    him
  }
}

// -------------------------------------------------------

import scala.util.Random

object MenWomenTest{
  /* In each test, each man/woman writes the identity of his/her partner into an
   * array.  We then test that the two arrays agree. */

  // Arrays that hold the id of each man/woman's partner
  var partnerOfMan, partnerOfWoman: Array[Int] = null

  /** Process for a man. */
  def man(me: Int, mw: MenWomen) = thread{
    partnerOfMan(me) = mw.manSync(me.toString).toInt
  }

  /** Process for a woman. */
  def woman(me: Int, mw: MenWomen) = thread{
    partnerOfWoman(me) = mw.womanSync(me.toString).toInt
  }

  /** Do a single test.  mtype indicates which solution to use. */
  def doTest(mType: String) = {
    val n = Random.nextInt(10) // # men, women
    partnerOfMan = new Array[Int](n); partnerOfWoman = new Array[Int](n)
    val mw: MenWomen =
      if(mType == "monitor") new MenWomenMonitor
      else if(mType == "monitor2") new MenWomenMonitor2
      else if(mType == "semaphore") new MenWomenSemaphore
      else{ assert(mType == "server");  new MenWomenServer }
    val men = || (for(i <- 0 until n) yield man(i, mw))
    val women = || (for(i <- 0 until n) yield woman(i, mw))
    run(men || women)
    mw.shutdown
    for(m <- 0 until n)
      assert(partnerOfWoman(partnerOfMan(m)) == m,
             partnerOfMan.mkString(", ")+"\n"+
               partnerOfWoman.mkString(", ")+"\n"+m)
  }

  /** Main method. */
  def main(args: Array[String]) = {
    var mType = "server"
    if(args.nonEmpty) args(0) match{
      case "--monitor" => mType = "monitor"
      case "--monitor2" => mType = "monitor2"
      case "--semaphore" => mType = "semaphore"
    }
    for(i <- 0 until 10000){
      doTest(mType); if(i%200 == 0) print(".")
    }
    println
  }

}
