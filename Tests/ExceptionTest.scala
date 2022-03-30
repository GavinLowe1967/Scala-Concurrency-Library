package tests

import ox.scl._
import java.lang.Thread.sleep

/** A test of exception handling.
  * 
  * With no arguments, it tests the use of run.  A Stopped should be caught,
  * but then a NotImplementedError should be printed and the system halted.
  * 
  * With an argument, it tests the use of fork.  A Stopped should be printed
  * and the system halted.  */
object ExceptionTest{

  /** Throw a Stopped if flag, otherwise throw a NotImplementedError. */
  def t1(flag: Boolean) = thread{ 
    sleep(100); if(flag) throw(new Stopped) else ???; () 
  }

  def t2 = thread{ for (i <- 0 to 4){ println(i); sleep(50) } }

  def runTest = {
    // In following, Stopped exception should be caught. 
    attempt{ run(t1(true) || t2) }{ println("Stopped caught") }
    // In following, NotImplementedError should be printed leading to
    // termination
    run(t1(false) || t2) 
    // Following should not be reached.
    assert(false, "unreachable")
  }

  def forkTest = {
    fork(t1(true) || t2)
    run(t2)
    assert(false, "unreachable")
  }

  def main(args: Array[String]) = {
    if(args.nonEmpty) forkTest else runTest
  }
}
