// The readers and writers problem
import ox.scl._

/** Trait for a readers and writers lock. */
trait ReadersWritersLock{
  def readerEnter: Unit
  def readerLeave: Unit
  def writerEnter: Unit
  def writerLeave: Unit
}


/** An implementation of the readers and writers problem, using a JVM
  * monitor. */
class ReadersWritersMonitor extends ReadersWritersLock{
  private var readers = 0; var writers = 0
  // Invariant: writers <= 1 && (readers == 0 || writers == 0)
    
  def readerEnter = synchronized{
    while(writers > 0) wait()
    readers += 1
  }

  def writerEnter = synchronized{
    while(writers > 0 || readers>0) wait()
    writers = 1
  }

  def readerLeave = synchronized{
    readers -= 1; notifyAll()
  }
      
  def writerLeave = synchronized{
    writers = 0; notifyAll()
  }
}

// -------------------------------------------------------

/** A readers-writers lock, that aims to prevent starvation of writers. 
  *
  * Based on Herlihy & Shavit, Section 8.3.2.
  */
class FairReadWriteLock extends ReadersWritersLock{
  private var readers = 0 // current # readers in the CS
  private var writers = 0 // is there a writer in the CS
  // Invariant: writers <= 1 && (readers == 0 || writers == 0)

  def readerEnter = synchronized{
    while(writers > 0) wait() // wait for writer to leave
    readers += 1 // record my entry
  }

  def readerLeave = synchronized{
    readers -= 1 // record my exit
    if(readers == 0) notifyAll() // signal to waiting writer
  }

  def writerEnter = synchronized{
    while(writers > 0) wait() // wait until no writer ahead of me
    writers = 1 // record that I'm trying; readers are blocked
    while(readers > 0) wait() // wait for readers to leave
  }

  def writerLeave = synchronized{
    writers = 0 // record my exit
    notifyAll() // signal to waiting reader or writer
  }
  
}

