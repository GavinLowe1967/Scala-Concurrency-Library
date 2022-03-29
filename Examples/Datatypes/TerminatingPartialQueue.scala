import ox.scl._
import scala.collection.mutable.Queue

/** A partial queue that terminates if all worker threads are attempting to
  * dequeue, and the queue is empty.
  * @param numWorkers the number of worker threads. */
class TerminatingPartialQueue[A](numWorkers: Int){
  /** Channel for enqueueing. */
  private val enqueueChan = new SyncChan[A]

  private type ReplyChan = SyncChan[A]

  /** Channel for dequeueing. */
  private val dequeueChan = new SyncChan[ReplyChan]

  /** Channel for shutting down the queue. */
  private val shutdownChan = new SyncChan[Unit]

  /** Enqueue x.
    * @throws StopException if the queue has been shutdown. */
  def enqueue(x: A): Unit = enqueueChan!x

  /** Attempt to dequeue a value.
    * @throws StopException if the queue has been shutdown. */
  def dequeue: A = {
    val reply = new SyncChan[A]
    dequeueChan!reply
    reply?()
  }

  /** Shut down this queue. */
  def shutdown = attempt{ shutdownChan!() }{ }
  // Note: it's possible that the server has already terminated, in which case
  // we catch the StopException.

  /** The server process. */
  private def server = thread("server"){
    // Currently held values
    val queue = new Queue[A]()
    // Queue holding reply channels for current dequeue attempt.
    val waiters = new Queue[ReplyChan]()
    // Inv: queue.isEmpty or waiters.isEmpty
    // Termination: signal to all waiting workers
    def close = {
      for(c <- waiters) c.close
      enqueueChan.close; dequeueChan.close; shutdownChan.close
    }

    serve(
      enqueueChan =?=> { x => 
        if(waiters.nonEmpty){ // pass x directly to a waiting dequeue
          assert(queue.isEmpty); waiters.dequeue!x
        }
        else queue.enqueue(x)
      }
      |  
      dequeueChan =?=> { reply =>
        if(queue.nonEmpty) reply!(queue.dequeue) // service request immediately
        else{
          waiters.enqueue(reply)
          if(waiters.length == numWorkers) close
        }
      }
      |
      shutdownChan =?=> { _ => close }
    )
  }

  server.fork
}
