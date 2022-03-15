import ox.scl._

/** A partial queue. */
trait PartialQueue[T]{
  /** Enqueue x. */
  def enqueue(x: T): Unit

  /** Dequeue a value.  Blocks until the queue is non-empty. */
  def dequeue: T

  /** Shut down the queue. */
  def shutdown = {}
}

// -------------------------------------------------------

import scala.collection.mutable.Queue

class ServerPartialQueue[T] extends PartialQueue[T]{
  private val enqueueChan = new SyncChan[T]

  private val dequeueChan = new SyncChan[T]

  private def server = thread("server"){
    val queue = new Queue[T]
    serve(
      enqueueChan =?=> { x => queue.enqueue(x) }
      | queue.nonEmpty && dequeueChan =!=> queue.dequeue
        // Following to try out the linearizability tester, outside the
        // precondition of the sequential specification object.
      // | dequeueChan =!=> {
      //     if(queue.nonEmpty) queue.dequeue else null.asInstanceOf[T]
      // }
    )
  }

  server.fork

  /** Enqueue x. */
  def enqueue(x: T) = enqueueChan!x

  /** Dequeue a value.  Blocks until the queue is non-empty. */
  def dequeue: T = dequeueChan?()

  /** Shut down the queue. */
  override def shutdown = { enqueueChan.close; dequeueChan.close }
}

// -------------------------------------------------------

/** A partial queue implemented as a monitor. */
class MonitorPartialQueue[T] extends PartialQueue[T]{
  /** The queue itself. */
  private val queue = new Queue[T]

  /** Enqueue x. */
  def enqueue(x: T) = synchronized{
    queue.enqueue(x); notify()
  }

  /** Dequeue a value.  Blocks until the queue is non-empty. */
  def dequeue: T = synchronized{
    while(queue.isEmpty) wait() // wait for a signal
    queue.dequeue
  }
}

