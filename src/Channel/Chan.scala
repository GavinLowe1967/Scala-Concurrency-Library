package ox.scl.channel

/* Basics of channels. */

/** Exception thrown if a communication is attempted on a channel that is
  * closed. */
class Closed extends ox.scl.Stopped

// ==================================================================

/** The sending end of a channel. */
trait OutPort[A]{
  /** Send `x` on the channel. */
  def !(x: A): Unit

  /** Close the channel for sending. */
  def closeOut(): Unit
}

// ==================================================================

/** The superclass of channels. */
trait Chan[A] extends InPort[A] with OutPort[A]{
  /** Close the channel. */
  def close(): Unit

  /** Reopen the channel.  Precondition: the channel is closed, and no threads
    * are trying to send or receive. */
  def reopen(): Unit
}
