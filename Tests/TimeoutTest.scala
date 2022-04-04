package tests

import ox.scl._
import scala.util.Random
import java.lang.Thread.sleep

/** Test of Condition.awaitNanos, Chan.sendBefore and Chan.receiveBefore. */
object TimeoutTest{
  var iters = 20
  var reps = 100
  var verbose = false

  /** A test of awaitNanos on a Condition. */
  def awaitNanosTest = {
    val lock = new Lock
    val cond = lock.newCondition
    def waiter = thread{
      for(i <- 0 until iters) lock.mutex{
        println(i); println(cond.awaitNanos(500_000_000L))
      }
    }
    def signaller = thread{
      for(i <- 0 until iters/2){
        sleep(Random.nextLong(1_000)) // in millis
        println("signal"); lock.mutex{ cond.signal() }
      }
    }
    run(waiter || signaller)
  }

  // =====================================
  // Components for channel timeouts

  val Delay = 3 // in millis

  var buffered = false // do we use buffered channels?

  def mkChan[A: scala.reflect.ClassTag] = 
    if(buffered) new BuffChan[A](1) else new SyncChan[A]

  /** A sender that tries to send [0..iters) on c, with a timeout of Delay ms,
    * and records results in sent. */
  def senderTimeout(c: Chan[Int], sent: Array[Boolean]) = thread{
    for(i <- 0 until iters){
      val success = c.sendBefore(Delay)(i); sent(i) = success
      if(verbose) 
        if(success) println(s"sent $i") else println(s"failed to send $i")
    }
    c.endOfStream
  }

  /** A thread that sends [0..max) on c. */
  def senderSeq(c: Chan[Int], max: Int, maxDelay: Int = 1) = thread{
    for(i <- 0 until max){ c!i; sleep(Random.nextInt(maxDelay)) }
    c.endOfStream
  }

  /** A thread that tries to receive at intervals of Delay ms, and expects to
    * receive [0..iters). */
  def receiverTimeout(c: Chan[Int]) = thread{
    var expected = 0; var steps = 0
    repeat{
      // sleep(Delay)
      c.receiveBefore(Delay) match{
        case Some(x) =>
          assert(x == expected); expected += 1; 
          if(verbose) println(s"received $x")
        case None => if(verbose) println("failed to receive")
      }
      steps += 1
    }
    assert(expected == iters)
    if(verbose) println(s"receiverTimeout took $steps steps") 
  }

  /** A thread that repeatedly receives on c, with delays of at most maxDelay,
    * and writes the values received in received. */
  def receiver(c: Chan[Int], received: Array[Boolean], 
    maxDelay: Int = 3*Delay)
  = thread{
      repeat{
        sleep(Random.nextLong(maxDelay)); val x = c?(); received(x) = true
      }
    }

  /** A receiver that expects to receive [0..max) on c. */
  def receiverSeq(c: Chan[Int], max: Int, maxDelay: Int = 1) = thread{
    var expected = 0
    repeat{
      val x = c?(); assert(x == expected); expected += 1
      sleep(Random.nextInt(maxDelay))
      //println(s"received $x")
    }
    assert(expected == max)
  }

  // =======================================================
  // Tests of sendBefore

  /** A test of the sendBefore function, with a single sender and single
    * receiver. */
  def sendBeforeTest = {
    val c = mkChan[Int]
    val sent, received = new Array[Boolean](iters)
    run(senderTimeout(c, sent) || receiver(c, received))
    assert(sent.sameElements(received), 
      "sent = "+sent.mkString(", ")+"\nreceived = "+received.mkString(", "))
    if(verbose) println(sent.count(_ == true)) // Around half of the total
  }


  /** A test of the sendBefore function into an alt. */
  def sendBeforeAltTest = {
    val c1, c2, c3 = mkChan[Int]
    val sent, received = new Array[Boolean](iters)
    val iters1 = iters/2 // # iters by otherSender
    def altThread = thread{
      def pause = sleep(Random.nextLong(3*Delay/2))
      var nextFrom2 = 0; var toSend = 0
      serve(
        c1 =?=> { x => 
          //println(s"received $x on c1"); 
          received(x) = true; pause
        }
        | c2 =?=> { x => 
          //println(s"received $x on c2"); 
          assert(x == nextFrom2); nextFrom2 += 1; pause
        }
        | toSend < iters1 && c3 =!=> { toSend } ==> { toSend += 1 }
      )
      c3.endOfStream
      assert(nextFrom2 == iters1 && toSend == iters1)
    }
    run(senderTimeout(c1, sent) || senderSeq(c2,iters1) || 
      altThread || receiverSeq(c3,iters1))
    assert(sent.sameElements(received), 
      "sent = "+sent.mkString(", ")+"\nreceived = "+received.mkString(", "))
    // println(sent.count(_ == true)) // Around half of the total
  }

  /** A test where a sendBefore is on a shared port. */
  def sharedSendBeforeTest = {
    val c = mkChan[Int]
    val sent = new Array[Boolean](iters)
    val received = new Array[Boolean](iters)
    val iters1 = iters/2 // # iters by otherSender
    // Thread that sends [-1..-iters1) on c
    def otherSender = thread{
      attempt{
        for(i <- 1 until iters1){ 
          c!(-i); sleep(Random.nextLong(Delay))
        }
      }{}
    }
    def receiver = thread{
      var expectedFromOther = -1
      repeat{
        val x = c?()
        if(x < 0){ assert(x == expectedFromOther); expectedFromOther -= 1 }
        else received(x) = true
        sleep(Random.nextLong(2*Delay))
      }
      //println(expectedFromOther)
    }
    run(senderTimeout(c, sent) || otherSender || receiver)
    assert(sent.sameElements(received), 
      "sent = "+sent.mkString(", ")+"\nreceived = "+received.mkString(", "))
    //println(sent.count(_ == true)) // Around half of the total
  }

  // =======================================================
  // Tests of receiveBefore

  /** A basic test of receiveBefore. */
  def receiveBeforeTest = {
    val c = new SyncChan[Int]
    run(senderSeq(c,iters,2*Delay) || receiverTimeout(c))
  }

  /** A test of receiveBefore in an alt. */
  def receiveBeforeAltTest = {
    val c1, c2, c3 = new SyncChan[Int]
    def altThread = thread{
      def pause = sleep(Random.nextLong(Delay))
      var nextFrom2 = 0; var toSend = 0; var toSend1 = 0
      serve(
        toSend1 < iters && c1 =!=> toSend1 ==> { 
          if(verbose) println("alt sent on c1"); toSend1 += 1; pause }
        |
        c2 =?=> { x =>
          if(verbose) println(s"alt received $x on c2"); 
          assert(x == nextFrom2); nextFrom2 += 1; pause
        }
        | 
        toSend < iters && c3 =!=> toSend ==> { toSend += 1; pause }
      )
      c1.close; c3.close
      assert(nextFrom2 == iters && toSend == iters)
    }
    run(altThread || receiverSeq(c1, iters, 2*Delay) || 
      senderSeq(c2, iters, 2*Delay) || receiverTimeout(c3) )
  }

  /** A test where receiveBefore is on a shared port. */
  def sharedReceiveBeforeTest = {
    val c = new SyncChan[Int]
    val received = new Array[Boolean](iters)
    def receiveTimeout(me: Int) = thread{
      repeat{
        c.receiveBefore(Delay) match{
          case Some(x) => 
            if(verbose) println(s"$me received $x")
            synchronized{ assert(!received(x)); received(x) = true }
          case None => if(verbose) println(s"$me failed to receive")
        }
      }
    }
    run(senderSeq(c, iters, 2*Delay) || receiveTimeout(1) || receiveTimeout(2))
    assert(received.forall(_ == true))
  }

  // =======================================================

  def main(args: Array[String]) = {
    // Parse arguments
    var test = ""; var i = 0
    while(i < args.length) args(i) match{
      case "--awaitNanos" => test = "awaitNanos"; i += 1

      case "--sendBefore" => test = "sendBefore"; i += 1
      case "--sharedSendBefore" => test = "sharedSendBefore"; i += 1
      case "--sendBeforeAlt" => test = "sendBeforeAlt"; i += 1

      case "--receiveBefore" => test = "receiveBefore"; i += 1
      case "--sharedReceiveBefore" => test = "sharedReceiveBefore"; i += 1
      case "--receiveBeforeAlt" => test = "receiveBeforeAlt"; i += 1

      case "--buffered" => buffered = true; i += 1
      case "--iters" => iters = args(i+1).toInt; i += 2
      case "--reps" => reps = args(i+1).toInt; i += 2
      case "--verbose" => verbose = true; i += 1
      case arg => println(s"Argument not recognised: $arg"); sys.exit
    }

    test match{
      case "awaitNanos" =>  awaitNanosTest

      case "sendBefore" => 
        for(i <- 0 until reps){ sendBeforeTest; print(".") }
        println
      case "sharedSendBefore" => 
        for(i <- 0 until reps){ sharedSendBeforeTest; print(".") }
        println
      case "sendBeforeAlt" => 
        for(i <- 0 until reps){ sendBeforeAltTest; print(".") }
        println

      case "receiveBefore" => 
        for(i <- 0 until reps){ receiveBeforeTest; print(".") }
        println
      case "sharedReceiveBefore" => 
        for(i <- 0 until reps){ sharedReceiveBeforeTest; print(".") }
        println
      case "receiveBeforeAlt" => 
        for(i <- 0 until reps){ receiveBeforeAltTest; print(".") }
        println
      case "" => println("No test specified")
    }
  }

}
