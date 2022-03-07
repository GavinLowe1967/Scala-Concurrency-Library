package tests

import ox.scl._
import scala.util.Random

object AltTest{
  def tagger[T](l: ?[T], r: ?[T], out: ![(Int, T)]) = thread("tagger"){
    repeat{
      alt ( 
        (true && l) =?=> { x => /*println(s"received $x on l");*/ out!(0, x) }
        | (true && r) =?=> { x => println(s"received $x on r"); out!(1, x) }
      )
    }
    l .closeIn; r.closeIn; out.closeOut
  }

  val N = 5

  def sender(c: Chan[Int]) = thread(s"sender $c"){ 
    for(i <- 0 until N){ Thread.sleep(Random.nextInt(2)); c!i }
  }

  def main(args: Array[String]) = {
    val l,r = new SyncChan[Int]
    val out = new SyncChan[(Int,Int)]
    val t = tagger(l,r,out)
    val receiver = thread("receiver"){ 
      val nexts = new Array[Int](2) // expected next values
      while(nexts.forall(_ < N)){
        val (i,x) = out?(); println(s"receiver received ($i, $x)"); 
        assert(x == nexts(i)); nexts(i) += 1
      }
      println("receiver done")
    }
    val system = t || sender(l) || sender(r) || receiver
    run(system)
    ()
  }

}
 
