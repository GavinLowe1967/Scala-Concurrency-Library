// A simple implementation of partial solutions
class Partial{
  /** Array holding the digits played so far, with 0 representing a blank
    * square. */
  private val contents = Array.ofDim[Int](9,9)

  /** Initialise from a file. */
  def init(fname: String) = {
    val lines = scala.io.Source.fromFile(fname).getLines
    for(i <- 0 until 9){
      val line = lines.next
      for(j <- 0 until 9){
	val c = line.charAt(j)
	if(c.isDigit) contents(i)(j) = c.asDigit
	else { assert(c=='.'); contents(i)(j) = 0 }
      }
    }
  }

  /** Print. */
  def printPartial = {
    for(i <- 0 until 9){
      for(j <- 0 until 9) print(contents(i)(j))
      println 
    }
    println
  }

  /** Is the partial solution complete? */
  def complete : Boolean = {
    for(i <- 0 until 9; j <- 0 until 9) if(contents(i)(j) == 0) return false
    true
  }

  /** Find a blank position; precondition: complete returns false. */
  def nextPos: (Int,Int) = {
    for(i <- 0 until 9; j <- 0 until 9) if(contents(i)(j) == 0) return (i,j)
    throw new RuntimeException("No blank position")
  }

  /** Can we play value d in position (i,j); precondition: (i,j) is blank. */
  def canPlay(i:Int, j:Int, d:Int): Boolean = {
    // Check if d appears in row i
    for(j1 <- 0 until 9) if(contents(i)(j1) == d) return false
    // Check if d appears in column j
    for(i1 <- 0 until 9) if(contents(i1)(j) == d) return false
    // Check if d appears in this 3x3 block
    val basei = i/3*3; val basej = j/3*3
    for(i1 <- basei until basei+3; j1 <- basej until basej+3)
      if(contents(i1)(j1) == d) return false
    // All checks passed
    true
  }

  /** Create a new partial solution, extending this one by playing d in
    * position (i,j). */
  def play(i:Int, j:Int, d:Int) : Partial = {
    val p = new Partial
    for(i1 <- 0 until 9; j1 <- 0 until 9) 
      p.contents(i1)(j1) = contents(i1)(j1)
    p.contents(i)(j) = d
    p
  }
}
