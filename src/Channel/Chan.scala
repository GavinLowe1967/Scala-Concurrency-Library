package ox.scl.channel


/** The superclass of channels. */
trait Chan[A] extends InPort[A] with OutPort[A]{

  /** Reopen the channel.  Precondition: the channel is closed, and no threads
    * are trying to send or receive. */
  def reopen: Unit

  // ======================================== Registration rules

  // The following might be too strict, particularly for BuffChans.  The
  // current rule is that a channel can be registered in at most one alt at a
  // time (considering both ports).

  /** Can an alt register at the InPort? */
  protected def checkCanRegisterIn = {
    require(receivingAlt == null, s"Inport of channel used in two alts.")
    require(sendingAlt == null, s"Both ports of channel used in alts.")
  }

  /** Can an alt register at the OutPort? */
  protected def checkCanRegisterOut = {
    require(receivingAlt == null, s"Both ports of channel used in alts.")
    require(sendingAlt == null, s"Outport of channel used in two alts.")
  }

}
