package ox.scl.channel

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

  /** Monitor for controlling synchronisations. */
  private val lock = new ox.scl.lock.Lock

  /** Condition for signalling to receiver that a value has been deposited. */
  private val dataAvailable = lock.newCondition

  /** Condition for signalling to sender that a space is available. */
  private val spaceAvailable = lock.newCondition

  /** Is the channel closed. */
  private var isClosed = false

  private var isClosedIn = false

  /** Close the channel for sending. */
  def closeIn(): Unit = lock.mutex{
    isClosedIn = true
    if(length == 0) close()
    else spaceAvailable.signalAll()
  }

  /** Close the channel for receiving.  This completely closes the channel. */
  def closeOut(): Unit = close()

  /** Close the channel. */
  def close() = lock.mutex{
    isClosed = true; isClosedIn = true
    dataAvailable.signalAll(); spaceAvailable.signalAll()
  }

  /** Reopen the channel. */
  def reopen() = lock.mutex{
    require(isClosed); isClosed = false; isClosedIn = false
    length = 0; first = 0
  }

  /** Send `x`. */
  def !(x: A) = lock.mutex{
    spaceAvailable.await(length < size || isClosedIn)
    if(isClosedIn) throw new Closed
    data((first+length)%size) = x; length += 1
    dataAvailable.signal()
  }

  def ?(u: Unit): A = lock.mutex{
    dataAvailable.await(length > 0 || isClosed)
    if(isClosed) throw new Closed
    val result = data(first); first = (first+1)%size; length -= 1
    spaceAvailable.signal()
    if(isClosedIn && length == 0) close()
    result
  }

  def registerIn(alt: AltT, index: Int, iter: Int) = ???
  def deregisterIn(alt: AltT, index: Int, iter: Int) = ???
}
