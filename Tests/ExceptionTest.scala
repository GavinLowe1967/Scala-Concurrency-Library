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
  def t1(flag: Boolean) = thread("t1"){ 
    sleep(100); if(flag) throw(new Stopped) else ???; () 
  }

  def t2(id: Int) = thread(s"t2($id)"){ 
    for (i <- 0 to 4){ println(s"$id: $i"); sleep(50) } 
  }

  def runTest = {
    // In following, Stopped exception should be caught after t2(1) finishes.
    attempt{ run(t1(true) || t2(1)) }{ println("Stopped caught") }

    // In following, t2(3) should be interrupted, and the NotImplementedError
    // should be caught; t2(2) should continue to run.
    fork(t2(2))
    try{ run(t1(false) || t2(3)) }
    catch{ case _: NotImplementedError => println("NotImplementedError caught") }
  }

  def forkTest = {
    // Both t2(1) and t2(2) should be halted, and the program should halt. 
    try{
      fork(t1(true) || t2(1))
      run(t2(2))
    }
    catch{ case _: Throwable => println("This shouldn't happen") }
    assert(false, "unreachable")
  }

  def main(args: Array[String]) = {
    if(args.nonEmpty) forkTest else runTest
  }
}
