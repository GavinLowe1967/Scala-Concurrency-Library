import ox.scl._

/** Program to show the dangers of undisciplined shared variables. */
object Race{
  var x = 0

  /** Process to increment x 1000 times. */
  def p = thread{ for(i <- 0 until 1000) x = x+1 }

  /** Process to decrement x 1000 times. */
  def q = thread{ for(i <- 0 until 1000) x = x-1 }

  /** Parallel composition. */
  def system = p || q

  def main(args : Array[String]) = {
    run(system); println(x) 
  }
}
 
