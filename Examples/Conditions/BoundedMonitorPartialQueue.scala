import ox.scl._
import scala.collection.mutable.Queue

/** A partial queue. */
trait PartialQueue[T]{
  /** Enqueue x. */
  def enqueue(x: T): Unit

  /** Dequeue a value.  Blocks until the queue is non-empty. */
  def dequeue: T

  /** Shut down the queue. */
  def shutdown = {}
}

/** A bounded partial queue implemented as a monitor. */
class BoundedMonitorPartialQueue[T](bound: Int) extends PartialQueue[T]{
  /** The queue itself. */
  private val queue = new Queue[T]

  /** A monitor object, to control the synchronisations. */
  private val lock = new Lock

  /** Condition for signalling that the queue is not full. */
  private val notFull = lock.newCondition

  /** Condition for signalling that the queue is not empty. */
  private val notEmpty = lock.newCondition

  /** Enqueue x.  Blocks while the queue is full. */
  def enqueue(x: T) = lock.mutex{
    notFull.await(queue.length < bound) 
    queue.enqueue(x)
    notEmpty.signal()
  }

  /** Dequeue a value.  Blocks until the queue is non-empty. */
  def dequeue: T = lock.mutex{
    notEmpty.await(queue.nonEmpty) // wait for a signal
    val result = queue.dequeue
    notFull.signal()
    result
  }
}
