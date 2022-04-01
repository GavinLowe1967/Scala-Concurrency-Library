import ox.scl._

/** An object that calculates the value of exp, in parallel with continuing 
  * execution. */
class Future[A](exp: => A){
  /** Variable to hold the result. */
  private var result: A = _ 

  /** Is the result valid? */
  private var done = false

  /** Server process to calculate the result. */
  private def server = thread{
    synchronized{ result = exp; done = true; notifyAll }
  }

  fork(server)

  /** Get the value of exp. */
  def apply(): A = synchronized{ while(!done) wait(); result }

}
