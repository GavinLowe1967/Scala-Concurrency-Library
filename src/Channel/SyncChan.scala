package ox.scl.channel

import ox.scl.lock.Lock

/** A synchronous channel passing data of type `A`. */
class SyncChan[A] extends Chan[A]{
  /** The current or previous value. */
  private var value = null.asInstanceOf[A]

  /** Is the current value of value valid, i.e. ready to be received? */
  private var full = false

  private var receiversWaiting = 0

  protected val lock = new ox.scl.lock.Lock

  /** Condition for signalling to receiver that a value has been deposited. */
  private val slotFull = lock.newCondition

  /** Condition for signalling to current sender that it can continue. */
  private val continue = lock.newCondition

  /** Condition for signalling to the next sender that the previous value has
    * been read. */
  private val slotEmptied = lock.newCondition

  // ================================= Closing

  /** Close the channel. */
  def close() = lock.mutex{
    isClosed = true
    // Signal to waiting threads
    slotEmptied.signalAll(); slotFull.signalAll(); continue.signalAll()
    // Signal to waiting alt, if any
    informAltInPortClosed()  // in InPort
    informAltOutPortClosed() // in OutPort
  }

  /** Close the channel for receiving: this closes the whole channel. */
  def closeIn() = close()

  /** Close the channel for sending: this closes the whole channel. */
  def closeOut() = close()

  /** Is the channel closed for output? */
  def isClosedOut = isClosed

  /** Reopen the channel.  Precondition: the channel is closed, and no threads
    * are trying to send or receive. */
  def reopen() = lock.mutex{
    require(isClosed); isClosed = false; full = false
    sendingAlt = null; receivingAlt = null
  }

  /** Check the channel is open, throwing a Closed exception if not. */
  @inline private def checkOpen = if(isClosed) throw new Closed

  // ================================= Sending

  /** Is a receive possible in the current state? */
  @inline protected def canReceive = full

  /** Send `x` on this channel. */
  def !(x: A) = lock.mutex{
    slotEmptied.await(!full || isClosed)
                                // wait for previous value to be consumed (1)
    checkOpen
    if(tryAltReceive(x))        // Send to an alt, if possible (in InPort)
      slotEmptied.signal        // signal to another sender at (1)
    else{
      value = x; full = true    // deposit my value
      if(receiversWaiting > 0){
        slotFull.signal()        // signal to receiver at (3)
        receiversWaiting -= 1
      }
// IMPROVE: following only if receiversWaiting = 0
      continue.await()         // wait for receiver (2)
      checkOpen
    }
  }

  /** Try to send the result of `x`, from an alt.  Return true if successful. */
  protected def trySend(x: () => A): Boolean = 
    // IMPROVE alt-to-alt communication ???
    if(receiversWaiting > 0){ 
      value = x(); full = true   // deposit my value
      // println(s"can send $value; receiversWaiting = $receiversWaiting"); 
      slotFull.signal()        // signal to receiver at (3)
      receiversWaiting -= 1
      true
    }
    else false

  // ================================= Receiving

  /** Receive a value from this channel. */
  def ?(u: Unit): A = lock.mutex{
    checkOpen
    tryAltSend match{
      case Some(x:A @unchecked) => x
      case None => waitToReceive
    }
  }

  /** Wait to receive a value in this channel.  Pre: the channel is open and
    * there is no sending alt waiting.*/
  private def waitToReceive: A = {
    if(!full){
      receiversWaiting += 1
      //println(s"waitToReceive; receiversWaiting = $receiversWaiting")
      slotFull.await(full || isClosed)  // wait for sender (3)
      // println(s"waiting done; receiversWaiting = $receiversWaiting")
      checkOpen
    }
    completeReceive             // clear slot, signal and return result
  }

  /** Complete a receive by clearing the slot and signalling. */
  @inline protected def completeReceive(): A = {
    full = false            // clear value
    continue.signal()       // notify current sender at (2)
    slotEmptied.signal()    // notify next sender at (1)
    value
  }

  /** Can an alt register at the InPort? */
  protected def canRegisterIn = receivingAlt == null && sendingAlt == null 

  /** Can an alt register at the OutPort? */
  protected def canRegisterOut = receivingAlt == null && sendingAlt == null 

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
