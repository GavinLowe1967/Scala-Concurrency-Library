package tests

import ox.scl._
import java.lang.Thread.sleep

object ExceptionTest{
  /** Test that should throw a Stopped if flag, otherwise throw a
    * NotImplementedError. */
  def threadException(flag: Boolean) = {
    def t1 = thread{ sleep(100); if(flag) throw(new Stopped) else ???; () }
    def t2 = thread{ for (i <- 0 until 4){ println(i); sleep(50) } }
    run(t1 || t2)
  }

  def old = {
    // In following, Stopped exception should be caught. 
    attempt{ threadException(true) }{ println("Stopped caught") }
    // In following, NotImplementedError should be printed leading to
    // termination
    threadException(false)
    // Following should not be reached.
    assert(false, "unreachable")
  }

  def p = thread("p"){ ??? }

  def main(args: Array[String]) = {
    if(args.nonEmpty) fork(p) else run(p)
  }




}
