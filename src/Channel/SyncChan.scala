package ox.scl.channel

import ox.scl.lock.Lock

/** A synchronous channel passing data of type `A`. */
class SyncChan[A] extends Chan[A]{
  /** The current or previous value. */
  private var value = null.asInstanceOf[A]

  /** Is the current value of value valid, i.e. ready to be received? */
  private var full = false

  /** Is the channel closed. */
  private var isClosed = false

  /** Number of senders waiting for a later synchronisation. */
  private var newSendersWaiting = 0

  /** Number of receivers waiting. */
  private var receiversWaiting = 0

  /** An Alt that is potentially waiting to receive from this. */
  @volatile private var receivingAlt: (AltT,Int) = null

  /** Monitor for controlling synchronisations. */
  private val lock = new ox.scl.lock.Lock

  /** Condition for signalling to receiver that a value has been deposited. */
  private val slotFull = lock.newCondition

  /** Condition for signalling to current sender that it can continue. */
  private val continue = lock.newCondition

  /** Condition for signalling to the next sender that the previous value has
    * been read. */
  private val slotEmptied = lock.newCondition

  /** Close the channel. */
  def close() = lock.mutex{
    isClosed = true; full = false
    // Signal to waiting threads
    slotEmptied.signalAll(); slotFull.signalAll(); continue.signalAll()
    // Signal to waiting alt, if any
    if(receivingAlt != null) receivingAlt match{
      case (alt, index) => alt.portClosed(index); receivingAlt = null
      case null => {}  // Just deregistered
    }
  }

  /** Close the channel for receiving: this closes the whole channel. */
  def closeIn() = close()

  /** Close the channel for sending: this closes the whole channel. */
  def closeOut() = close()

  /** Reopen the channel.  Precondition: the channel is closed, and no threads
    * are trying to send or receive. */
  def reopen() = lock.mutex{
    require(isClosed && !full); isClosed = false
  }

  /** Check the channel is open, throwing a Closed exception if not. */
  @inline private def checkOpen = if(isClosed) throw new Closed

  /** Send `x` on this channel. */
  def !(x: A) = lock.mutex{
    slotEmptied.await(!full || isClosed)
                             // wait for previous value to be consumed (1)
    checkOpen
    // Try sending to an alt, if possible
    var done = false
    if(receivingAlt != null) receivingAlt match{
      case (alt, index) => 
        // See if alt is still willing to receive from this
        if(alt.maybeReceive(x, index)){ receivingAlt = null; done = true }
      case null => {} // Just deregistered
    }
    if(!done){
      value = x; full = true   // deposit my value
      slotFull.signal()        // signal to receiver at (3)
      continue.await()         // wait for receiver (2)
      checkOpen
    }
  }

  /** Receive a value from this channel. */
  def ?(u: Unit) : A = lock.mutex{
    slotFull.await(full || isClosed)  // wait for sender (3)
    checkOpen
    completeRead             // clear slot and signal
    value
  }

  /** Complete a read by clearing the slot and signalling. */
  @inline private def completeRead = {
    full = false            // clear value
    continue.signal()       // notify current sender at (2)
    slotEmptied.signal()    // notify next sender at (1)
  }

  /** Register that `alt` is trying to receive on this from its branch with
    * index `index`.. */
  def registerIn(alt: AltT, index: Int): RegisterInResult[A] = lock.mutex{
    require(receivingAlt == null)
    if(isClosed) RegisterInClosed
    else if(full){
      completeRead           // clear slot and signal
      RegisterInSuccess(value)
    }
    else{
      receivingAlt = (alt, index)
      RegisterInWaiting 
    }
  } 

  /** Record that `alt` is no longer trying to receive on this. */
  def deregisterIn(alt: AltT, index: Int) = {
    // Note: no locking here; read and write are volatile
    assert(receivingAlt == (alt,index) || receivingAlt == null)
    // Might have receivingAlt = null if this has just closed.
    receivingAlt = null
  }
}

/*

import ox.scl.lock.{MutexSemaphore,SignallingSemaphore}

class SyncChan[A] extends Chan[A]{

  /** The current or previous value. */
  private var value = null.asInstanceOf[A]

  /** Is the current value of value valid, i.e. ready to be received? */
  // private var full = false

  private var receiversWaiting = 0

  private var senderWaiting = false

  private val mutex = new MutexSemaphore

  private val empty = new MutexSemaphore

  private val filled, synched = new SignallingSemaphore

  def closeIn(): Unit = ???

  def closeOut(): Unit = ???

  def close() = {} // FIXME

  def reopen() = {} // FIXME


  def !(x: A) = {
    empty.down // This will be the next sender to send
    mutex.down
    value = x
    if(receiversWaiting > 0){
      filled.up     // signal to receiver at (2)
      empty.up
    }
    else{
      senderWaiting = true
      mutex.up; synched.down // wait for signal (1)
      senderWaiting = false
      mutex.up; empty.up
    }
  }

  def ?(u: Unit) = {
    mutex.down
    if(senderWaiting){
      assert(receiversWaiting == 0)
      val result = value
      synched.up             // signal to sender at (1)
      result
    }
    else{
      receiversWaiting += 1
      mutex.up; filled.down  // wait for signal (2)
      receiversWaiting -= 1
      val result = value
      mutex.up; result
    }
  }

}

// ==================================================================

/** A many-many synchronous channel, implemented using semaphores. */
class SyncChanZ[A] extends Chan[A]{  
  /** The current or previous value. */
  private var value = null.asInstanceOf[A]

  /** A semaphore for signalling to receiver that a value has been deposited. */
  private val slotFull = new SignallingSemaphore

  /** A semaphore for signalling to current sender that it can continue. */
  private val continue = new SignallingSemaphore

  /** A semaphore for signalling to the next sender that the previous value has
    * been read. */
  private val slotEmptied = new MutexSemaphore

  def closeIn(): Unit = {} 

  def closeOut(): Unit = {} // FIXME

  def close(): Unit = {}

  def reopen(): Unit = {} // FIXME

  /* Note: the semaphores together provide mutual exclusion. */

  def !(x: A) = {
    slotEmptied.down        // wait for previous value to be consumed
    value = x               // deposit my value
    slotFull.up             // pass baton to receiver
    continue.down           // wait for receiver
    slotEmptied.up           // pass baton to next sender
  }

  def ?(u: Unit): A = {
    slotFull.down            // wait for sender
    val result = value       // take value
    continue.up              // pass baton to current sender
    result
  }
}

 */
