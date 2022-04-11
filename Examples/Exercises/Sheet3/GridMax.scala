import ox.scl._

/** n^2 processes are arranged in a toroidal grid; each can communicate with
  * its four neighbours.  Each has an integer v.  Each should find the overall
  * max. */
class GridMax(n: Int, xss: Array[Array[Int]]){
  require(n >= 1 && xss.length == n && xss.forall(_.length == n))

  /** Array to hold results. */
  private val results = Array.ofDim[Int](n,n)

  /** Worker with coordinates (i,j) and starting value x, with channels to allow
    * data to be passed upwards and rightwards. */
  private def worker(i: Int, j: Int, x: Int, readUp: ?[Int], writeUp: ![Int], 
	             readRight: ?[Int], writeRight: ![Int])
    = thread("worker"+(i,j)){
    var myMax = x
    // propogate values along rows
    for(i <- 0 until n-1){
      writeRight!myMax; myMax = myMax max readRight?()
    }
    // propogate values upwards
    for(i <- 0 until n-1){
      writeUp!myMax; myMax = myMax max readUp?()
    }
    // Store result
    results(i)(j) = myMax
  }

  /** Channels by which values are passed rightwards; indexed by coords of
    * recipients. */
  private val right = Array.fill(n,n)(new BuffChan[Int](1))

  /** Channels by which values are passed upwards; indexed by coords of
    * recipients. */
  private val up = Array.fill(n,n)(new BuffChan[Int](1))

  /** Run the system, and return array storing results obtained. */
  def apply(): Array[Array[Int]] = {
    val workers = 
      || (for(i <- 0 until n; j <- 0 until n) yield
            worker(i, j, xss(i)(j),
                   up(i)(j), up(i)((j+1)%n), right(i)(j), right((i+1)%n)(j)))
    run(workers)
    results
  }
}

// -----------------------------------------------------------------

/** n^2 processes are arranged in a toroidal grid; each can communicate with
  * its four neighbours.  Each has an integer v.  Each should find the overall
  * max.  This version operates in O(log n) parallel time. */
class LogGridMax(n: Int, xss: Array[Array[Int]]){
  require(n >= 1 && xss.length == n && xss.forall(_.length == n))

  /** Array to hold results. */
  private val results = Array.ofDim[Int](n,n)

  /** Barrier for synchronisation. */
  private val barrier = new Barrier(n*n)

  private def mkId(i: Int, j: Int) = n*i+j

  /** Worker with coordinates (i,j) and starting value x, a single incoming
    * channel, and channels to send to every other worker. */
  private def worker(
      i: Int, j: Int, x: Int, read: ?[Int], write: List[List[![Int]]]
  ) = thread("worker "+(i,j)){
    var myMax = x // max I've seen so far
    val myId = mkId(i,j)
    var r = 0 // Round number 
    var gap = 1 // 2^r
    // Invariant: max is the maximum of the values initially chosen by nodes
    // ((i+di)%n, (j+dj)%n) for 0 <= di, dj < gap = 2^r
    while(gap < n){
      // Send max to processes gap positions left and/or down of here
      val i1 = (i+n-gap)%n; val j1 = (j+n-gap)%n
      write(i)(j1)!myMax; write(i1)(j1)!myMax; write(i1)(j)!myMax
      // Receive from three processes gap right and/or above here
      for(i <- 0 until 3) myMax = myMax max read?()
      // Update r, max
      r += 1; gap += gap
      barrier.sync(myId) // Sync before next round
    }
    results(i)(j) = myMax
  }

  private val chans = List.fill(n,n)(new BuffChan[Int](3))

  /** Run the system, and return array storing results obtained. */
  def apply(): Array[Array[Int]] = {
    val workers = 
      || (for(i <- 0 until n; j <- 0 until n) yield
            worker(i, j, xss(i)(j), chans(i)(j), chans))
    run(workers)
    results
  }
}

// ------------------------------------------------------------------

/** This version uses about 2 log n rounds, log n in each dimension. */
class LogGridMax2(n: Int, xss: Array[Array[Int]]){
  require(n >= 1 && xss.length == n && xss.forall(_.length == n))

  private def mkId(i: Int, j: Int) = n*i+j

  /** Array to hold results. */
  private val results = Array.ofDim[Int](n,n)

  /** Barriers for synchronisation in the two rounds. */
  private val rowBarriers = Array.fill(n)(new Barrier(n))
  private val colBarriers = Array.fill(n)(new Barrier(n))
  /* Note: we use a different barrier for each row in phase 1, and a different
   * barrier for each column in phase 2; but we use different barriers for
   * each phase, or else the synchronisations can go wrong, with a particular
   * synchronisation mixing calls to sync from the two phases. */

  /** Worker with coordinates (i,j) and starting value x, a single incoming
    * channel, and channels to send to every other worker. */
  private def worker(i: Int, j: Int, x: Int,
                     read: ?[Int], write: List[List[![Int]]])
    = thread("worker "+(i,j)){
      var myMax = x // max I've seen so far

      // Propagate along rows
      var r = 0; var gap = 1 // gap = 2^r
      // Inv: myMax = max xss(i)[j..j+gap) (looping round)
      while(gap < n){
        // Send to gap places left
        write(i)((j+n-gap)%n)!myMax
        // Receive from worker (i, (j+gap) mod n)
        myMax = myMax max read?() 
        // received value = max xss(i)[(j+gap) mod n .. (j+2gap) mod n)
        r += 1; gap += gap
        if(gap < n) rowBarriers(i).sync(j) // Sync before next round
      }
      // myMax = max xss(i)

      // Wait for all workers in this column to finish phase 1.
      colBarriers(j).sync(i)

      // Propagate up columns
      r = 0; gap = 1
      // Inv: myMax = max xss[i..i+gap) (looping round)
      while(gap < n){
        // Send to gap places down
        write((i+n-gap)%n)(j)!myMax
        // Receive from worker ((i+gap) mod n, j)
        myMax = myMax max read?() 
        // received value = max xss[(i+gap) mod n .. (i+2gap) mod n)
        r += 1; gap += gap
        if(gap < n) colBarriers(j).sync(i) // Sync before next round
      }

      results(i)(j) = myMax
  }

  private val chans = List.fill(n,n)(new BuffChan[Int](1))

  /** Run the system, and return array storing results obtained. */
  def apply(): Array[Array[Int]] = {
    val workers = 
      || (for(i <- 0 until n; j <- 0 until n) yield
            worker(i, j, xss(i)(j), chans(i)(j), chans))
    run(workers)
    results
  }
}
