import ox.scl._

/** A slot, based on an SCL monitor. */
class MonitorSlot[T]{
  private var value = null.asInstanceOf[T]
  private var filled = false

  private val lock = new Lock
  // conditions for signalling the slot is empty or non-empty, respectively.
  private val empty, nonEmpty = lock.newCondition

  def put(v: T) = lock.mutex{
    // while(filled) empty.await()
    empty.await(!filled)
    value = v; filled = true
    nonEmpty.signal() // signal to a get
  }

  def get: T = lock.mutex{
    // while(!filled) nonEmpty.await()
    nonEmpty.await(filled)
    filled = false; empty.signal() // signal to a put
    value
  }
}

/** System informally testing the slot. */
object MonitorSlotTest{
  val slot = new MonitorSlot[Int]

  def producer = thread("Producer"){
    for(i <- 0 until 1000) slot.put(i)
  }

  def consumer = thread("Consumer"){
    for(i <- 0 until 1000){
      val v = slot.get; if(v%100 == 0) println(v)
    }
  }

  def main(args : Array[String]) = {
    run(producer || producer || consumer || consumer)
  }

}
