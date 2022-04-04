package tests

import ox.scl._
import scala.util.Random

/** Test on an alt where channels are shared. */
object AltSharedTest{
  /* The first test has the InPort of in shared between middle1 and middle2.
   * The second test also has out2 shared between middle2 and middle3. */

  /** Number of iterations per test. */
  var iters = 100

  var verbose = false

  /** Thread that sends [0..iters) on out. */
  def sender1(out: ![Int]) = thread{ 
    for(i <- 0 until iters) out!i; out.endOfStream 
  }

  /** One-place buffer from in to out, using a serve. */
  def middle1(in: ?[Int], out: ![Int]) = thread{
    serve( in =?=> { x => if(verbose) println(s"transmitting $x"); out!x } )
    out.endOfStream
  }

  /* In the case of test2, we want to close out2 when both middle2 and middle3
   * are finished: each calls maybeClose, and the second call closes the
   * channel. */
  var doneCount = 0
  def maybeClose(out: ![Int]) = synchronized{
    doneCount += 1; if(doneCount == 2){ out.endOfStream; doneCount = 0 }
  }

  /** One-place buffer from in to out, not using an alt.  flag is true if we're
    * doing test1. */
  def middle2(in: ?[Int], out: ![Int], flag: Boolean) = thread{
    if(!flag) Thread.sleep(30) // pause to give middle3 a chance
    repeat{ 
      val x = in?(); out!x; 
      if(!flag && Random.nextInt(3) == 0) Thread.sleep(1) // pause again
    }
    if(flag) out.endOfStream else maybeClose(out)
  }

  /** Unbounded buffer from in to out, using an alt. */
  def middle3(in: ?[Int], out: ![Int]) = thread{
    var holding = new scala.collection.mutable.Queue[Int] // List[Int]()
    serve(
      in =?=> { x => 
          holding.enqueue(x); if(verbose) println(s"transmitting $x") }
      | holding.nonEmpty && out =!=> { holding.dequeue } // ==> 
         // { holding = holding.tail }
    )
    maybeClose(out)
  }

  /** Receiver that expects to receive a permutation of [0..iters) between in1
    * and in2. */
  def receiver(in1: ?[Int], in2: ?[Int]) = thread{
    val received = new Array[Boolean](iters)
    serve(
      in1 =?=> { x => assert(!received(x)); received(x) = true }
      | in2 =?=> { x => assert(!received(x)); received(x) = true }
    )
    assert(received.forall(_ == true))
  }

  /** Receiver that expects to receive a permutation of [0..iters) on out. */
  def receiver2(out: ?[Int]) = thread{
    val received = new Array[Boolean](iters)
    repeat{
      val x = out?(); assert(!received(x)); received(x) = true 
    }
    assert(received.forall(_ == true))
  }

  /** Test where an InPort is shared. */
  def doTest1 = {
    // The InPort of left is shared between an alt and a non-alt. 
    val left, right1, right2 = new SyncChan[Int]
    run(sender1(left) || middle1(left, right1) || middle2(left, right2, true) || 
      receiver(right1, right2))
  }

  /** Test where both an InPort and an OutPort are shared. */
  def doTest2 = {
    // The InPort of left and the OutPort of right are each shared between an
    // alt and a non-alt. */
    val left, right = new SyncChan[Int]
    run(sender1(left) || middle3(left, right)  || middle2(left, right, false)||
      receiver2(right))
  }

  def main(args: Array[String]) = {    
    // Parse args.
    var reps = 1000; var i = 0; var test = 1
    while(i < args.length) args(i) match{
      case "--reps" => reps = args(i+1).toInt; i += 2
      case "--verbose" => verbose = true; i += 1
      case "--test2" => test = 2; i += 1
      case arg => println(s"Unrecognised argument: $arg")
    }

    for(i <- 0 until reps){
      if(test == 1) doTest1 else doTest2
      if(i%10 == 0) print(".")
    }
    println
  }
}
