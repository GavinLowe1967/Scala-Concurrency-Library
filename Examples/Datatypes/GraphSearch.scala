/** A trait representing an unlabelled graph with nodes of type N. */
trait Graph[N]{
  /** The successors of node n. */
  def succs(n: N): List[N]
}

// -------------------------------------------------------

/** Trait representing a graph search problem in graph g. */
abstract class GraphSearch[N](g: Graph[N]){
  /** Try to find a path in g from start to a node that satisfies isTarget. */
  def apply(start: N, isTarget: N => Boolean): Option[List[N]]
}

// -------------------------------------------------------

import scala.collection.mutable.{Set,Queue}

/** Sequential graph search implementation. */
class SeqGraphSearch[N](g: Graph[N]) extends GraphSearch[N](g){
  def apply(start: N, isTarget: N => Boolean): Option[List[N]] = {
    // All nodes seen so far.
    val seen = Set[N](start)
    // Queue storing nodes and the path leading to that node
    val queue = new Queue[(N, List[N])](); queue += ((start, List(start)))

    while(queue.nonEmpty){
      val (n, path) = queue.dequeue
      for(n1 <- g.succs(n)){
        if(isTarget(n1)) return Some(path :+ n1)
        else if(seen.add(n1)) queue.enqueue((n1, path :+ n1))
      }
    }
    None
  }
}

// -------------------------------------------------------

/** A concurrent set with an add operation. */
class ConcSet[A]{
  /** The underlying set. */
  private val set = Set[A]()

  /** Add x to this set.  Return true if x was not previously in the set. */
  def add(x: A): Boolean = synchronized{ set.add(x) }
  // This implementation uses a monitor, for simplicity.
}

// -------------------------------------------------------

import ox.scl._

/** A class to search Graph g concurrently. */
class ConcGraphSearch[N](g: Graph[N]) extends GraphSearch[N](g){
  /**The number of workers. */
  val numWorkers = 8

  /** Try to find a path in g from start to a node that satisfies isTarget. */
  def apply(start: N, isTarget: N => Boolean): Option[List[N]] = {
    // All nodes seen so far.
    val seen = new ConcSet[N]; seen.add(start)
    // Queue storing edges and the path leading to that edge
    val queue = new TerminatingPartialQueue[(N, List[N])](numWorkers)
    queue.enqueue((start, List(start)))

    // Channel on which a worker tells the coordinator that it has found a
    // solution.
    val pathFound = new SyncChan[List[N]]

    // A single worker
    def worker = thread("worker"){
      repeat{
        val (n, path) = queue.dequeue
        for(n1 <- g.succs(n)){
          if(isTarget(n1)) pathFound!(path :+ n1) // done!
          else if(seen.add(n1)) queue.enqueue((n1, path :+ n1))
        }
      }
      pathFound.close // causes coordinator to close down
    }

    // Variable that ends up holding the result; written by coordinator. 
    var result: Option[List[N]] = None

    def coordinator = thread("coordinator"){
      attempt{ result = Some(pathFound?()) }{ }
      queue.shutdown // close queue; this will cause most workers to terminate
      pathFound.close // in case another thread has found solution
    }

    val workers = || (for(_ <- 0 until numWorkers) yield worker)
    run(workers || coordinator)
    result
  }
}
