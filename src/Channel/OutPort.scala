package ox.scl.channel

/** The sending end of a channel. */
trait OutPort[A] extends Port{
  /** Send `x` on the channel. */
  def !(x: A): Unit

  /** Try to send `x` within `millis` milliseconds.  
    * @returns boolean indicating whether send successful. */
  def sendBefore(millis: Long)(x: A): Boolean = 
    sendBeforeNanos(millis*1_000_000)(x)

  /** Try to send `x` within `nanos` nanoseconds.  
    * @returns boolean indicating whether send successful. */
  def sendBeforeNanos(nanos: Long)(x: A): Boolean

  /** Close the channel for sending. */
  def closeOut(): Unit

  /** Create a branch of an Alt from this. */
  def =!=> (value: => A) = new SimpleOutPortBranch(this, () => value)

  /* Implementations must provide the following, which are used with alts,
   * below. */

  /** Is the channel closed for output? */
  protected def isClosedOut: Boolean

  // /** Lock for controlling synchronisations. */
  // protected val lock: ox.scl.lock.Lock

  // ================================= Alts

  /** Try to send `value` on this channel from an alt.  Return true if
    * successful. */
  protected def trySend(value: () => A): Boolean

  /** Check that an alt can register here, throwing an exception otherwise. */
  protected def checkCanRegisterOut: Unit

  /* Code related to alts.  These make various assumptions concerning the
   * implementation of the subclasses, namely that they use lock, isClosedOut,
   * trySend, and canRegisterOut as expected. */

  /** An Alt that is potentially waiting to send on this, combined with the
    * index number of the branch in that alt, the iteration number for the
    * alt, and a computation giving the result.  Note: the iteration number is
    * used only for assertions. */
  protected var sendingAlt: (AltT, Int, Int, ()=>A) = null

  /** Registration from Alt `alt` corresponding to its branch `index` on
    * iteration `iter`. */
  private [channel] 
  def registerOut(alt: AltT, index: Int, iter: Int, value: () => A) 
      : RegisterOutResult = lock.mutex{
    // println(s"registerOut($alt, $index, $iter)")
    checkCanRegisterOut // In channel implementation
    if(isClosedOut) RegisterOutClosed
    else try{
      // Note: the channel might be closed while this thread is waiting in
      // trySend; translate the exception into a RegisterOutClosed
      if(trySend(value)) RegisterOutSuccess
      else{ sendingAlt = (alt, index, iter, value); RegisterOutWaiting }
    } catch{ case e: Closed => RegisterOutClosed }
  }

  /** Deregistration from Alt `alt` corresponding to its branch `index` on
    * iteration `iter`. */
  private [channel] 
  def deregisterOut(alt: AltT, index: Int, iter: Int) = lock.mutex{
    assert(sendingAlt == null || 
      sendingAlt._1 == alt && sendingAlt._2 == index && sendingAlt._3 == iter)
    // Might have sendingAlt = null if this has just closed or there was an
    // earlier call to tryAltSend.
    sendingAlt = null
  }

  /** Try to have current alt (if any) send a value.  This corresponds to the
    * current thread receiving. */
  @inline protected def tryAltSend: Option[A] = {
    if(sendingAlt != null){
      val (alt, index, iter, value) = sendingAlt; sendingAlt = null
      alt.maybeSend(index,iter) 
      // Either way, it won't be willing to send subsequently, so we clear
      // sendingAlt above.
    }
    else None
  }

  /** Inform the current alt (if any) that this port has closed. */
  @inline protected def informAltOutPortClosed() = {
    require(isClosedOut)
    if(sendingAlt != null){
      val (alt, index, iter, _) = sendingAlt
      alt.portClosed(index, iter); sendingAlt = null
    }
  }
}

// ==================================================================

/** The result of a `registerOut` on an OutPort[A]. */
trait RegisterOutResult

/** The OutPort communicated with the Alt. */
case object RegisterOutSuccess extends RegisterOutResult

/** The OutPort is closed. */
case object RegisterOutClosed extends RegisterOutResult

/** The OutPort is not currently able to communicate. */
case object RegisterOutWaiting extends RegisterOutResult

// ==================================================================

/** A guarded OutPort used in an alt.  This corresponds to the syntax 
  * `guard && outPort`.  Deprecated.  */
/*
class GuardedOutPort[A](guard: () => Boolean, outPort: OutPort[A]){
  def =!=> (body: A => Unit) = new OutPortBranch(guard, outPort, body)
}
 */


// ==================================================================

/** A branch in an alt corresponding to an OutPort.  This corresponds to the
  * syntax `guard && outPort =!=> value ==> cont`. */
class OutPortBranch[A]( 
  val guard: () => Boolean, val outPort: OutPort[A], 
  val value: () => A, val cont: () => Unit)
    extends AtomicAltBranch{
}

// ==================================================================

/** A branch in an alt corresponding to an OutPort with no guard.  This
  * corresponds to the syntax `outPort =!=> value ==> cont`. */
class UnguardedOutPortBranch[A](
  outPort: OutPort[A], value: () => A, cont: () => Unit)
    extends OutPortBranch(() => true, outPort, value, cont)
 
// ==================================================================

/** A branch in an alt corresponding to an OutPort with no guard or
  * continuation.  This corresponds to the syntax `outPort =!=> value`. */
class SimpleOutPortBranch[A](outPort: OutPort[A], value: () => A)
    extends UnguardedOutPortBranch[A](outPort, value, () => {}){
  /** Add a continuation.  This corresponds to the syntax 
    * outPort =!=> value ==> cont`. */
  def ==> (cont: => Unit) = 
    new UnguardedOutPortBranch(outPort, value, () => cont)
}
