package ox.scl.lock

/** Lock objects. */
class Lock{
  import java.lang.Thread

  /** How many times is this locked? */
  private var locked = 0

  /** The thread locking the object. */
  private[lock] var locker: Thread = null

  /** Acquire the lock. */
  def acquire() = synchronized{
    val thisThread = Thread.currentThread
    if(thisThread == locker) locked += 1 // re-entry
    else{
      while(locked > 0) wait()
      locked = 1; locker = thisThread
    }
  }

  /** Acquire the lock `count` times. */
  private[lock] def acquireMultiple(count: Int) = synchronized{
    val thisThread = Thread.currentThread; assert(locker != thisThread)
    while(locked > 0) wait()
    locked = count; locker = thisThread
  }

  /** Release the lock. */
  def release() = synchronized{
    assert(locker == Thread.currentThread,
      s"Lock held by $locker but unlocked by ${Thread.currentThread}")
    locked -= 1
    if(locked == 0){ locker = null; notify() }
    // Otherwise this thread still holds the lock
  }

  /** Release the lock as many times as it is held; return that number. */
  private[lock] def releaseAll(): Int = synchronized{
    assert(locker == Thread.currentThread,
      s"Lock held by $locker but unlocked by ${Thread.currentThread}")
    val numLocked = locked; locked = 0; locker = null; notify(); numLocked
  }

  /** Execute `comp` under mutual exclusion for this lock. */
  def mutex[A](comp: => A): A = {
    acquire(); try{comp} finally{ if(locker == Thread.currentThread) release() }
    // Note: the check in the finally clause is in case this thread received
    // an interrupt while waiting on a condition, so not holding the lock.
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
    @volatile var ready = false // has this thread received a signal?
  }

  /** Check that the current thread holds the lock. */
  @inline private def checkThread = 
    assert(Thread.currentThread == lock.locker, 
      s"Action on Condition by thread ${Thread.currentThread}, but the "+
        s"corresponding lock is held by ${lock.locker}")

  /** Queue holding ThreadInfos for waiting threads.
    * This is accessed only by a thread holding lock. */
  private val waiters = new scala.collection.mutable.Queue[ThreadInfo]()

  /** Wait on this condition. */
  def await(): Unit = {
    checkThread
    var wasInterrupted = false
    // record that I'm waiting
    val myInfo = new ThreadInfo; waiters.enqueue(myInfo) 
    val numLocked = lock.releaseAll()               // release the lock
    while(!myInfo.ready){
      LockSupport.park()                            // wait to be woken
      if(Thread.interrupted){ myInfo.ready = true; wasInterrupted = true }
    }                              // reacquire the lock
    if(wasInterrupted)
      throw new InterruptedException
      // Thread.currentThread.interrupt()     // reassert interrupt status 
    lock.acquireMultiple(numLocked)      
  }

  /** Wait until `test` is true, rechecking when a signal is received. */
  def await(test: => Boolean): Unit = while(!test) await()

  import java.lang.System.nanoTime

  /** Wait on this condition for at most nanos nanoseconds.  Return true if a
    * signal was received. */
  def awaitNanos(nanos: Long): Boolean = {
    val deadline = nanoTime+nanos // time to timeout
    checkThread
    var wasInterrupted = false
    // record that I'm waiting
    val myInfo = new ThreadInfo; waiters.enqueue(myInfo) 
    val numLocked = lock.releaseAll()                // release the lock
    var remaining = deadline-nanoTime
    while(!myInfo.ready && remaining > 0){
      LockSupport.parkNanos(remaining)               // wait to be woken
      if(Thread.interrupted){ myInfo.ready = true; wasInterrupted = true }
      remaining = deadline-nanoTime
    }
    // Note: if the deadline is reached, but another thread signals to this
    // thread before it reacquires the lock, then we treat the signal as
    // having been received.
    if(wasInterrupted)
      throw new InterruptedException
      //Thread.currentThread.interrupt     // reassert interrupt status 
    lock.acquireMultiple(numLocked)                  // reacquire the lock
    if(!myInfo.ready) waiters -= myInfo 
    myInfo.ready
  }

  /** Wait on this condition until test becomes true or for at most nanos
    * nanoseconds.  Return true if test is true. */
  def awaitNanos(nanos: Long, test: => Boolean): Boolean = {
    val deadline = nanoTime+nanos // time to timeout
    var testTrue = test; var remaining = nanos
    while(!testTrue && remaining > 0){ 
      awaitNanos(remaining); testTrue = test
      if(!testTrue) remaining = deadline-nanoTime
    }
    testTrue
  }

  /** Signal to the first waiting thread. */
  def signal(): Unit = {
    checkThread
    if(waiters.nonEmpty){
      val threadInfo = waiters.dequeue()
      if(!threadInfo.ready){
        threadInfo.ready = true; LockSupport.unpark(threadInfo.thread)
      }
      else signal() // try next one; that thread was interrupted or timed out
    }      
  }

  /** Signal to all waiting threads. */
  def signalAll() = {
    checkThread
    while(waiters.nonEmpty){
      val threadInfo = waiters.dequeue()
      threadInfo.ready = true; LockSupport.unpark(threadInfo.thread)
    }      
  }
}
