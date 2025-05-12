package tests

import ox.scl._
import scala.util.Random

/** Test of the case where a port is duplicated in an alt or serve. */
object AltDupTest{
  val N = 10

  var buffering = 0

  var verbose = false

  /** Tagger process.  This has the same InPort in both branches. */
  def tagger[T](l: ??[T], out: !![(Int, T)]) = thread("tagger"){
    serve( 
      l =?=> { x => out!(0, x) }
      | l =?=> { x => out!(1, x) }
    )
    // println("tagger closed")
    l .close(); out.endOfStream()
  }

  /** Sender that sends [0..N) on c. */
  def sender(c: Chan[Int]) = thread(s"sender $c"){ 
    var i = 0
    repeat(i < N){ Thread.sleep(Random.nextInt(3)); c!i; i += 1 }
    if(verbose) println("Sender closing")
    c.endOfStream()
  }

  def receiver(out: ??[(Int,Int)]) = thread("receiver"){
    var expected = 0; var expectedTag = 0
    while(expected < N){
      if(verbose) println(s"receiver: $expected")
      val (i,x) = out?(); if(verbose) println(s"receiver received ($i, $x)")
      assert(x == expected && x < N && i == expectedTag,
        s"Received ($i, $x); expected ($expectedTag, $expected)")
      expected += 1; expectedTag = 1-expectedTag
    }
    //println("receiver done")
  } 

  /** Another tagger.  This has the same OutPort in both branches. */
  def tagger2[T](l: ??[T], out: !![(Int, T)]) = thread("tagger2"){
    var x = l?()
    serve(
      out =!=> (0,x) ==> { x = l?() }
      | out =!=> (1,x) ==> { x = l?() }
    )
  }

  /** Run a single test.  If flag is true use tagger (repeated inport) else use
    * tagger2 (repeated outport). */
  def doTest(flag: Boolean) = {
    val l = 
      if(buffering > 1) new BuffChan[Int](buffering)
      else if(buffering == 1) new SingletonBuffChan[Int]
      else new SyncChan[Int]
    val out = new SyncChan[(Int,Int)]
    val t = if(flag) tagger(l,out) else tagger2(l,out)
    val system = t || sender(l) || receiver(out)
    run(system)
  }

  def main(args: Array[String]) = {
    var reps = 1000; var doTest1 = true
    var i = 0
    while(i < args.length) args(i) match{
      case "--reps" => reps = args(i+1).toInt; i += 1
      case "--buffering" => buffering = args(i+1).toInt; i += 2
      // case "--buffChan" => buffChan = true; i += 1
      case "--test2" => doTest1 = false; i += 1
      case "--verbose" => verbose = true; i += 1
    }

    for(i <- 0 until reps){ doTest(doTest1); if(i%10 == 0) print(".") }
    println()
  }

}

