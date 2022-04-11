import ox.scl._

object UnboundedBuff{
  // Unbounded buffer
  def buff[T](in: ?[T], out: ![T]) = thread{
    var queue = new scala.collection.mutable.Queue[T];

    serve(
      in =?=> { x => queue.enqueue(x) }
      | !queue.isEmpty && out =!=> { queue.dequeue }
    )
    in.close; out.endOfStream
  }

  val Limit = 1000

  // Produce stream of nats
  def nats(out: ![Int]) = thread("nats"){
    for(n <- 0 until Limit){ 
      out!n; if(n%100 == 0) print("."); Thread.sleep(4) 
    }
    out.endOfStream
  }

  // Consume and print numbers at random intervals
  def receiver(in: ?[Int]) = thread{
    val random = new scala.util.Random; var expected = 0
    repeat{ 
      Thread.sleep(random.nextInt(10)); val x = in?(); // println("received"+x)
      assert(x == expected); expected += 1
    }
    assert(expected == Limit) 
  }

  val in, out = new SyncChan[Int]
  def System = nats(in) || buff(in, out) || receiver(out)

  def main(args : Array[String]) = run(System)
}
