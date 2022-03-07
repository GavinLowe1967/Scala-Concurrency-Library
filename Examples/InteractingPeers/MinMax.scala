import ox.scl._

/** The trait specifying the various min-max examples.
  * 
  * Each thread calls apply, passing in its value, and gets back the overall
  * minimum and maximum. */
trait MinMax{
  type IntPair = (Int, Int)

  /** Submit value, and receive back overall minimum and maximum. 
    * @param me the identity of this thread.
    * @param x the value submitted. */
  def apply(me: Int, x: Int): IntPair
}

// -------------------------------------------------------

/** Implementation of MinMax using a controller.  The controller is the thread
  * with identity 0. */
class Centralised(n: Int) extends MinMax{
  private val toController = new BuffChan[Int](n-1 max 1)
  private val fromController = new BuffChan[IntPair](n-1 max 1)

  def apply(me: Int, x: Int): IntPair = {
    if(me == 0){ // this is the controller
      var min = x; var max = x
      // Receive values from other threads
      for(i <- 1 until n){
        val w = toController?() ; if(w < min) min = w; if(w > max) max = w
      }
      // Distribute min and max
      for(i <- 1 until n) fromController!(min,max)
      (min, max)
    }
    else{
      toController!x  // submit my value
      fromController?() // get back the result
    }
  }
}

// -------------------------------------------------------

/** Implementation of MinMax using the symmetric (fully connected) pattern. */
class Symmetric(n: Int) extends MinMax{
  /** Channels to send to nodes, indexed by the receivers' identities. */
  private val toNode = Array.fill(n)(new BuffChan[Int](n-1 max 1))

  def apply(me: Int, x: Int): IntPair = {
    // Process to send x to other threads.  First version has all threads
    // sending in the same order; the second tries to avoid contention.
    // def sender = thread{ for(i <- 0 until n) if(i != me) toNode(i)!x }
    def sender = thread{ for(i <- 1 until n) toNode((me+i)%n)!x }
    // Variables to hold min and max so far
    var min = x; var max = x
    // Process to receive from other threads, and accumulate min and max
    def receiver = thread{
      for(i <- 1 until n){
	val w = toNode(me)?(); if(w < min) min = w; if(w > max) max = w
      }
    }
    run(sender || receiver)
    (min, max)
  }
}

// -------------------------------------------------------

/** Implementation of MinMax using a ring, with a distinguished initiator.
  * The initiator is the thread with identity 0. */
class Ring(n: Int) extends MinMax{
  require(n >= 2)

  /** Channels connecting the threads.  Channel chan(i) goes from thread (i-1)
    * mod n to thread i; so thread me inputs on chan(me) and outputs on
    * chan((me+1)%n). */
  private val chan = Array.fill(n)(new BuffChan[IntPair](1))
 
  def apply(me: Int, x: Int): IntPair = {
    val in = chan(me); val out = chan((me+1)%n)
    if(me == 0){ // This is the initiator
      // Start the communications going
      out!(x, x)
      // Receive min and max back, and send them round
      val (min,max) = in?()
      out!(min,max)
      // Receive them back at the end
      // in?()
      (min, max)
    }
    else{
      // receive min and max so far, and pass on possibly updated min and max
      val (min1, max1) = in?()
      out!(Math.min(min1,x), Math.max(max1,x))
      // receive final min and max, and pass them on
      val (min, max) = in?()
      if(me != n-1) out!(min,max)
      (min, max)
    }
  } 
}

// -------------------------------------------------------

/** Implementation of MinMax using a symmetric ring. */
class RingSym(n: Int) extends MinMax{
  /** Channels connecting the threads.  Channel chan(i) goes from thread (i-1)
    * mod n to thread i; so thread me inputs on chan(me) and outputs on
    * chan((me+1)%n).  These channels need to be buffered. */
  private val chan = Array.fill(n)(new BuffChan[Int](1))

  def apply(me: Int, x: Int): IntPair = {
    val in = chan(me); val out = chan((me+1)%n)
    var min = x; var max = x
    out!x                 // send my value round
    for(k <- 1 until n){
      val w = in?()       // receive next value
      if(w < min) min = w
      if(w > max) max = w 
      out!w               // pass it on
    }
    val w = in?(); assert(x == w) // receive my value back
    (min, max)
  }
}

// -------------------------------------------------------

class Tree(n: Int) extends MinMax{
  /** Channels leading up and down the tree.  Each array is indexed by the
    * child's identity. */
  private val up, down = Array.fill(n)(new BuffChan[IntPair](1))
  
  def apply(me: Int, x: Int) = {
    // Identities of the two children
    val child1 = 2*me+1; val child2 = 2*me+2
    // min and max seen so far
    var min = x; var max = x
    // Receive min and max values from both children
    if(child1 < n){ 
      val (min1, max1) = up(child1)?()
      if(min1 < min) min = min1
      if(max1 > max) max = max1 
    }
    if(child2 < n){       
      val (min1, max1) = up(child2)?()
      if(min1 < min) min = min1 
      if(max1 > max) max = max1 
    }
    // Send min and max to parent, and wait for overall min and max to return
    if(me != 0){ 
      up(me)!(min, max) 
      val pair = down(me)?(); min = pair._1; max = pair._2
    }
    // Send min and max to children
    if(child1 < n) down(child1)!(min, max)
    if(child2 < n) down(child2)!(min, max)
    (min, max)
  }
}


  
