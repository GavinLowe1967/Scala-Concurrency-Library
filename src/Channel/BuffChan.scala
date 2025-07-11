package ox.scl.channel

import java.lang.System.nanoTime

/** Trait for buffered channels. */
trait BuffChanT[A] extends Chan[A]{
  /* This trait defines all the synchronisation/signalling mechanisms for
   * buffered channels.  The implementations define how the stored data is
   * implemented, via the following operations, each of which is called by a
   * thread that holds the lock. */

  /** Is this channel full? */
  protected def isFull: Boolean

  /** Is this channel empty? */
  protected def isEmpty: Boolean

  /** Get and remove the first item in the buffer. */
  protected def get(): A

  /** Add `x` to the buffer. */
  protected def add(x: A): Unit 

  /** Clear the buffer. */
  protected def clear(): Unit

  /* Lock and conditions. */

  protected val lock = new ox.scl.lock.Lock

  /** Condition for signalling to receiver that a value has been deposited. */
  private val dataAvailable = lock.newCondition

  /** Condition for signalling to sender that a space is available. */
  private val spaceAvailable = lock.newCondition

  // ================================= Closing

  protected var isClosedOut = false

  /** Close the channel. */
  def close() = lock.mutex{
    isChanClosed = true; isClosedOut = true
    dataAvailable.signalAll(); spaceAvailable.signalAll()
    // Signal to waiting alt, if any
    informAltInPortClosed() // in InPort
    informAltOutPortClosed() // in OutPort
  }

  /** Close the channel for receiving.  This completely closes the channel. */
  // def closeIn(): Unit = close()

  /** Close the channel for sending.  The values in the buffer can still be
    * consumed, but then the channel closes completely. */
  def endOfStream(): Unit = lock.mutex{
    isClosedOut = true
    if(isEmpty) close()
    else{
      spaceAvailable.signalAll()
      informAltOutPortClosed() // in OutPort
    }
  }

  /** Reopen the channel. */
  def reopen() = lock.mutex{
    require(isClosed, s"reopen called on $this, but it isn't closed."); 
    isChanClosed = false; isClosedOut = false
    clear(); sendingAlt = null; receivingAlt = null
  }

  /** Check the channel is open, throwing a Closed exception if not. */
  @inline private def checkOpen = if(isClosed) throw new Closed

  // ================================= Sending

  /** Is a receive possible in the current state? */
  @inline protected def canReceive = !isEmpty

  /** Send `x`. */
  def !(x: A) = lock.mutex{
    spaceAvailable.await(!isFull || isClosedOut) // wait for space (1)
    if(isClosedOut) throw new Closed
    if(receivingAlt != null) assert(isEmpty)
    // Try passing to alt first (in InPort)
    if(!tryAltReceive(x)) storeValue(x)
    else spaceAvailable.signal()               // signal to next sender at (1/1')
  }

  /** Store x, and signal to a receiver.  Pre: not closed for sending and
    * length < size. */
  @inline private def storeValue(x: A) = {
    add(x)                                      // store x in the buffer
    dataAvailable.signal()                      // signal to receiver at (2)
  }

  /** Try to send the result of `x`, from an alt.  Return true if successful. */
  protected def trySend(x: () => A): Boolean = {
    if(!isFull){ storeValue(x()); true }
    else false
  }

  /** Try to send `x` within `nanos` nanoseconds.  
    * @return boolean indicating whether send successful. */
  def sendWithinNanos(nanos: Long)(x: A): Boolean = lock.mutex{
    val deadline = nanoTime+nanos
    val timeout = !spaceAvailable.awaitNanos(nanos, !isFull || isClosedOut)
                                        // wait for space, for at most nanos (1')
    if(isClosedOut) throw new Closed
    if(receivingAlt != null) assert(isEmpty) 
    if(timeout) false
    else if(!tryAltReceive(x)){ storeValue(x); true }
    else{ spaceAvailable.signal(); true }    // signal to next sender at (1/1')
  }

  // ================================= Receiving

  /** Receive a value from this channel. */
  def ?(u: Unit): A = lock.mutex{
    checkOpen
    if(isEmpty) tryAltSend match{
      case Some(x) => x
      case None => waitToReceive
    }
    else completeReceive 
  }

  /** Wait to receive a value in this channel.  Pre: the channel is open and
    * there is no sending alt waiting. */
  private def waitToReceive: A = {
    dataAvailable.await(!isEmpty || isClosed)   // wait for data (2)
    checkOpen; completeReceive // Remove item, signal and return result
  }

  /** Complete a receive by removing an item and signalling. */
  @inline protected def completeReceive: A = {
    val result = get();         // Get result from buffer
    spaceAvailable.signal()       // signal to sender at (1)
    if(isClosedOut && isEmpty) close()
    result
  }

  /** Try to receive within `nanos` nanoseconds. 
    * @return `Some(x)` if `x` received, otherwise `None`. */
  def receiveWithinNanos(nanos: Long): Option[A] = lock.mutex{
    checkOpen
    val deadline = nanoTime+nanos
    if(isEmpty) tryAltSend match{
      case Some(x) => Some(x)
      case None => waitToReceiveBy(deadline)
    }
    else Some(completeReceive)
  }

  /** Wait to receive a value in this channel until at most time deadline.  Pre:
    * the channel is open and there is no sending alt waiting. */
  private def waitToReceiveBy(deadline: Long): Option[A] = {
    val timeout =                   // wait for data until at most deadline (2')
      !dataAvailable.awaitNanos(deadline-nanoTime, !isEmpty || isClosed)
    checkOpen
    if(timeout) None
    else Some(completeReceive) // Remove item, signal and return result
  }
}

// =======================================================

/** A buffered channel with capacity `size`. */
class BuffChan[A: scala.reflect.ClassTag](size: Int) extends BuffChanT[A]{

  require(size > 0, 
    s"BuffChan created with capacity $size: must be strictly positive.")

  /** Array holding the data in the non-singleton case. */
  private val data = /* if(singleton) null else */ new Array[A](size)

  /** Index of the first piece of data. */
  private var first = 0

  /** Number of pieces of data currently held.  Inv: 0 <= length <= size. */
  private var length = 0

  /** Is this channel full? */
  protected def isFull = length == size

  /** Is this channel empty? */
  protected def isEmpty = length == 0

  /** Get and remove the first item in the buffer. */
  protected def get(): A = {
    require(length > 0); length -= 1
    val r = data(first); first = first+1; if(first == size) first = 0; r
  }

  /** Add `x` to the buffer. */
  protected def add(x: A) = {
    require(length < size)
    var index = first+length; if(index >= size) index -= size
    data(index) = x; length += 1
  }

  /** Clear the buffer. */
  protected def clear(): Unit = {
    length = 0; first = 0
  }
}

// =======================================================

/** A buffered channel of size 1. */
class OnePlaceBuffChan[A] extends BuffChanT[A]{
  /** The contents of the buffer, when `full` is true. */
  private var datum: A = _

  /** Does the buffer contain a value, namely `datum`? */
  private var filled = false

  /* This represents the buffer with contents
   *   if(filled) <datum> else <>. */

  protected def isFull = filled

  protected def isEmpty = !filled

  protected def get() = { require(filled); filled = false; datum }

  protected def add(x: A) = { require(!filled); datum = x; filled = true }

  protected def clear() = filled = false 
}

// =======================================================

/** An unbounded buffered channel. */
class UnboundedBuffChan[A] extends BuffChanT[A]{
  /** We store the contents of the buffer in a linked list constructed from
    * nodes of the following type. */
  private class Node(val datum: A){
    var next: Node = null
  }

  /** A dummy header node. */
  private var header = new Node(null.asInstanceOf[A])

  /** The last node in the list. */
  private var last = header

  /* This represents the buffer with contents <n.datum | n <- L(header.next)>. */

  protected val isFull = false

  protected def isEmpty = header == last

  protected def get() = { header = header.next; header.datum }

  protected def add(x: A) = {
    val n = new Node(x); last.next = n; last = n
  }

  protected def clear() = {
    header = new Node(null.asInstanceOf[A]); last = header
  }
}
