/** A graph corresponding to Sudoku problems.  From each node p of the graph,
  * there is a path to every solution of p.  Further, there is at most one
  * path between any pair of nodes p1 and p2. */
object SudokuGraph extends Graph[Partial]{
  /** The successors of a particular partial solution.
    * 
    * It is guaranteed that any solution of p is also a solution of a member
    * of succs(p), and vice versa.  Further, each element of succs(p) has
    * fewer blank squares than p.
    * 
    * Pre: !p.complete. */
  def succs(p: Partial): List[Partial] = {
    val (i,j) = p.nextPos
    (for(d <- 1 to 9; if p.canPlay(i, j, d)) yield p.play(i, j, d)).toList
  }
}

// -------------------------------------------------------

/** A program for solving Sudoku problems, based on a GraphSearch object.  
  * 
  * This expects to find the filename for the starting position as the first
  * argument on the command line.  */
object Sudoku{
  def main(args: Array[String]) = {
    val fname = args(0)
    val useConc = args.length > 1 && args(1) == "--conc"
    val p = new Partial; p.init(fname)
    val g = SudokuGraph
    val solver: GraphSearch[Partial] =
      if(useConc) new ConcGraphSearch(g) else new SeqGraphSearch(g)
    solver(p, _.complete) match{
      case Some(p1) => p1.printPartial
      case None => println("No solution found")
    }
  }
}
