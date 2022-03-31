import ox.scl._

/** Trait representing objects that perform Jacobian iteration. */
trait Jacobi{
  val Epsilon = 0.000001 // tolerance

  /** Find x such that a x is approximately b, performing Jacobi iteration until
    * successive iterations vary by at most Epsilon.
    * Pre: a is of size n by n, and b is of size n, for some n. */
  def solve(a: Array[Array[Double]], b: Array[Double]): Array[Double]

  /** Calculate new value for x(i) based on the old value. */ 
  @inline protected def update(
    a: Array[Array[Double]], b: Array[Double], x: Array[Double], i: Int, n: Int)
      : Double = {
    var sum = 0.0
    for(j <- 0 until n; if j != i) sum += a(i)(j)*x(j)
    (b(i)-sum) / a(i)(i)
  }
}

// -------------------------------------------------------

/** A sequential implementation of Jacobian iteration. */
object SeqJacobi extends Jacobi{
  def solve(a: Array[Array[Double]], b: Array[Double]): Array[Double] = {
    val n = a.length
    require(a.forall(_.length == n) && b.length == n)
    var oldX, newX = new Array[Double](n)
    var done = false

    while(!done){
      done = true
      for(i <- 0 until n){
        newX(i) = update(a, b, oldX, i, n)
	done &&= Math.abs(oldX(i)-newX(i)) < Epsilon
      }
      if(!done){ val t = oldX; oldX = newX; newX = t } // swap arrays
    }
    newX
  }
}

// -------------------------------------------------------

/** A concurrent implementation of Jacobian iteration, using shared variables. */
class ConcJacobi(p: Int) extends Jacobi{
  private val combBarrier = new AndBarrier(p)

  def solve(a: Array[Array[Double]], b: Array[Double]): Array[Double] = {
    val n = a.length
    require(a.forall(_.length == n) && b.length == n && n%p == 0)
    val height = n/p // height of one strip
    var x0, x1 = new Array[Double](n)
    var result: Array[Double] = null // ends up storing final result

    // Worker to handle rows [me*height .. (me+1)*height).
    def worker(me: Int) = thread{
      val start = me*height; val end = (me+1)*height
      var oldX = x0; var newX = x1; var done = false
      while(!done){
        var myDone = true
        for(i <- start until end){
          newX(i) = update(a, b, oldX, i, n)
	  myDone &&= Math.abs(oldX(i)-newX(i)) < Epsilon
        }
        done = combBarrier.sync(me, myDone)
        if(!done){ val t = oldX; oldX = newX; newX = t } // swap arrays
      }
      // worker 0 sets result to final result
      if(start == 0) result = newX
    }

    // Run system
    run(|| (for (i <- 0 until p) yield worker(i)))
    result
  }
}

// -------------------------------------------------------

/** A concurrent implementation of Jacobian iteration, using message
  * passing. */
class JacobiMP0(p: Int) extends Jacobi{
  private val barrier = new Barrier(p)
  // Messages are triples: worker identity, new segment of x, is that worker
  // willing to terminate?
  private type Msg = (Int, Array[Double], Boolean)
  // Channels to send messages to workers: indexed by receiver's identity;
  // buffered.
  private val toWorker = Array.fill(p)(new BuffChan[Msg](p-1))

  def solve(a: Array[Array[Double]], b: Array[Double]): Array[Double] = {
    val n = a.length
    require(a.forall(_.length == n) && b.length == n && n%p == 0)
    val height = n/p // height of one strip
    var result: Array[Double] = null // will hold final result

    // Worker to handle rows [me*height .. (me+1)*height).
    def worker(me: Int) = thread{
      val start = me*height; val end = (me+1)*height; var done = false
      val x = new Array[Double](n)

      while(!done){
        done = true
        // newX(i) holds the new value of x(i+start)
        val newX = new Array[Double](height)
        // Update this section of x, storing results in newX
        for(i <- start until end){
          newX(i-start) = update(a, b, x, i, n)
          done &&= Math.abs(x(i)-newX(i-start)) < Epsilon
        }
        // Send this section to all other processes
        for(w <- 1 until p) toWorker((me+w)%p)!(me, newX, done)
        // Copy newX into x
        for(i <- 0 until height) x(start+i) = newX(i)
        // Receive from others
        for(k <- 0 until p-1){
          val (him, hisX, hisDone) = toWorker(me)?()
          for(i <- 0 until height) x(him*height+i) = hisX(i)
          done &&= hisDone
        }
        // Synchronise for end of round
        barrier.sync(me)
      }
      if(me == 0) result = x // copy final result
    } // end of worker

    // Run system
    run(|| (for(i <- 0 until p) yield worker(i)))
    result
  }
}

// -------------------------------------------------------

/** A concurrent implementation of Jacobian iteration, using message
  * passing, and avoiding copying of individual elements. */
class JacobiMP(p: Int) extends Jacobi{
  private val barrier = new Barrier(p)
  // Messages are triples: worker identity, new segment of x, is that worker
  // willing to terminate?
  private type Msg = (Int, Array[Double], Boolean)
  // Channels to send messages to workers: indexed by receiver's identity;
  // buffered.
  private val toWorker = Array.fill(p)(new BuffChan[Msg](p-1))

  def solve(a: Array[Array[Double]], b: Array[Double]): Array[Double] = {
    val n = a.length
    require(a.forall(_.length == n) && b.length == n && n%p == 0)
    val height = n/p // height of one strip
    var result: Array[Double] = null // will hold final result

    // Worker to handle rows [me*height .. (me+1)*height).
    def worker(me: Int) = thread{
      val start = me*height; val end = (me+1)*height; var done = false
      val xs = Array.ofDim[Double](p,height)
      // xs represents the vector x = xs.flatten

      while(!done){
        done = true
        // newX(i) holds the new value of x(i+start) = xs(me)(i)
        val newX = new Array[Double](height)
        // Update this section of x, storing results in newX
        for(i1 <- 0 until height){
          val i = start+i1
          var sum = 0.0
          for(k <- 0 until p; j1 <- 0 until height){
            val j = k*height+j1; if(j != i) sum += a(i)(j)*xs(k)(j1)
          }
          newX(i1) = (b(i)-sum) / a(i)(i)
          done &&= Math.abs(xs(me)(i1)-newX(i1)) < Epsilon
        }
        // Send this section to all other processes
        for(w <- 1 until p) toWorker((me+w)%p)!(me, newX, done)
        // Copy newX into x
        xs(me) = newX
        // Receive from others
        for(k <- 0 until p-1){
          val (him, hisX, hisDone) = toWorker(me)?()
          xs(him) = hisX; done &&= hisDone
        }
        // Synchronise for end of round
        barrier.sync(me)
      }
      if(me == 0) result = xs.flatten // copy final result
    } // end of worker

    // Run system
    run(|| (for(i <- 0 until p) yield worker(i)))
    result
  }
}
