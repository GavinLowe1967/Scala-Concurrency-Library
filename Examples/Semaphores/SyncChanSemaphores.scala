import ox.scl._

/** Trait for a synchronous channel. */
trait SyncChanT[A]{
  /** Send x, synchronously. */
  def send(x: A): Unit

  /** Receive a value. */
  def receive: A
}


/** A many-many synchronous channel, implemented using semaphores. */
class SyncChanSemaphores[A] extends SyncChanT[A]{  
  /** The current or previous value. */
  private var value = null.asInstanceOf[A]

  /** A semaphore for signalling to receiver that a value has been deposited. */
  private val slotFull = new SignallingSemaphore

  /** A semaphore for signalling to current sender that it can continue. */
  private val continue = new SignallingSemaphore

  /** A semaphore for signalling to the next sender that the previous value has
    * been read. */
  private val slotEmptied = new MutexSemaphore

  /* Note: the semaphores together provide mutual exclusion. */

  def send(x: A) = {
    slotEmptied.down        // wait for previous value to be consumed
    value = x               // deposit my value
    slotFull.up             // pass baton to receiver
    continue.down           // wait for receiver
    slotEmptied.up           // pass baton to next sender
  }

  def receive: A = {
    slotFull.down            // wait for sender
    val result = value       // take value
    continue.up              // pass baton to current sender
    result
  }

/*
  This version is incorrect.  If a send is suspended before attempting
  continue.down, the current receiver can signal on continue and slotEmptied.
  Then the following sender and receiver can run, leading to a second
  continue.up, which gets lost.

  def send(x: A) = {
    slotEmptied.down        // wait for previous value to be consumed
    value = x               // deposit my value
    slotFull.up             // pass baton to receiver
    continue.down           // wait for receiver
  }

  def receive: A = {
    slotFull.down            // wait for sender
    assert(full)
    val result = value       // take value
    continue.up              // pass baton to current sender
    slotEmptied.up           // pass baton to next sender
    result
  }
 */

} 
