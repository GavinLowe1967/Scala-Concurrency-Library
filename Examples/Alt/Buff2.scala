import ox.scl._

/** Various implementations of two-place buffers. */ 
object Buff2{

  /** Two place buffer. */
  def buff2[T](in: ?[T], out: ![T]): Unit = {
    val x = in?()
    buff2full(in, out, x)
  }
  /** Two place buffer holding x. */
  def buff2full[T](in: ?[T], out: ![T], x: T): Unit = {
    alt(
      out =!=> { x } ==> { /*println("sent in alt");*/ buff2(in, out) }
      | in =?=> { y => out!x; /*println("sent in branch");*/ buff2full(in, out, y) }
    )
  }  

  /** Two place buffer, using alternation. */
  def buff2Alt[T](in: ?[T], out: ![T]) = {
    var x: T = null.asInstanceOf[T]  // contents, possibly invalid
    var empty = true // is the buffer empty?
    serve(
      !empty && out =!=> { empty = true; /* println(s"alt sends $x");*/ x }
      | empty && in =?=> { 
        v => x = v; /* println(s"alt receives $x");*/ empty = false 
      }
      | !empty && in =?=> { v => 
        /*println(s"alt receives $v non-empty");*/ out!x; 
        /*println(s"alt sends $x in branch");*/ x = v 
      }
    )
  }

  /** One-place buffer. */
  def buff1[T](in: ?[T], out: ![T]) = {
    var x: T = null.asInstanceOf[T]  // contents, when empty = false
    var empty = true // is the buffer empty?
    serve(
      !empty && out =!=> { empty = true; /* println(s"alt sends $x");*/ x }
      | empty && in =?=> { v => x = v; /* println(s"alt receives $x");*/ empty = false }
    )
  }

  /** The complete system.
    * @param useAlt should the alt-based definition be used? */
  def system(useAlt: Boolean): Computation = {
    // Random delay
    def pause = Thread.sleep(scala.util.Random.nextInt(500))

    // We create a pipeline, consisting of (1) a process producing the natural
    // numbers; (2) a two-place buffer; (3) a process that echos to the
    // console.
    val c1, c2 = new SyncChan[Int]
    val sender = thread{ var n = 0; while (true) { pause; c1!n; n+=1 }}
    val buff = 
      if(useAlt) thread{ buff2Alt(c1, c2) } else thread{ buff2(c1, c2) }
    val receiver = thread{ 
      var expected = 0
      repeat{ 
        val n = c2?(); // println(s"receiver received $n"); 
        assert(n == expected); expected += 1; pause 
      }
    }
    sender || buff || receiver
  }

  def main(args: Array[String]) = {
    val useAlt = args.nonEmpty && args(0) == "--useAlt"
    run(system(useAlt))
  }
}

// ==================================================================

/** Test the implementations of Buff2, using linearizability testing. */
object Buff2Test{
  var iters = 50  // Number of iterations by each worker
  val MaxVal = 20 // Maximum value sent
  //val Size = 2 // maximum capacity

  // Which instance to run?  0 = buff2, 1 = buff2Alt, 2 = buff1
  var instance = 0

  var buffered = false

  /** Build a concurrent object around an element of Buff2. */
  class Buffer {
    val in = if(buffered) new BuffChan[Int](1) else  new SyncChan[Int]  
    val out = if(buffered) new BuffChan[Int](1) else new SyncChan[Int]

    def send(x: Int) = in!x

    def receive: Int = { val x = out?(); /*println(s"Thread receives $x");*/ x }

    thread("Buffer"){ 
      if(instance == 0) attempt{ Buff2.buff2(in,out) }{}
      else if(instance == 1) Buff2.buff2Alt(in, out) 
      else Buff2.buff1(in, out)
    }.fork
    //thread("Buffer"){ Buff2.buff1(in, out) }.fork

    def shutdown = { in.close; out.close }
  }

  type SeqBuff = scala.collection.immutable.Queue[Int]
  type ConcBuff = Buffer

  /* The capacity of the buffer and channels. */
  def capacity = (if(instance == 2) 1 else 2)+(if(buffered) 2 else 0)

  /* Operations on the specification. */
  def seqSend(x: Int)(q: SeqBuff) : (Unit, SeqBuff) = {
    require(q.length < capacity); ((), q.enqueue(x))
  }
  def seqReceive(q: SeqBuff) : (Int, SeqBuff) = {
    require(q.nonEmpty); q.dequeue
  }


  /** A worker for the LinTesters */
  def worker(me: Int, log: LinearizabilityLog[SeqBuff, ConcBuff]) = {
    val random = new scala.util.Random(scala.util.Random.nextInt+me*45207)
    for(i <- 0 until iters){
      if(me%2 == 0){
        val x = i /*random.nextInt(MaxVal)*/; //println(s"Thread $me sending $x")
        log(_.send(x), "send("+x+")", seqSend(x))
      }
      else{
        //println(s"Thread $me trying to receive")
        log(_.receive, "receive", seqReceive)
      }
    }
    //println(s"$me done")
  }

  def main(args: Array[String]) = {
    // parse arguments
    var i = 0; val p = 2      // Number of workers 
    var reps = 10000 // 0000  // Number of repetitions
    while(i < args.length) args(i) match{
      case "--iters" => iters = args(i+1).toInt; i += 2 
      case "--reps" => reps = args(i+1).toInt; i += 2 
      case "--alternative" => instance = 1; i += 1
      case "--onePlace" => instance = 2; i += 1
      case "--buffered" => buffered = true; i += 1
      // case "--size" => size = args(i+1).toInt; i += 2
      case arg => println("Unrecognised argument: "+arg); sys.exit
    }

    for(r <- 0 until reps){
      val concBuff = new Buffer
      val seqBuff = scala.collection.immutable.Queue[Int]()
      val tester = LinearizabilityTester[SeqBuff,ConcBuff](
        seqBuff, concBuff, p, worker _)
      assert(tester() > 0)
      concBuff.shutdown
      // println
      if(r%100 == 0) print(".")
    } // end of for loop
    println
  }

}
