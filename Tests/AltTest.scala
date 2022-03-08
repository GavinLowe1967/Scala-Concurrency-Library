package tests

import ox.scl._
import scala.util.Random

/** Test on an alt.  Two threads send to an alt, which tags the values and
  * passes them to a receiver. */
object AltTest{
  /** Tagger process. */
  def tagger[T](l: ?[T], r: ?[T], out: ![(Int, T)]) = thread("tagger"){
    repeat{
      alt ( 
        true && Random.nextInt(5) != 0 && l =?=> { x => 
          /*println(s"received $x on l");*/ out!(0, x) }
        | Random.nextInt(5) != 0 && r =?=> { x => 
          /*println(s"received $x on r");*/ out!(1, x) }
      ) 
    }
    // println("tagger closed")
    l .closeIn; r.closeIn; out.closeOut
  }

  /** Number of values sent on each channel per test. */
  val N = 50

  /** Sender that sends [0..N) on c. */
  def sender(c: Chan[Int]) = thread(s"sender $c"){ 
    var i = 0
    repeat(i < N){ Thread.sleep(Random.nextInt(2)); c!i; i += 1 }
    //println(s"sender $c done")
    Thread.sleep(Random.nextInt(2))
    c.closeIn
  }
    
  def receiver(out: ?[(Int,Int)]) = thread("receiver"){
    val nexts = new Array[Int](2) // expected next values
    repeat(nexts.exists(_ < N)){
      val (i,x) = out?(); // println(s"receiver received ($i, $x)");
      assert(x == nexts(i) && x < N); nexts(i) += 1
    }
    //println("receiver done")
  } 

  /** Run a single test. */
  def doTest = {
    val l,r = new SyncChan[Int]
    val out = new SyncChan[(Int,Int)]
    val t = tagger(l,r,out)
    val system = t || sender(l) || sender(r) || receiver(out)
    run(system)
  }

  def main(args: Array[String]) = {
    var reps = 1000
    for(i <- 0 until reps){ doTest; if(i%10 == 0) print(".") }
    println
  }

}
 
