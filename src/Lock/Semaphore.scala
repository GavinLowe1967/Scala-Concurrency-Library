package ox.scl.lock

/** A binary semaphore.
  * @param isUp is the semaphore initially in the "up" state? */
class Semaphore(private var isUp: Boolean){
  /** Put the semaphore up.  Unblock a waiting down, if there is one. */
  def up = synchronized{
    require(!isUp); isUp = true; notify()
  }

  /** Wait for the semaphore to be up, and put it down. */
  def down = synchronized{
    while(!isUp) wait()
    isUp = false
  }
}

/** A binary semaphore that can be used for mutual exclusion: its initial
  * state is up. */
class MutexSemaphore extends Semaphore(true)

/** A binary semaphore that can be used for signalling: its initial state is
  * down. */
class SignallingSemaphore extends Semaphore(false)
