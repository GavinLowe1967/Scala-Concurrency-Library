import ox.scl._

/* Program to display the perils of busy waiting. */

class Slot[T]{
  private var value = null.asInstanceOf[T]
  private var filled = false

  def put(v :T) = {
    while(filled){  } 
    value = v; filled = true
  }

  def get : T = {
    while(!filled){  }
    val result = value; filled = false
    result
  }
}

object BusyWait{
  val slot = new Slot[Int]

  def Producer = thread("Producer"){
    for(i <- 0 until 100) slot.put(i)
  }

  def Consumer = thread("Consumer"){
    for(i <- 0 until 100){
      val v = slot.get; assert(v == i) // ; println(v)
    }
  }

  def main(args : Array[String]) = 
    for(i <- 0 until 1000){
      run(Producer || Consumer); print(".")
    }
}
  
