import ox.scl._

/** A ring of node processes.  The ith node holds the value xs(i).  Each ends
  * up with the value of foldleft f xs.  Node i outputs its final value on
  * outs(i).  */
class RingFold[T](xs: Array[T], f: (T,T) => T, outs: Array[SyncChan[T]]){
  private val n = xs.length
  require(n >= 2 && outs.length == n)

  /** The channels connecting nodes, indexed by the recipient's identity:
    * node(i) receives on chans(i) and sends on chans((i+1)%n). */
  private val chans = Array.fill(n)(new SyncChan[T]) 

  /** A single node. */
  private def node[T](me: Int) = thread{
    val left = chans(me); val right = chans((me+1)%n)
    val out = outs(me); val x = xs(me)
    if(me == 0){
      right!x // Start things going
      val result = left?(); right!result// Receive final result and pass it on
      out!result
    }
    else{
      val y = left?(); right!f(y,x) // Add my value to value being passed
      val result = left?()          // Receive final result
      if(me != n-1) right!result    // Pass it on if I'm not the last node
      out!result
    }
  }  

  /** The complete ring. */
  def apply(): Computation = || (for(i <- 0 until n) yield node(i))
}

// -------------------------------------------------------

import scala.reflect.ClassTag

/** A ring of node processes.  The ith node holds the value xs(i).  Each ends
  * up with the value of foldleft f xs.  Node i outputs its final value on
  * outs(i).  This uses an assumption that f is associative and commutative. */
class RingFold1[T: ClassTag](
    xs: Array[T], f: (T,T) => T, outs: Array[SyncChan[T]]){
  private val n = xs.length
  require(n >= 2 && outs.length == n)

  /** The channels connecting nodes, indexed by the recipient's identity:
    * node(i) receives on chans(i) and sends on chans((i+1)%n).  The channels
    * need to be buffered. */
  private val chans = Array.fill(n)(new BuffChan[T](1)) 

  /** A single node. */
  private def node[T](me: Int) = thread{
    val left = chans(me); val right = chans((me+1)%n)
    val out = outs(me); val x = xs(me); var y = x
    // Inv: at the start of round i, y = foldl f xs[me-i..me], where the indices
    // are interpreted mod n. 
    for(i <- 0 until n-1){
      right!y         // = foldl f xs[me-i..me]
      val z = left?() // = foldl f xs[me-1-i..me-1]
      y = f(z, x)     // = foldl f xs[me-(i+1)..me], maintaining invariant
    }
    // y = foldl f xs[me-(n-1)..me] = foldl f xs since f is AC.
    out!y
  }  

  /** The complete ring. */
  def apply(): Computation = || (for(i <- 0 until n) yield node(i))
}


// -------------------------------------------------------

import scala.util.Random
 
object RingFoldTest{
  /** A process that expects to receive expected on each channel in chans, and
    * throws an exception if an incorrect value is received. */
  def checker(chans: Array[SyncChan[Int]], expected: Int) = thread{
    for(chan <- chans){ val res = chan?(); assert(res == expected) }
  }

  /** A single test of either RingFold or RingFold1, using random values.
    * @param assoc use RingFold1 if true */
  def doTest(assoc: Boolean) = {
    val n = 2+Random.nextInt(20)
    val xs = Array.fill(n)(Random.nextInt(100))
    val outs = Array.fill(n)(new SyncChan[Int])
    def f(x: Int, y: Int) = if(assoc) x+y else 2*x+y
    val rf =
      // Note: abbreviating ".apply" to "()" requires the ClassTag
      // if(assoc) new RingFold1[Int](xs, f, outs)(ClassTag(classOf[Int]))()
      if(assoc) new RingFold1[Int](xs, f, outs).apply()
      else new RingFold[Int](xs, f, outs)()
    run(rf || checker(outs, xs.foldLeft(0)(f)))
  }

  def main(args: Array[String]) = {
    val assoc = args.nonEmpty && args(0) == "--assoc"
    for(i <- 0 until 100000){
      doTest(assoc); if(i%200 == 0) print(".")
    }
    println
  }

}
