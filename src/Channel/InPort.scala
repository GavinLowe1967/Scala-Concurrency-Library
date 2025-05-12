package ox.scl.channel

/** The receiving end of a channel. */
trait InPort[A] extends Port{
  /** Receive on the inport. */
  def ?(u: Unit): A

  /** Receive on the inport. */
  def receive(): A = this?()

  /** Try to receive within `millis` milliseconds. 
    * @return `Some(x)` if `x` received, otherwise `None`. */
  def receiveWithin(millis: Long): Option[A] =
    receiveWithinNanos(millis*1000000)

  /** Try to receive within `nanos` nanoseconds. 
    * @return `Some(x)` if `x` received, otherwise `None`. */
  def receiveWithinNanos(nanos: Long): Option[A]

  /** Close the channel for receiving. */
  def close(): Unit

  /** Create a branch of an Alt from this. */
  def =?=> (body: A => Unit) = new UnguardedInPortBranch(this, body)

  /* Implementations must provide the following, which are used with alts,
   * below. */

  /** Is a receive possible in the current state? */
  protected def canReceive: Boolean

  /** Complete a receive.  Pre: the port is not closed and there is a value
    * available. */
  protected def completeReceive: A

  /** Is the channel closed? */
  protected var isChanClosed = false

  /** Is the channel closed? */
  def isClosed = isChanClosed

  /** Check an alt can register here, throwing an exception otherwise. */
  protected def checkCanRegisterIn: Unit

  // /** Lock for controlling synchronisations. */
  // protected val lock: ox.scl.lock.Lock 

  // ================================= Alts

  /* Code related to alts.  These make various assumptions concerning the
   * implementation of the subclasses, namely that they use lock, isClosed,
   * canReceive and completeReceive as expected. */

  /** An Alt that is potentially waiting to receive from this, combined with the
    * index number of the branch in that alt, and the iteration number for the
    * alt.  Note: the iteration number is used only for assertions. */
  protected var receivingAlt: (AltT, Int, Int) = null

  /** Registration from Alt `alt` corresponding to its branch `index` on
    * iteration `iter`. */
  private [channel] 
  def registerIn(alt: AltT, index: Int, iter: Int): RegisterInResult[A] 
  = lock.mutex{
    checkCanRegisterIn // In channel implementation
    if(isClosed) RegisterInClosed
    else if(canReceive){
      val result = completeReceive           // complete the receive
      RegisterInSuccess(result)
    }
    else{ receivingAlt = (alt, index, iter); RegisterInWaiting }
  } 

  /** Deregistration from Alt `alt` corresponding to its branch `index` on
    * iteration `iter`. */
  private [channel] 
  def deregisterIn(alt: AltT, index: Int, iter: Int) = lock.mutex{
    assert(receivingAlt == (alt,index,iter) || receivingAlt == null)
    // Might have receivingAlt = null if this has just closed or if there was
    // an earlier call to tryAltReceive.
    receivingAlt = null
  }

  /** Try to have current registered alt (if any) receive x.  Return true if
    * successful. */
  @inline protected def tryAltReceive(x: A): Boolean = {
    if(receivingAlt != null){
      assert(!canReceive)
      val (alt, index, iter) = receivingAlt; receivingAlt = null
      // See if alt is still willing to receive from this
      alt.maybeReceive(x, index, iter)
      // Either way, it won't be willing to receive subsequently, so we clear
      // receivingAlt above.
    }
    else false
  }

  /** Inform the current alt (if any) that this port has closed. */
  @inline protected def informAltInPortClosed() = {
    require(isClosed)
    if(receivingAlt != null){
      val (alt, index, iter) = receivingAlt
      alt.portClosed(index, iter); receivingAlt = null
    }
  }
}

// ==================================================================

/** The result of a `registerIn` on an InPort[A]. */
private [channel] trait RegisterInResult[+A]

/** The InPort passed `result` to the Alt. */
private [channel] 
case class RegisterInSuccess[A](result: A) extends RegisterInResult[A]

/** The InPort is closed. */
private [channel] 
case object RegisterInClosed extends RegisterInResult[Nothing]

/** The InPort is not currently able to communicate. */
private [channel] 
case object RegisterInWaiting extends RegisterInResult[Nothing]

// ==================================================================

/** A branch in an alt corresponding to an InPort.  This corresponds to the
  * syntax `guard && inPort =?=> body`. */
private [scl] class InPortBranch[A]( 
  val guard: Boolean, val inPort: InPort[A], val body: A => Unit)
    extends AtomicAltBranch{
  /** The value received; filled in by the alt. */
  private[channel] var valueReceived: A = _
}

// ==================================================================

/** A branch in an alt corresponding to an InPort with no guard.  This
  * corresponds to the syntax `inPort =?=> body`. */
private [scl] 
class UnguardedInPortBranch[A](inPort: InPort[A], body: A => Unit) 
    extends InPortBranch(true, inPort, body)
 
