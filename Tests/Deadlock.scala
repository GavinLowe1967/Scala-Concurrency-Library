package tests 

import ox.scl._

/** A test for naming of threads.  This should deadlock. */
object Deadlock{

  val c1, c2 = new SyncChan[Int]

  def p = thread("p"){ val x = c1?(); c2!x }

  def q = thread("q"){ val x = c2?(); c1!x }

  def main(args: Array[String]) = run(p || q)
}
