package tests

import ox.scl._
import scala.util.Random


object AltWithinTest{
  /** Number of iterations. */
  var iters = 100

  /** Amount of buffering in channel (0 means SyncChan). */
  var buffering = 0

  /** Number of senders. */
  var numSenders = 1

  /** Number of receivers. */
  var numReceivers = 1

  /** Index of the test to perform. */
  var testNum = 1

  def mkChan: Chan[Int] = 
    if(buffering == 0) new SyncChan[Int] 
    else if(buffering == 1) new OnePlaceBuffChan[Int]
    else if(buffering < 0) new UnboundedBuffChan[Int]
    else new BuffChan[Int](buffering)

  /** Test of receiveWithin. */
  def test1 = {
    val c = mkChan
    val size = iters * numSenders // # values sent
    val received = new Array[Boolean](size) // which values have been received
    def sender(me: Int) = thread{ 
      for(i <- me*iters until (me+1)*iters){ 
        Thread.sleep(Random.nextInt(6)); c!i 
      }
    }
    def receiver = thread{ 
      repeat{ 
        c.receiveWithin(3) match{
          case Some(i) =>
            synchronized{ assert(!received(i)); received(i) = true } 
          case None => print("X")
        }
      }
    }
    val senders = thread{
      run(|| (for(i <- 0 until numSenders) yield sender(i))); c.endOfStream()
    }
    val receivers = || (for(_ <- 0 until numReceivers) yield receiver)
    run(senders || receivers)
    for(i <- 0 until size) assert(received(i))
  }

  /** Test of sendWithin. */
  def test2 = {
    val c = mkChan
    val size = iters * numSenders // # values sent
    val sent = new Array[Boolean](size) // which values have been sent
    val received = new Array[Boolean](size) // which values have been received
    def sender(me: Int) = thread{ 
      for(i <- me*iters until (me+1)*iters) 
        if(c.sendWithin(3)(i)) sent(i) = true else print("X")
    }
    def receiver = thread{ 
      repeat{ 
        Thread.sleep(Random.nextInt(6)); val i = c?(); 
        synchronized{ assert(!received(i)); received(i) = true }
      }
    }
    val senders = thread{
      run(|| (for(i <- 0 until numSenders) yield sender(i))); c.endOfStream()
    }
    val receivers = || (for(_ <- 0 until numReceivers) yield receiver)
    run(senders || receivers)
    for(i <- 0 until size) assert(sent(i) == received(i))
  }

  /** Test of sendWithin and receiveWithin. */
  def test3 = {
    val c = mkChan
    val size = iters * numSenders // # values sent
    val sent = new Array[Boolean](size) // which values have been sent
    val received = new Array[Boolean](size) // which values have been received
    def sender(me: Int) = thread{ 
      for(i <- me*iters until (me+1)*iters){
        Thread.sleep(Random.nextInt(6))
        if(c.sendWithin(2)(i)) sent(i) = true else print("X")
      }
    }
    def receiver = thread{ 
      repeat{ 
        Thread.sleep(Random.nextInt(6))
        c.receiveWithin(2) match{
          case Some(i) =>
            synchronized{ assert(!received(i)); received(i) = true } 
          case None => print("Y")
        }
      }
    }
    val senders = thread{
      run(|| (for(i <- 0 until numSenders) yield sender(i))); c.endOfStream()
    }
    val receivers = || (for(_ <- 0 until numReceivers) yield receiver)
    run(senders || receivers)
    for(i <- 0 until size) assert(sent(i) == received(i))
  }

  // TODO: test with alts.

  /** Test with several senders, received by an alt. */
  def test4 = {
    val cs = Array.fill(numSenders)(mkChan)
    val size = iters * numSenders // # values sent
    val sent = new Array[Boolean](size) // which values have been sent
    val received = new Array[Boolean](size) // which values have been received
    /** Sender on cs(me). */
    def sender(me: Int) = thread{
      val c = cs(me)
      for(i <- me*iters until (me+1)*iters){
        Thread.sleep(Random.nextInt(6))
        if(c.sendWithin(2)(i)) sent(i) = true else print("X")
      }
      c.endOfStream()
    }
    def receiver = thread{
      serve(
        | (for(i <- 0 until numSenders) yield 
          cs(i) =?=> { x => 
            assert(!received(x)); received(x) = true
            Thread.sleep(Random.nextInt(4))
          }
        )
      )
    }
    val senders = || (for(i <- 0 until numSenders) yield sender(i))
    run(senders || receiver)
    for(i <- 0 until size) assert(sent(i) == received(i))
  }

  /** Test with several receivers, with sending by an alt. */
  def test5 = {
    val cs = Array.fill(numReceivers)(mkChan)
    val size = iters * numReceivers // # values sent
    val sent = new Array[Boolean](size) // which values have been sent
    val received = new Array[Boolean](size) // which values have been received
    /** Receiver on cs(me).  Expects to receive a subset of
      * [me*iters..(me+1)*iters). */
    def receiver(me: Int) = thread{
      val c = cs(me)
      repeat{
        c.receiveWithin(2) match{
          case Some(x) =>
            assert(me*iters <= x && x < (me+1)*iters)
            synchronized{ assert(!received(x)); received(x) = true } 
          case None => print("X")
        }
      }
    }
    def sender = thread{
      var j = 0
      serve(
        | (for(i <- 0 until numReceivers) yield
          j < iters && cs(i) =!=> 
            { val x = i*iters+j; sent(x) = true; x } ==>
            { j += 1; Thread.sleep(Random.nextInt(3)) }
        )
      )
      cs.foreach(_.endOfStream())
    }
    val receivers = || (for(i <- 0 until numReceivers) yield receiver(i))
    run(sender || receivers)
    for(i <- 0 until size) assert(sent(i) == received(i))
  }

  /** Test where receivers use timeout for a fixed number of iterations, then
    * close channel.  An alt sends repeatedly. */
  def test6 = {
    require(buffering == 0, "test6 requires synchronous channels")
    // ... the sender could successfully send; but the receiver might close
    // and so not receive.
    val cs = Array.fill(numReceivers)(mkChan)
    val maxSends = iters * numReceivers // max number of sends
    val size = maxSends * numReceivers // range of values sent
    val sent = new Array[Boolean](size) // which values have been sent
    val received = new Array[Boolean](size) // which values have been received
    /* Receiver on cs(me).  Expects to receive a subset of
     * [me*maxSends..(me+1)*maxSends). */
    def receiver(me: Int) = thread{
      val c = cs(me)
      for(j <- 0 until iters){
        c.receiveWithin(2) match{
          case Some(x) =>
            assert(me*maxSends <= x && x < (me+1)*maxSends, 
              s"x = $x, me = $me")
            synchronized{ assert(!received(x)); received(x) = true } 
          case None => print("X")
        }
      }
      c.close()
    }
    /** Sender that repeatedly sends. */
    def sender = thread{
      var j = 0
      serve(
        | (for(i <- 0 until numReceivers) yield
          cs(i) =!=>
            { val x = i*maxSends+j; sent(x) = true; x } ==>
            { j += 1; assert(j <= maxSends); Thread.sleep(Random.nextInt(3)) }
        )
      )
    }
    val receivers = || (for(i <- 0 until numReceivers) yield receiver(i))
    run(sender || receivers)
    for(i <- 0 until size) 
      assert(sent(i) == received(i), s"i = $i; ${sent(i)}, ${received(i)}")
  }

  /** Test where senders use timeout for a fixed number of iterations, then
    * close channel.  An alt receives repeatedly. */
  def test7 = {
    val cs = Array.fill(numSenders)(mkChan)
    val size = iters * numSenders // max number of sends
    val sent = new Array[Boolean](size) // which values have been sent
    val received = new Array[Boolean](size) // which values have been received
    /* Sender on cs(me).  Sends a subset of [me*iters .. (me+1)*iters). */
    def sender(me: Int) = thread{
      val c = cs(me)
      for(x <- me*iters until (me+1)*iters){
        if(c.sendWithin(2)(x)) sent(x) = true
        else print("X")
      }
      c.endOfStream()
    }
    /* Receiver. */
    def receiver = thread{
      serve(
        | (for(i <- 0 until numSenders) yield 
          cs(i) =?=> { x => 
            assert(!received(x)); received(x) = true; 
            Thread.sleep(Random.nextInt(2))
          }
        )
      )
    }
    val senders = || (for(i <- 0 until numSenders) yield sender(i))
    run(senders || receiver)
    for(i <- 0 until size) 
      assert(sent(i) == received(i), s"i = $i; ${sent(i)}, ${received(i)}")
  }

  /** Main function. */
  def main(args: Array[String]) = {
    var reps = 1000
    var i = 0
    while(i < args.length) args(i) match{
      case "--reps" => reps = args(i+1).toInt; i += 2
      case "--iters" => iters = args(i+1).toInt; i += 2
      case "--senders" => numSenders = args(i+1).toInt; i += 2
      case "--receivers" => numReceivers = args(i+1).toInt; i += 2
      case "--buffering" => buffering = args(i+1).toInt; i += 2
      case "--test" => testNum = args(i+1).toInt; i += 2
    }

    for(i <- 0 until reps){ 
      testNum match{
        case 1 => test1; case 2 => test2; case 3 => test3; case 4 => test4
        case 5 => test5; case 6 => test6; case 7 => test7
      }
      if(i%10 == 0) print(".")
    }
    println()

  }



}
