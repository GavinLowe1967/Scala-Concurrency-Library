package ox.scl.channel

/** The receiving end of a channel. */
trait InPort[A]{
  /** Receive on the inport. */
  def ?(u: Unit): A

  /** Close the channel for receiving. */
  def closeIn(): Unit

  /** Create a branch of an Alt from this. */
  def =?=> (body: A => Unit) = new UnguardedInPortBranch(this, body)

  /** Complete a receive.  Pre: the port is not closed and there is a value
    * available. */
  protected def completeReceive(): A

  /** Is a receive possible in the current state? */
  protected def canReceive: Boolean

  /** Is the channel closed? */
  protected var isClosed = false

  /** Monitor for controlling synchronisations. */
  protected val lock = new ox.scl.lock.Lock

  /* Code related to registering and deregistering of alts.  These make various
   * assumptions concerning the implementation of the subclasses, namely that
   * they use lock, isClosed, canReceive and completeReceive as expected. */

  /** An Alt that is potentially waiting to receive from this, combined with the
    * index number of the branch in that alt, and the iteration number for the
    * alt.  Note: the iteration number is used only for assertions. */
  protected var receivingAlt: (AltT, Int, Int) = null

  /** Registration from Alt `alt` corresponding to its branch `index` on
    * iteration `iter`. */
  private [channel] 
  def registerIn(alt: AltT, index: Int, iter: Int)
      : RegisterInResult[A] = lock.mutex{
    require(receivingAlt == null)
    if(isClosed) RegisterInClosed
    else if(canReceive){
      val result = completeReceive           // clear slot and signal
      RegisterInSuccess(result)
    }
    else{
      receivingAlt = (alt, index, iter)
      RegisterInWaiting 
    }
  } 

  /** Deregistration from Alt `alt` corresponding to its branch `index` on
    * iteration `iter`. */
  private [channel] 
  def deregisterIn(alt: AltT, index: Int, iter: Int) = lock.mutex{
    assert(receivingAlt == (alt,index,iter) || receivingAlt == null)
    // Might have receivingAlt = null if this has just closed or if this made
    // a previous call of maybeReceive on alt.
    receivingAlt = null
  }

  /** Try to have current registered alt (if any) accept x. */
  @inline protected def tryAltCallBack(x: A) = {
    if(receivingAlt != null){
      assert(!canReceive)
      val (alt, index, iter) = receivingAlt; receivingAlt = null
      // See if alt is still willing to receive from this
      alt.maybeReceive(x, index, iter)
      // Either way, it won't be willing to receive subsequently, so we clear
      // receivingAlt above
    }
    else false
  }
}

// ==================================================================

/** The result of a `registerIn` on an InPort[A]. */
trait RegisterInResult[+A]

/** The InPort passed `result` to the Alt. */
case class RegisterInSuccess[A](result: A) extends RegisterInResult[A]

/** The InPort is closed. */
case object RegisterInClosed extends RegisterInResult[Nothing]

/** The InPort is not currently able to communicate. */
case object RegisterInWaiting extends RegisterInResult[Nothing]

// ==================================================================

/** A guarded InPort used in an alt.  This corresponds to the syntax 
  * `guard && inPort`. */
class GuardedInPort[A](guard: () => Boolean, inPort: InPort[A]){
  def =?=> (body: A => Unit) = new InPortBranch(guard, inPort, body)
}

// ==================================================================

/** A branch in an alt corresponding to an InPort.  This corresponds to the
  * syntax `guard && inPort =?=> body`. */
class InPortBranch[A]( 
  val guard: () => Boolean, val inPort: InPort[A], val body: A => Unit)
    extends AtomicAltBranch{
  /** The value received; filled in by the alt. */
  private[channel] var valueReceived: A = _
}


// ==================================================================

/** A branch in an alt corresponding to an InPort with no guard.  This
  * corresponds to the syntax `inPort =?=> body`. */
class UnguardedInPortBranch[A](inPort: InPort[A], body: A => Unit) 
    extends InPortBranch(() => true, inPort, body)
 
