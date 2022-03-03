package ox.scl.lock

/** Lock objects. */
class Lock{
  import java.lang.Thread

  /** How many times is this locked? */
  private var locked = 0

  /** The thread locking the object. */
  private[lock] var locker: Thread = null

  /** Acquire the lock. */
  def acquire = synchronized{
    val thisThread = Thread.currentThread
    if(thisThread == locker) locked += 1 // re-entry
    else{
      while(locked > 0) wait()
      locked = 1; locker = thisThread
    }
  }

  /** Release the lock. */
  def release = synchronized{
    assert(locker == Thread.currentThread,
      s"Lock held by $locker but unlocked by ${Thread.currentThread}")
    locked -= 1
    if(locked == 0){ locker = null; notify() }
    // Otherwise this thread still holds the lock
  }

  /** Execute `comp` under mutual exclusion for this lock. */
  def mutex[A](comp: => A): A = {
    acquire; try{comp} finally{release}
  } 

  /** Get a condition associated with this lock. */
  def newCondition = new Condition(this)
}

// =======================================================

import java.util.concurrent.locks.LockSupport

/** A condition associated with `lock`. */
class Condition(lock: Lock){
  /** Information about waiting threads. */
  private class ThreadInfo{
    val thread = Thread.currentThread //the waiting thread
    var ready = false // has this thread received a signal?
  }

  /** Check that the current thread holds the lock. */
  @inline private def checkThread = 
    assert(Thread.currentThread == lock.locker, 
      s"Action on Condition by thread ${Thread.currentThread}, but the "+
        "corresponding lock is held by ${lock.locker}")

  /** Queue holding ThreadInfos for waiting threads.
    * This is accessed only by a thread holding lock. */
  private val waiters = new scala.collection.mutable.Queue[ThreadInfo]()

  /** Wait on this condition. */
  def await(): Unit = {
    checkThread
    var wasInterrupted = false
    // record that I'm waiting
    val myInfo = new ThreadInfo; waiters.enqueue(myInfo) 
    lock.release                                    // release the lock
    while(!myInfo.ready){
      LockSupport.park()                            // wait to be woken
      if(Thread.interrupted){ myInfo.ready = true; wasInterrupted = true }
    }
    lock.acquire                                    // reacquire the lock
    if(wasInterrupted)
      Thread.currentThread.interrupt     // reassert interrupt status 
  }

  /** Wait until `test` is true, rechecking when a signal is received. */
  def await(test: => Boolean): Unit = while(!test) await()

  /** Signal to the first waiting thread. */
  def signal() = {
    checkThread
    if(waiters.nonEmpty){
      val threadInfo = waiters.dequeue
      threadInfo.ready = true; LockSupport.unpark(threadInfo.thread)
    }      
  }

  /** Signal to all waiting threads. */
  def signalAll() = {
    checkThread
    while(waiters.nonEmpty){
      val threadInfo = waiters.dequeue
      threadInfo.ready = true; LockSupport.unpark(threadInfo.thread)
    }      
  }
}
