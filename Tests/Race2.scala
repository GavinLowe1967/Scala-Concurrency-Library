package tests

import ox.scl._

/** Program that avoids races by locking. */
object Race2{
  var x = 0

  /** Lock to protect `x`. */
  val lock = new Lock

  /** Process to increment x 1000 times. */
  def p = thread{ for(i <- 0 until 1000) lock.mutex{ x = x+1 } }

  /** Process to decrement x 1000 times. */
  def q = thread{ for(i <- 0 until 1000) lock.mutex{ x = x-1 } }

  /** Parallel composition. */
  def system = p || q

  def main(args : Array[String]) = {
    for(i <- 0 until 10000){ 
      run(system); assert(x == 0); if(i%100 == 0) print(".") 
    }
    println()
  }
}
