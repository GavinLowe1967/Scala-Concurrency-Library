// Template for the Sleeping Tutor practical

import ox.scl._

/** The trait for a Sleeping Tutor protocol. */
trait SleepingTutor{
  /** A tutor waits for students to arrive. */
  def tutorWait: Unit

  /** A student arrives and waits for the tutorial. */
  def arrive: Unit
  
  /** A student receives a tutorial. */
  def receiveTute: Unit

  /** A tutor ends the tutorial. */
  def endTeach: Unit
}

// =======================================================

import scala.util.Random

object SleepingTutorSimulation{
  private val st: SleepingTutor = new SleepingTutorMonitor

  def student(me: String) = thread("Student"+me){
    while(true){
      Thread.sleep(Random.nextInt(2000))
      println("Student "+me+" arrives")
      st.arrive
      println("Student "+me+" ready for tutorial")
      st.receiveTute
      println("Student "+me+" leaves")
    }
  }

  def tutor = thread("Tutor"){
    while(true){
      println("Tutor waiting for students")
      st.tutorWait
      println("Tutor starts to teach")
      Thread.sleep(1000)
      println("Tutor ends tutorial")
      st.endTeach
      Thread.sleep(1000)
    }
  }

  def system = tutor || student("Alice") || student("Bob")

  def main(args: Array[String]) = run(system)
}

// =======================================================

/* Note: I'll provide implementations using both a JVM monitor and an SCL
 * monitor.  Students are expected to provide just one: the latter is probably
 * better. */

/** An implementation using a JVM monitor. */
class SleepingTutorMonitor extends SleepingTutor{
  private var waiting = 0 // number of students waiting
  private var tutees = 0 // number of students being taught

  /** A tutor waits for students to arrive. */
  def tutorWait = synchronized{
    while(tutees < 2) wait()
  }

  /** A student arrives and waits for the tutorial. */
  def arrive = synchronized{
    if(waiting == 0){ // first student to arrive
      waiting += 1
      while(tutees < 1) wait()
      assert(waiting == 1 && tutees == 1)
      waiting -= 1; tutees += 1; notifyAll() // wake up tutor (and other student)
    }
    else{ // second student to arrive
      assert(tutees == 0)
      tutees += 1; notifyAll() // wake up (tutor and) other student
    }
  }

  /** A student receives a tutorial. */
  def receiveTute = synchronized{
    while(tutees > 0) wait() // wait for tute to finish
  }

  /** A tutor ends the tutorial. */
  def endTeach = synchronized{
    tutees = 0; notifyAll() // wake up the students receiving the tute
  }
}

// =======================================================

/** An implementation using an SCL monitor. */
class SleepingTutorSCLMonitor extends SleepingTutor{
  private var waiting = 0 // number of students waiting
  private var tutees = 0 // number of students being taught

  private val lock = new Lock

  /** Condition for tutor to wait on until two students have arrived. */
  private val tutorialStart = lock.newCondition

  /** Condition for tustudent to wait on, either until other student has
    * arrived, or until tutorial has finished. */
  private val studentWait = lock.newCondition

  /** A tutor waits for students to arrive. */
  def tutorWait = lock.mutex{
    tutorialStart.await(tutees == 2)  // (1)
  }

  /** A student arrives and waits for the tutorial. */
  def arrive = lock.mutex{
    if(waiting == 0){ // first student to arrive
      waiting += 1
      studentWait.await(tutees == 1) // (2)
      assert(waiting == 1 && tutees == 1)
      waiting = 0; tutees = 2; tutorialStart.signal() // wake up tutor at (1)
    }
    else{ // second student to arrive
      assert(tutees == 0)
      tutees += 1; studentWait.signal() // wake up other student at (2)
    }
  }

  /** A student receives a tutorial. */
  def receiveTute = lock.mutex{
    studentWait.await(tutees == 0) // wait for tute to finish (3)
  }

  /** A tutor ends the tutorial. */
  def endTeach = lock.mutex{
    tutees = 0; studentWait.signalAll() // wake up the students at (3)
  }
}

// =======================================================

/** An implementation using semaphores. */
class SleepingTutorSemaphore extends SleepingTutor{
  private var waiting = 0 // number of students waiting
  private var tutees = 0 // number of students being taught

  /** Semaphore to enfoce mutual exclusion on the shared variables. */
  private val mutex = new MutexSemaphore

  /** Semaphore to signal to the tutor that both students have arrived. */
  private val tutorialStart = new SignallingSemaphore

  /** Semaphore to signal to the students that the tutorial has ended. */
  private val tutorialEnd = new SignallingSemaphore

  /** Semaphore to signal to the first student that the tutorial is about to
    * start. */
  private val studentWait = new SignallingSemaphore

  // Note: it is tempting to try to combine the latter two semaphores into a
  // single one.  However, this can lead to a signal being mis-directed
  // towards a student on the next iteration of the protocol.

  /** A tutor waits for students to arrive. */
  def tutorWait = {
    tutorialStart.down  // wait for tutees (1)
    assert(tutees == 2)
    mutex.up // last student left mutex down
  }

  /** A student arrives and waits for the tutorial. */
  def arrive = {
    mutex.down
    if(waiting == 0){ // first student to arrive
      waiting = 1
      mutex.up; studentWait.down // wait for signal (2)
      assert(waiting == 1 && tutees == 1)
      waiting = 0; tutees = 2
      tutorialStart.up // wake up tutor at (1) (pass the baton)
    }
    else{ // second student to arrive
      assert(tutees == 0); tutees = 1
      studentWait.up // wake up other student at (2) (pass the baton)
    }
  }

  /** A student receives a tutorial. */
  def receiveTute = {
    tutorialEnd.down // wait for tute to finish (3)
    tutees -= 1
    if(tutees == 1) tutorialEnd.up // wake up partner at (3)
    else mutex.up
    //print("s")
  }

  /** A tutor ends the tutorial. */
  def endTeach = {
    mutex.down
    tutorialEnd.up // signal to student at (3)
    //print("t")
  }
}

