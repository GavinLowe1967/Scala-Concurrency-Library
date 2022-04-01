import ox.scl._

class ConcGraphSearch[N](g: Graph[N]) extends GraphSearch[N](g){
  /**The number of workers. */
  val numWorkers = 8

  /** Perform a depth-first search in g, starting from start, for a node that
    * satisfies isTarget. */
  def apply(start: N, isTarget: N => Boolean): Option[N] = {
    // The stack supporting the depth-first search, holding nodes.
    val stack = new TerminatingPartialStack[N](numWorkers)
    stack.push(start)

    // Channel on which a worker tells the coordinator that it has found a
    // solution.
    val solnFound = new SyncChan[N]

    // A worker thread
    def worker = thread("worker"){
      repeat{
        val n = stack.pop
        for(n1 <- g.succs(n)){
          if(isTarget(n1)) solnFound!n1 else stack.push(n1)
        }
      }
      solnFound.close // causes coordinator to close down
    }

    // Variable that ends up holding the result; written by coordinator. 
    var result: Option[N] = None

    // The coordinator.
    def coordinator = thread("coordinator"){
      attempt{ result = Some(solnFound?()) }{ }
      stack.shutdown // close stack; this will cause most workers to terminate
      solnFound.close // in case another thread has found solution
    }

    val workers = || (for(_ <- 0 until numWorkers) yield worker)
    run(workers || coordinator)
    result
  }
}
