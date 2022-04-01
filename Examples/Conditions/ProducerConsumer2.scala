import ox.scl._

/** A slot, used by multiple producers and consumers. */
class Slot2[T]{
  private var value = null.asInstanceOf[T]
  private var filled = false

  def put(v:T) = synchronized{
    while(filled) wait()
    value = v; filled = true
    notifyAll()
  }

  def get : T = synchronized{
    while(!filled) wait()
    val result = value; filled = false
    notifyAll()
    result
  }
}

/** System testing the slot. */
object ProducerConsumer2{
  val slot = new Slot2[Int]

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
  
