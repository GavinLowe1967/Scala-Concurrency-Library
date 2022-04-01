import ox.scl._

/** Program to show the dangers of undisciplined shared variables. */
object Race2{

  object Counter{
    private var x = 0
    
    def inc = x = x+1

    def dec = x = x-1

    def getX = x
  }

  // Process to increment x 1000 times
  def p = thread{ for(i <- 0 until 1000) Counter.inc }

  // Process to decrement x 1000 times
  def q = thread{ for(i <- 0 until 1000) Counter.dec }

  // Parallel composition
  def system = p || q

  def main(args : Array[String]) = {
    run(system); println(Counter.getX)
  }
}
