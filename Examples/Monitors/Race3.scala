import ox.scl._

/** Program to illustrate using a JVM monitor to protect a shared variable. */
object Race3{

  object Counter{
    private var x = 0
    
    def inc = synchronized{ x = x+1 }

    def dec = synchronized{ x = x-1 }

    def getX = synchronized{ x }
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
