import ox.scl._

/** Various things shared between the sequential and concurrent smoothing
  * functions. */
object Smooth{
  type Row = Array[Boolean] // of size w
  type Image = Array[Row] // of size N

  /** Test if majority of neightbours of b(i)(j) are set. */
  def majority(b: Image, i: Int, j: Int): Boolean = {
    val n = b.length; val w = b(0).length
    var sum = 0 // # set neighbours so far
    var count = 0 // # neighbours so far
    for(i1 <- i-1 to i+1; if i1 >= 0 && i1 < n;
        j1 <- j-1 to j+1; if j1 >= 0 && j1 < w){
      count += 1; if(b(i1)(j1)) sum += 1
    }
    2*sum >= count
  }

  /** Print the image in a. */
  def printArray(a: Smooth.Image) = {
    val n = a.length; val w = a(0).length
    for(i <- 0 until n){
      for(j <- 0 until w) if(a(i)(j)) print("*") else print(" ");
      println;
    }
    println;
  }
}

/** Smooth image a, using p workers, with at most maxIters iterations. */
class SmoothShared(a: Array[Array[Boolean]], p: Int, maxIters: Int){
  private val n = a.length; assert(a.forall(_.length == n))
  assert(n%p == 0); private val height = n/p // height of each strip

  /** Barrier used at the end of each reading stage. */
  private val barrier = new Barrier(p)

  /** Barrier uses at the end of each writing state, and to decide
    * termination. */
  private val combBarrier = new AndBarrier(p)
   
  /** Worker process. */
  private def worker(me: Int) = thread("worker"+me){
    val start = me*height; val end = (me+1)*height
    // This worker is responsible for rows [start..height)
    var done = false; var iters = 0

    while(!done && iters < maxIters){
      // This thread will calculate next smoothed values in newA.  newA(i) will
      // hold the new value for myA(start+i).
      val newA = Array.ofDim[Boolean](height, n)
      var myDone = true // have no values changed yet?
      for(i <- start until end; j <- 0 until n){
	newA(i-start)(j) = Smooth.majority(a, i, j)
	myDone &&= (newA(i-start)(j) == a(i)(j))
      }
      barrier.sync(me)

      // Copy into a
      for(i <- start until end) a(i) = newA(i-start)
      // Synchronise with neighbours
      iters += 1
      done = combBarrier.sync(me, myDone) 
    }
  }

  /** Smooth the image. */
  def apply() = run( || (for (i <- 0 until p) yield worker(i)) )
}

// ==================================================================

/** This version uses two arrays. */
class SmoothShared2(a: Array[Array[Boolean]], p: Int, maxIters: Int){
  private val n = a.length; private val w = a(0).length
  assert(a.forall(_.length == w))
  assert(n%p == 0); private val height = n/p // height of each strip

  // The two arrays.  We set a1 to be the original a. 
  private val a1 = a; private val a2 = Array.ofDim[Boolean](n,w)

  /** Barrier used at the end of each round, and also to decide termination. */
  private val combBarrier = new AndBarrier(p)

  /** Worker process. */
  private def worker(me: Int) = thread("worker"+me){
    val start = me*height; val end = (me+1)*height
    // This worker is responsible for rows [start..height)
    var oldA = a1; var newA = a2  // Old and new versions of the image
    var done = false; var iters = 0

    while(!done && iters < maxIters){
      var myDone = true // have no values changed yet?
      for(i <- start until end; j <- 0 until w){
	newA(i)(j) = Smooth.majority(oldA, i, j)
	myDone &&= (newA(i)(j) == oldA(i)(j))
      }
      iters += 1; done = combBarrier.sync(me, myDone) // Synchronise with others
      if(!done && iters < maxIters){ val t = oldA; oldA = newA; newA = t }
    }

    // Copy newA into a, if not already equal. 
    if(newA != a) for(i <- start until end) a(i) = newA(i)
  }

  /** Smooth the image. */
  def apply() = run( || (for (i <- 0 until p) yield worker(i)) )
}
