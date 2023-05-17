package ox.scl.channel

import ox.scl.lock.Lock
import java.lang.System.nanoTime

/** A synchronous channel passing data of type `A`. */
class NewSyncChan[A] extends Chan[A]{
  import NewSyncChan._

  /** The current or previous value. */
  private var value = null.asInstanceOf[A]

  /** The current status of an exchange. */
  private var status = Empty

  protected val lock = new ox.scl.lock.Lock

  /** Condition for signalling to receiver that a value has been deposited (so
    * status = Filled), or the channel has been closed. */
  private val slotFull = lock.newCondition

  /** Condition for signalling to current sender that it can continue (so status
    * = Read), or the channel has been closed. */
  private val continue = lock.newCondition

  /** Condition for signalling to the next sender that the previous exchange has
    * completed (so status = Empty), or the channel has been closed. */
  private val slotEmptied = lock.newCondition

  /** Condition for signalling to a waiting alt that the previous exchange has
    * completed (so status = Empty), or the channel has been closed. */
  private val altSlotEmptied = lock.newCondition

  /** How many receivers are waiting on slotFull (including those that have
    * received a signal but not yet obtained the lock).  Each receiver is
    * responsible for incrementing and decrementing receiversWaiting. */
  private var receiversWaiting = 0

  /** Is there an alt waiting on altSlotEmptied? */
  private var altWaiting = false

  /** Signal that the slot has been emptied. */
  @inline private def signalSlotEmptied() = 
    if(altWaiting) altSlotEmptied.signal() else slotEmptied.signal()

  /* Note that we prioritise signalling to an alt over signalling to a normal
   * thread.  Otherwise, if both an alt and a normal thread are waiting, the
   * normal thread could receive the signal and subsequently wait on continue.
   * This can lead to a deadlock.  */

  // ================================= Closing

  /** Close the channel. */
  def close = lock.mutex{
    if(!isChanClosed){
      isChanClosed = true
      // Signal to waiting threads
      slotEmptied.signalAll(); altSlotEmptied.signal()
      slotFull.signalAll(); continue.signalAll()
      // Signal to waiting alt, if any
      informAltInPortClosed()  // in InPort
      informAltOutPortClosed() // in OutPort
    }
  }

  /** Close the channel for sending: this closes the whole channel. */
  def endOfStream = close

  /** Is the channel closed for output? */
  def isClosedOut = isClosed

  /** Reopen the channel.  Precondition: the channel is closed, and no threads
    * are trying to send or receive. */
  def reopen = lock.mutex{
    require(isClosed, s"reopen called of $this, but it isn't closed.") 
    require(receiversWaiting == 0 && !altWaiting, 
      s"reopen called of $this, but a thread is still waiting in it.")
    assert(sendingAlt == null && receivingAlt == null)
    isChanClosed = false; status = Empty
  }

  /** Check the channel is open, throwing a Closed exception if not. */
  @inline private def checkOpen = if(isClosed) throw new Closed

  // ================================= Sending

  /** Is a receive possible in the current state? */
  @inline protected def canReceive = status == Filled

  /** Send `x` on this channel. */
  def !(x: A) = lock.mutex{
    slotEmptied.await(status == Empty || isClosed) 
                                // wait for previous value to be consumed (1)
    checkOpen
    if(tryAltReceive(x))        // Send to an alt, if possible (in InPort)
      signalSlotEmptied()       // signal to another sender at (1)/(1')/(1'')
    else{
      value = x; status = Filled    // deposit my value
      completeSend
    }
  }

  /** Complete the send by maybe signalling to a waiting receiver, and then
    * waiting for a signal back. */
  @inline private def completeSend = {
    maybeSignalToReceiver
    // Note: following is necessary even if receiversWaiting > 0; otherwise
    // this value might be taken by a new thread that starts after this
    // finishes (or after the alt has done something else).
    continue.await()         // wait for receiver (2)
    if(status == Read){ 
      status = Empty
      signalSlotEmptied()  // signal to another sender at (1)/(1')/(1'')
    }
    else{ 
      assert(isClosed)
      if(receiversWaiting == 0) throw new Closed 
      // Otherwise the channel is closed, but this thread got to run before a
      // waiting receiver.  The first receiver to run will successfully
      // consume the value this thread deposited.
    }
  }

  /** Signal to a waiting receiver if there is one. */
  @inline private def maybeSignalToReceiver = {
    if(receiversWaiting > 0) slotFull.signal()    // signal to receiver at (3)
  }

  /** Try to send the result of `x`, from an alt.  Return true if successful.
    * Called by OutPort.registerOut.  Pre: thread holds lock. */
  protected def trySend(x: () => A): Boolean = {
    assert(!altWaiting); altWaiting = true
    // If there are receivers waiting, Wait for slot to be emptied (1')
    altSlotEmptied.await(status == Empty || receiversWaiting == 0 || isClosed)
    altWaiting = false
    if(receiversWaiting == 0){
      checkOpen // If closed, exception gets caught in OutPort.registerOut
      if(status == Empty) slotEmptied.signal() // another thread might be waiting
      false
    }
    else if(status == Empty){ // && receiversWaiting > 0
      value = x(); status = Filled   // deposit my value
      completeSend
      true
    }
    else throw new Closed // channel closed
  }

  /** Try to send `x` within `nanos` nanoseconds.  
    * @return boolean indicating whether send successful. */
  def sendWithinNanos(nanos: Long)(x: A): Boolean = lock.mutex{
    val deadline = nanoTime+nanos
    // Wait until !full || isClosed, but for at most nanos ns.
    val timeout = !slotEmptied.awaitNanos(nanos, status == Empty || isClosed)
    checkOpen
    if(timeout) false
    else if(tryAltReceive(x)){      // Send to an alt, if possible (in InPort)
      signalSlotEmptied()           // signal to another sender at (1)/(1')/(1'')
      true                          // Success
    }
    else{
      // assert(status == Empty)
      value = x; status = Filled    // deposit my vatlue
      maybeSignalToReceiver
      val success = continue.awaitNanos(deadline-nanoTime)
                                             // wait for receiver (2'')
      if(status == Read){
        assert(success); status = Empty; signalSlotEmptied(); true
      }
      else{
        assert(status == Filled && value == x)
        if(isClosed){ if(receiversWaiting == 0) throw new Closed else true }
        else{ assert(!success); status = Empty; signalSlotEmptied(); false }

// Old version
//if(isClosed && receiversWaiting == 0) throw new Closed
// status = Empty 
//         checkOpen
//         assert(!success); // status = Empty; 
//         signalSlotEmptied(); false
//}
      }
    }
  }

  // ================================= Receiving

  /** Receive a value from this channel. */
  def ?(u: Unit): A = lock.mutex{
    checkOpen
    var result = tryAltSend    // Try to receive from an alt first
    while(result.isEmpty && status != Filled){
      // Have to wait
      receiversWaiting += 1
      slotFull.await()
      receiversWaiting -= 1
      // Prioritise completing the receive over checking the channel is closed
      if(status != Filled){
        result = tryAltSend
        if(result == None)  // Another thread took value or channel closed
          checkOpen
      }
    }
    result match{ case Some(x) => x; case None => completeReceive }
  }  

  /** Complete a receive by clearing the slot and signalling. */
  @inline protected def completeReceive: A = {
    // require(status == Filled); 
    status = Read       // clear value
    continue.signal()   // notify current sender at (2) or (2')
    value
  }

  /** Try to receive within `nanos` nanoseconds. 
    * @return `Some(x)` if `x` received, otherwise `None`. */
  def receiveWithinNanos(nanos: Long): Option[A] = lock.mutex{
    checkOpen
    val deadline = nanoTime+nanos; var timeout = false
    var result = tryAltSend    // Try to receive from an alt first
    if(result.isEmpty && status != Filled){
      // have to wait
      receiversWaiting += 1
      do{
        timeout = !slotFull.awaitNanos(deadline-nanoTime)
        if(!timeout && status != Filled){ 
          result = tryAltSend
          if(result == None && isClosed){ 
            receiversWaiting -= 1; throw new Closed
          }
        }
      } while(result.isEmpty && status != Filled && !timeout);
      receiversWaiting -= 1 
    }
    // Note: in first two cases, complete receive even if channel closed
    if(result.nonEmpty){ assert(!timeout); result }
    else if(status == Filled) Some(completeReceive)
    else{
      assert(timeout && nanoTime-deadline >= 0)
      checkOpen // choose to throw exception is closed
      None
    }
  }
}

// =======================================================

/** Companion object. */
object NewSyncChan{
  /* Values for the status variable. */
  private val Empty = 0 // the slot does not hold a valid value
  private val Filled = 1 // the slot has been filled
  private val Read = 2 // the slot has been read by a receiver
}
