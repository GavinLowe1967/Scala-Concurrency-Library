package ox.scl.channel

/* This file contains various traits and classes built upon in channel and alt
 * implementations. */

/** The base trait of a port. */
trait Port{
  /** Lock for controlling synchronisations. */
  protected val lock: ox.scl.lock.Lock 
}

// ==================================================================

/** Trait corresponding to the interface an alt presents to its branches and
  * ports. */
trait AltT{
  /** Potentially receive `value` from the InPort with index `i` on iteration
    * `iter`. */
  private[channel] def maybeReceive[A](value: A, i: Int, iter: Int): Boolean

  /** Potentially send on the OutPort of branch `index` on iteration `iter`. */
  private[channel] def maybeSend[A](index: Int, iter: Int): Option[A]

  /** Receive indication from branch `i` that the port has closed on iteration
    * `iter`. */
  private[channel] def portClosed(i: Int, iter: Int): Unit
}

// ==================================================================

/** The base class of branches of an Alt. */
trait AltBranch{
  /** Combine this with other, to produce a choice between two branches. */
  def | (other: AltBranch) = new InfixAltBranch(this, other)

  /** Unpack this into its atomic branches. */
  private[scl] def unpack: List[AtomicAltBranch]
}

// ==================================================================

/** An atomic AltBranch, i.e. offering no choice. */
trait AtomicAltBranch extends AltBranch{
  private[scl] def unpack = List(this)
}

// ==================================================================

/** The combination of two AltBranches. */
class InfixAltBranch(left: AltBranch, right: AltBranch) extends AltBranch{
  private[scl] def unpack = left.unpack ++ right.unpack
}
