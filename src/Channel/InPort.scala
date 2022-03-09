package ox.scl.channel

/** The receiving end of a channel. */
trait InPort[A]{
  /** Receive on the inport. */
  def ?(u: Unit): A

  /** Close the channel for receiving. */
  def closeIn(): Unit

  /** Create a branch of an Alt from this. */
  def =?=> (body: A => Unit) = new UnguardedInPortBranch(this, body)

  /** Registration from Alt `alt` corresponding to its branch `index` on
    * iteration `iter`. */
  def registerIn(alt: AltT, index: Int, iter: Int): RegisterInResult[A]

  /** Deregistration from Alt `alt` corresponding to its branch `index` on
    * iteration `iter`. */
  def deregisterIn(alt: AltT, index: Int, iter: Int): Unit
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
 
