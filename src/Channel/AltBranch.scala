package ox.scl.channel

/** Trait corresponding to the interface an alt presents to its branches and
  * ports. */
trait AltT{
  def maybeReceive[A](value: A, i: Int): Boolean
}

/** The base class of branches of an Alt. */
trait AltBranch{
  /** Combine this with other, to produce a choice between two branches. */
  def | (other: AltBranch) = new InfixAltBranch(this, other)

  /** Unpack this into its atomic branches. */
  def unpack: List[AtomicAltBranch]
}

// ==================================================================

/** An atomic AltBranch, i.e. offering no choice. */
trait AtomicAltBranch extends AltBranch{
  def unpack = List(this)
}

// ==================================================================

/** The combination of two AltBranches. */
class InfixAltBranch(left: AltBranch, right: AltBranch) extends AltBranch{
  def unpack = left.unpack ++ right.unpack

}
