import ox.scl._

/** Trait for a readers and writers lock. */
trait ReadersWritersLock{
  def readerEnter: Unit
  def readerLeave: Unit
  def writerEnter: Unit
  def writerLeave: Unit
}

/** A readers-writers lock, using semaphores. */
class ReadersWritersLockSemaphore extends ReadersWritersLock{
  /** Number of readers in the CS. */
  private var readers = 0 // current # readers in the CS

  /** Number of readers waiting to enter. */
  private var readersWaiting = 0

  /** Number of writers in the CS. */
  private var writers = 0 

  /** Number of writers waiting. */
  private var writersWaiting = 0

  /** Semaphore to protect shared variables. */
  private val mutex = new MutexSemaphore

  /** Semaphore to signal that reader can enter.
    * Indicates that writers == 0. */
  private val readerEntry = new SignallingSemaphore

  /** Semaphore to signal that writer can enter.
    * Indicates that writers == 0 && readers == 0. */
  private val writerEntry = new SignallingSemaphore

  def readerEnter = {
    mutex.down
    if(writers == 0){ // go straight in
      readers += 1; mutex.up
    }
    else{ // have to wait
      readersWaiting += 1; mutex.up
      readerEntry.down // wait to be released
      assert(writers == 0)
      readersWaiting -= 1; readers += 1
      if(readersWaiting > 0) readerEntry.up // signal to next reader
      else mutex.up
    }
  }

  def readerLeave = {
    mutex.down
    readers -= 1
    assert(readersWaiting == 0)
    if(readers == 0 && writersWaiting > 0) writerEntry.up // signal to writer
    else mutex.up
  }

  def writerEnter = {
    mutex.down
    if(readers == 0 && writers == 0){ // go straight in
      writers += 1; mutex.up
    }
    else{ // have to wait
      writersWaiting += 1; mutex.up
      writerEntry.down // wait to be released
      writersWaiting -= 1
      assert(readers == 0 && writers == 0); writers = 1
      mutex.up
    }
  }

  def writerLeave = {
    mutex.down
    writers = 0
    if(readersWaiting > 0) readerEntry.up
    else if(writersWaiting > 0) writerEntry.up
    else mutex.up
  }
}

// -------------------------------------------------------

/** A fair readers-writers lock, using semaphores. */
class FairReadersWritersLockSemaphore extends ReadersWritersLock{
  /** Number of readers in the CS. */
  private var readers = 0 // current # readers in the CS

  /** Number of readers waiting to enter. */
  private var readersWaiting = 0

  /** Number of writers in the CS. */
  private var writers = 0 

  /** Number of writers waiting. */
  private var writersWaiting = 0

  /** Semaphore to protect shared variables. */
  private val mutex = new MutexSemaphore

  /** Semaphore to signal that reader can enter.
    * Indicates that writers == 0 and that it is the readers' turn. */
  private val readerEntry = new SignallingSemaphore

  /** Semaphore to signal that writer can enter.
    * Indicates that writers == 0 && readers == 0. */
  private val writerEntry = new SignallingSemaphore

  def readerEnter = {
    mutex.down
    if(writers == 0 && writersWaiting == 0){ // go straight in
      readers += 1; mutex.up
    }
    else{ // have to wait
      readersWaiting += 1; mutex.up
      readerEntry.down // wait to be released
      assert(writers == 0)
      readersWaiting -= 1; readers += 1
      if(readersWaiting > 0) readerEntry.up // signal to next reader
      else mutex.up
    }
  }

  def readerLeave = {
    mutex.down
    readers -= 1
    if(readers == 0 && writersWaiting > 0) writerEntry.up // signal to writer
    else mutex.up
  }

  def writerEnter = {
    mutex.down
    if(readers == 0 && writers == 0){ // go straight in
      writers += 1; mutex.up
    }
    else{ // have to wait
      writersWaiting += 1; mutex.up
      writerEntry.down // wait to be released
      writersWaiting -= 1
      assert(readers == 0 && writers == 0); writers = 1
      mutex.up
    }
  }

  def writerLeave = {
    mutex.down
    writers = 0
    if(readersWaiting > 0) readerEntry.up
    else if(writersWaiting > 0) writerEntry.up
    else mutex.up 
  }

}
