import ox.scl._

/** Trait for a synchronous channel. */
trait SyncChanT[A]{
  /** Send x, synchronously. */
  def send(x: A): Unit

  /** Receive a value. */
  def receive: A
}


/** A shared synchronous channel passing data of type A, implemented using a
  * monitor. */
class SharedSyncChan[A] extends SyncChanT[A]{
  /** The current or previous value. */
  private var value = null.asInstanceOf[A]

  /** Is the current value of value valid, i.e. ready to be received? */
  private var full = false

  /** Monitor for controlling synchronisations. */
  private val lock = new Lock

  /** Condition for signalling to sender that a value has been deposited. */
  private val slotFull = lock.newCondition

  /** Condition for signalling to current receiver that it can continue. */
  private val continue = lock.newCondition

  /** Condition for signalling to the next sender that the previous value has
    * been read. */
  private val slotEmptied = lock.newCondition

  def send(x: A) = lock.mutex{
    slotEmptied.await(!full) // wait for previous value to be consumed
    // Deposit my value, and signal to receiver
    value = x; full = true; slotFull.signal()
    // Wait for receiver
    continue.await()
  }

  def receive: A = lock.mutex{
    // wait for sender
    slotFull.await(full)
    // notify current sender
    continue.signal()
    // clear value, and notify next sender
    full = false; slotEmptied.signal()
    value
  }

}
