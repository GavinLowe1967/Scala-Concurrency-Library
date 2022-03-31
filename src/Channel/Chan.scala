package ox.scl.channel


/** The superclass of channels. */
trait Chan[A] extends InPort[A] with OutPort[A]{
  /** Close the channel. */
  // def close(): Unit

  /** Reopen the channel.  Precondition: the channel is closed, and no threads
    * are trying to send or receive. */
  def reopen(): Unit
}
