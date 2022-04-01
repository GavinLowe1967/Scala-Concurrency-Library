/** A one-one synchronous channel passing data of type A, implemented using a
  * monitor. */
class SyncChan[A]{
  /** The current or previous value. */
  private var value = null.asInstanceOf[A]

  /** Is the current value of value valid, i.e. ready to be received? */
  private var full = false

  def send(x: A) = synchronized{
    assert(!full) // previous round must be complete
    // Deposit my value, and signal to receiver
    value = x; full = true; notify()
    // Wait for receiver
    while(full) wait()
  }

  def receive: A = synchronized{
    // wait for sender
    while(!full) wait()
    // clear value, and notify sender
    full = false; notify()
    value
  }

}

// ==================================================================

import ox.scl._
import scala.util.Random

object SyncChanTest{
  // We will log using events of the following form
  abstract class LogEvent
  case class BeginSend(x: Int) extends LogEvent
  case object EndSend extends LogEvent
  case object BeginReceive extends LogEvent
  case class EndReceive(x: Int) extends LogEvent

  /** Check the log es is valid. */
  def checkLog(es: Array[LogEvent]) = {
    val n = es.length; val keepEs = es.clone

    // Print the log, highlighting event k, and exit
    def printLog(k: Int) = {
      for(j <- 0 to n) println(s"$j:\t${keepEs(j)}"+(if(j==k) "***" else ""))
      assert(false); sys.exit()
    }

    // We traverse the log, replacing accounted for events by null.
    var i = 0 // index of next event to consider
    // advance to the next non-null event
    def advance = { while(i < n && es(i) == null) i += 1 }

    while(i < n){
      // We expect the first two non-null events to be BeginSend(x) and
      // BeginReceive, in some order
      advance
      if(i < n) es(i) match{
        case BeginSend(x) => 
          i += 1; advance
          es(i) match{ case BeginReceive => findEnds(x) }
        case BeginReceive => 
          i += 1; advance
          es(i) match{ case BeginSend(x) => findEnds(x) }
        case e => println(s"Error: Begin event expected; found $e"); printLog(i)
      }
    }    

    // Find and null-out EndSend and EndReceive(x) events, starting at i+1.
    def findEnds(x: Int) = {
      // One of the End events should appear immediately; the other might
      // begin after some end events.
      i += 1
      es(i) match{
        case EndSend => 
          es(i) = null; i += 1
          // Search for next EndReceive, which should be for x.
          var j = i; var found = false
          while(!found){
            es(j) match{
              case EndReceive(y) =>
                if(y == x){ es(j) = null; found = true }
                else{ println("Error: incorrect value received"); printLog(j) }
              case _ => j += 1
            }
          }
        case EndReceive(`x`) => 
          es(i) = null; i += 1
          // Search for next EndSend
          var j = i
          while(es(j) != EndSend) j += 1
          es(j) = null
        case _ => println("Error: End event expected"); printLog(i)
      }
    }
  }

  /** Do a single test. */
  def doTest = {
    val n = Random.nextInt(30) // # items to send
    val chan = new SyncChan[Int] // channel to test
    val log = new Log[LogEvent](2) // shared log

    // The sender
    val sender = thread{
      for(i <- 0 until n){
        val x = Random.nextInt(100)
        log.add(0, BeginSend(x))
        chan.send(x)
        log.add(0, EndSend)
      }
    }
    // The receiver
    val receiver = thread{
      for(i <- 0 until n){
        log.add(1, BeginReceive)
        val x = chan.receive
        log.add(1, EndReceive(x))
      }
    }

    run(sender || receiver)
    checkLog(log.get)
  }

  def main(args: Array[String]) = {
    for(r <- 0 until 10000){ doTest; if(r%100 == 0) print(".") }
    println
  }


}
