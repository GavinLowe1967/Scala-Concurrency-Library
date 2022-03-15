import ox.scl._

/** A total queue. */
trait TotalQueue[T]{
  /** Enqueue x. */
  def enqueue(x: T): Unit

  /** Dequeue a value.  Returns None if the queue is empty. */
  def dequeue: Option[T]

  /** Shut down the queue. */
  def shutdown: Unit
}

// -------------------------------------------------------

import scala.collection.mutable.Queue

class ServerTotalQueue[T] extends TotalQueue[T]{
  private val enqueueChan = new SyncChan[T]

  private val dequeueChan = new SyncChan[Option[T]]

  private def server = thread("server"){
    val queue = new Queue[T]
    serve(
      enqueueChan =?=> { x => queue.enqueue(x) }
      | dequeueChan =!=> { if(queue.nonEmpty) Some(queue.dequeue) else None }
    )
  }

  fork(server)

  def enqueue(x: T) = enqueueChan!x

  def dequeue: Option[T] = dequeueChan?()

  def shutdown = { enqueueChan.close; dequeueChan.close }
}

// ==================================================================

class ServerTotalQueue2[T] extends TotalQueue[T]{
  private val enqueueChan = new SyncChan[T]

  private val dequeueChan = new SyncChan[SyncChan[Option[T]]]

  private def server = thread("server"){
    val queue = new Queue[T]
    serve(
      enqueueChan =?=> { x => queue.enqueue(x) }
      | dequeueChan =?=> { c => 
        if(queue.nonEmpty) c!Some(queue.dequeue) else c!None }
    )
  }

  fork(server)

  def enqueue(x: T) = enqueueChan!x

  def dequeue: Option[T] = { 
    val c = new SyncChan[Option[T]]; dequeueChan!c; c?() 
  }

  def shutdown = { enqueueChan.close; dequeueChan.close }
}

// -------------------------------------------------------

import scala.util.Random

object QueueTest1{
  val iters = 20000000
  val MaxVal = 20 // Maximum value placed in the queue
  var enqueueProb = 0.3 // probability of doing an enqueue

  var doDequeues = true

  def worker(me: Int, q: TotalQueue[Int]) = thread{
    for(i <- 0 until iters){
      if(Random.nextFloat <= enqueueProb) q.enqueue(Random.nextInt(MaxVal))
      else q.dequeue
      //println(s"$me: $i")
      if(me == 0 && i%5000 == 0) print(".")
    }
    println(s"$me done")
  }

  def main(args: Array[String]) = {
    if(args.nonEmpty && args(0) == "--enqueues") doDequeues = false
    val q = new ServerTotalQueue[Int]
    run(|| (for(id <- 0 until 4) yield worker(id,q)))
  }

}
