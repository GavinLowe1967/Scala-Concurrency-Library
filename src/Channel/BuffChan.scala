package ox.scl.channel

import java.lang.System.nanoTime

/** A buffered channel with capacity `size`. */
class BuffChan[A: scala.reflect.ClassTag](size: Int) extends Chan[A]{
  require(size > 0, 
    s"BuffChan created with capacity $size: must be strictly positive.")

  /** Array holding the data. */
  private val data = new Array[A](size)

  /** Index of the first piece of data. */
  private var first = 0

  /** Number of pieces of data currently held.  Inv: 0 <= length <= size. */
  private var length = 0

  /* The contents of the buffer is data[first .. first+length) (indices
   * interpreted mod size. */

  protected val lock = new ox.scl.lock.Lock

  /** Condition for signalling to receiver that a value has been deposited. */
  private val dataAvailable = lock.newCondition

  /** Condition for signalling to sender that a space is available. */
  private val spaceAvailable = lock.newCondition

  // ================================= Closing

  protected var isClosedOut = false

  /** Close the channel. */
  def close() = lock.mutex{
    isClosed = true; isClosedOut = true
    dataAvailable.signalAll(); spaceAvailable.signalAll()
    // Signal to waiting alt, if any
    informAltInPortClosed() // in InPort
    informAltOutPortClosed() // in OutPort
  }

  /** Close the channel for receiving.  This completely closes the channel. */
  // def closeIn(): Unit = close()

  /** Close the channel for sending. */
  def closeOut(): Unit = lock.mutex{
    isClosedOut = true
    if(length == 0) close()
    else{
      spaceAvailable.signalAll()
      informAltOutPortClosed() // in OutPort
    }
  }

  /** Reopen the channel. */
  def reopen() = lock.mutex{
    require(isClosed, s"reopen called of $this, but it isn't closed."); 
    isClosed = false; isClosedOut = false
    length = 0; first = 0; sendingAlt = null; receivingAlt = null
  }

  /** Check the channel is open, throwing a Closed exception if not. */
  @inline private def checkOpen = if(isClosed) throw new Closed

  // ================================= Sending

  /** Is a receive possible in the current state? */
  @inline protected def canReceive = length > 0

  /** Send `x`. */
  def !(x: A) = lock.mutex{
    spaceAvailable.await(length < size || isClosedOut) // wait for space (1)
    if(isClosedOut) throw new Closed
    if(receivingAlt != null) assert(length == 0)
    // Try passing to alt first (in InPort)
    if(!tryAltReceive(x)) storeValue(x)
    else spaceAvailable.signal                 // signal to next sender at (1/1')
  }

  /** Store x, and signal to a receiver.  Pre: not closed for sending and
    * length < size. */
  @inline private def storeValue(x: A) = {
    data((first+length)%size) = x; length += 1
    dataAvailable.signal()                      // signal to receiver at (2)
  }

  /** Try to send the result of `x`, from an alt.  Return true if successful. */
  protected def trySend(x: () => A): Boolean = {
    // println("trySend")
    if(length < size){ storeValue(x()); true }
    else false
  }

  /** Try to send `x` within `nanos` nanoseconds.  
    * @returns boolean indicating whether send successful. */
  def sendBeforeNanos(nanos: Long)(x: A): Boolean = lock.mutex{
    val deadline = nanoTime+nanos
    val timeout = !spaceAvailable.awaitNanos(nanos, length < size || isClosedOut)
                                        // wait for space, for at most nanos (1')
    if(isClosedOut) throw new Closed
    if(receivingAlt != null) assert(length == 0)
    if(timeout) false
    else if(!tryAltReceive(x)){ storeValue(x); true }
    else{ spaceAvailable.signal; true }      // signal to next sender at (1/1')
  }

  // ================================= Receiving

  /** Receive a value from this channel. */
  def ?(u: Unit): A = lock.mutex{
    checkOpen
    if(length == 0) tryAltSend match{
      case Some(x) => x
      case None => waitToReceive
    }
    else completeReceive 
  }

  /** Wait to receive a value in this channel.  Pre: the channel is open and
    * there is no sending alt waiting. */
  private def waitToReceive: A = {
    dataAvailable.await(length > 0 || isClosed)   // wait for data (2)
    checkOpen
    completeReceive // Remove item, signal and return result
  }

  /** Complete a receive by removing an item and signalling. */
  @inline protected def completeReceive(): A = {
    val result = data(first); first = (first+1)%size; length -= 1
    spaceAvailable.signal()                      // signal to sender at (1)
    if(isClosedOut && length == 0) close()
    result
  }

  /** Try to receive within `nanos` nanoseconds. 
    * @return `Some(x)` if `x` received, otherwise `None`. */
  def receiveBeforeNanos(nanos: Long): Option[A] = lock.mutex{
    checkOpen
    val deadline = nanoTime+nanos
    if(length == 0) tryAltSend match{
      case Some(x) => Some(x)
      case None => waitToReceiveBy(deadline)
    }
    else Some(completeReceive) // waitToReceive
  }

  /** Wait to receive a value in this channel until at most time deadline.  Pre:
    * the channel is open and there is no sending alt waiting. */
  private def waitToReceiveBy(deadline: Long): Option[A] = {
    val timeout =                   // wait for data until at most deadline (2')
      !dataAvailable.awaitNanos(deadline-nanoTime, length > 0 || isClosed)
    checkOpen
    if(timeout) None
    else Some(completeReceive) // Remove item, signal and return result
  }


  // Following might be too strict

  /** Can an alt register at the InPort? */
  protected def checkCanRegisterIn = {
    require(receivingAlt == null, s"Inport of channel used in two alts.")
    require(sendingAlt == null, s"Both ports of channel used in alts.")
  }

  /** Can an alt register at the OutPort? */
  protected def checkCanRegisterOut = {
    require(receivingAlt == null, s"Both ports of channel used in alts.")
    require(sendingAlt == null, s"Outport of channel used in two alts.")
  }

}
