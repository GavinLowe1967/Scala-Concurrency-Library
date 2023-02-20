package tests

import ox.scl._

/** Test of an alt where the channel expressions are re-evaluated on each
  * branch. */
object AltChannelTest{
  val c1, c2 = new SyncChan[Int]

  val iters = 20
  val reps = 20
  val start2 = 100

  /** An alt that alternates between channels c1 and c2 using a conditional
    * channel expression. */
  def altThread = thread{
    var flag = false; var exp1 = 0; var exp2 = start2
    serve(
      (if(flag) c1 else c2) =?=> { x =>
        if(flag){ assert(x == exp1); exp1 += 1 } 
        else{ assert(x == exp2); exp2 += 1 }
        flag = !flag
      }
    )
    assert(exp1 == iters && exp2 == iters+start2)
  }

  def sender(c: ![Int], start: Int) = thread{
    for(i <- 0 until iters) c!(i+start)
    c.endOfStream
  }

  def main(args: Array[String]) = {
    for(i <- 0 until reps){
      if(i > 0){ c1.reopen; c2.reopen }
      run(sender(c1,0) || sender(c2,start2) || altThread)
      print(".")
    }
    println
  }


}
