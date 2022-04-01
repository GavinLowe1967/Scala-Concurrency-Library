import ox.scl._

/** A slot, used by a single producer and a single consumer. */
class Slot[T]{
  private var value = null.asInstanceOf[T]
  private var filled = false

  def put(v:T) = synchronized{
    while(filled) wait()
    value = v; filled = true
    notify()
  }

  def get : T = synchronized{
    while(!filled) wait()
    val result = value; filled = false
    notify()
    result
  }
}

/** System testing the slot. */
object ProducerConsumer{
  val slot = new Slot[Int]

  def producer = thread("Producer"){
    for(i <- 0 until 1000) slot.put(i)
  }

  def consumer = thread("Consumer"){
    for(i <- 0 until 1000){
      val v = slot.get; assert(v == i) // if(v%100 == 0) println(v)
    }
  }

  def main(args : Array[String]) = {
    for(i <- 0 until 1000){
      run(producer || consumer); print(".")
    }
    println
  }
}
  
