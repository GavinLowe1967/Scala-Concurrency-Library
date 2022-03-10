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

  @inline protected def canReceive = length > 0

  /* The contents of the buffer is data[first .. first+length) (indices
   * interpreted mod size. */

  /** An Alt that is potentially waiting to receive from this, combined with the
    * index number of the branch in that alt, and the iteration number for the
    * alt.  Note: the iteration number is used only for assertions. */
  //private var receivingAlt: (AltT, Int, Int) = null

  /** Monitor for controlling synchronisations. */
  //private val lock = new ox.scl.lock.Lock

  /** Condition for signalling to receiver that a value has been deposited. */
  private val dataAvailable = lock.newCondition

  /** Condition for signalling to sender that a space is available. */
  private val spaceAvailable = lock.newCondition

  /** Is the channel closed. */
  //private var isClosed = false

  private var isClosedOut = false

  /** Close the channel for sending. */
  def closeOut(): Unit = lock.mutex{
    isClosedOut = true
    if(length == 0) close()
    else spaceAvailable.signalAll()
  }

  /** Close the channel for receiving.  This completely closes the channel. */
  def closeIn(): Unit = close()

  /** Close the channel. */
  def close() = lock.mutex{
    isClosed = true; isClosedOut = true
    dataAvailable.signalAll(); spaceAvailable.signalAll()
    // Signal to waiting alt, if any
    if(receivingAlt != null){
      val (alt, index, iter) = receivingAlt
      alt.portClosed(index, iter); receivingAlt = null
    }
  }

  /** Reopen the channel. */
  def reopen() = lock.mutex{
    require(isClosed); isClosed = false; isClosedOut = false
    length = 0; first = 0
  }

  /** Send `x`. */
  def !(x: A) = lock.mutex{
    spaceAvailable.await(length < size || isClosedOut)
    if(isClosedOut) throw new Closed
    // var done = false
    // if(receivingAlt != null){
    //   assert(length == 0, length)
    //   val (alt, index, iter) = receivingAlt
    //   // See if alt is still willing to receive from this
    //   if(alt.maybeReceive(x, index, iter)) done = true 
    //   // Either way, it won't be willing to receive subsequently
    //   receivingAlt = null
    // }
    if(!tryAltCallBack(x)){
      data((first+length)%size) = x; length += 1
      dataAvailable.signal()
    }
  }

  /** Receive a value from this channel. */
  def ?(u: Unit): A = lock.mutex{
    dataAvailable.await(length > 0 || isClosed)
    if(isClosed) throw new Closed
    completeReceive
    // val result = data(first); first = (first+1)%size; length -= 1
    // spaceAvailable.signal()
    // if(isClosedOut && length == 0) close()
    // result
  }

  /** Complete a receive by removing an item and signalling. */
  @inline protected def completeReceive(): A = {
    val result = data(first); first = (first+1)%size; length -= 1
    spaceAvailable.signal()
    if(isClosedOut && length == 0) close()
    result
  }

// IMPROVE: should following be in InPort?
  /** Register that `alt` is trying to receive on this from its branch with
    * index `index` on iteration `iter`. */
  // private [channel] def registerIn(alt: AltT, index: Int, iter: Int)
  //     : RegisterInResult[A] = lock.mutex{
  //   require(receivingAlt == null)
  //   if(isClosed) RegisterInClosed
  //   else if(length > 0){
  //     val result = completeReceive()          // remove item and signal
  //     RegisterInSuccess(result)
  //   }
  //   else{
  //     receivingAlt = (alt, index, iter)
  //     RegisterInWaiting 
  //   }
  // } 

  /** Record that `alt` is no longer trying to receive on this. */
  // private [channel] 
  // def deregisterIn(alt: AltT, index: Int, iter: Int) = lock.mutex{
  //   assert(receivingAlt == (alt,index,iter) || receivingAlt == null)
  //   // Might have receivingAlt = null if this has just closed.
  //   receivingAlt = null
  // }

}
