import ox.scl._

object SleepingTutorTest{
  val reps = 1000 // Number of tests to run
  val iters = 1000 // Number of iterations per run

  // Events for logging
  abstract class LogEvent
  case object StudentArrives extends LogEvent
  case object TutorTeaches extends LogEvent
  case object TutorEnds extends LogEvent
  case object StudentLeaves extends LogEvent

  def student(me: Int, st: SleepingTutor, log: Log[LogEvent])
  = thread("Student"+me){
    for(_ <- 0 until iters){
      log.add(me, StudentArrives)
      st.arrive
      st.receiveTute
      log.add(me, StudentLeaves)
    }
  }

  def tutor(me: Int, st: SleepingTutor, log: Log[LogEvent]) = thread("Tutor"){
    for(_ <- 0 until iters){
      st.tutorWait
      log.add(me, TutorTeaches)
      log.add(me, TutorEnds)
      st.endTeach
    }
  }

  /** Check the log from a run. */
  def checkLog(events: Array[LogEvent]): Boolean = {
    // Number of students present; is the tutor teaching?
    var students = 0; var teaching = false
    assert(events.length == 6*iters)
    for(i <- 0 until events.length) events(i) match{
      case StudentArrives => students += 1; assert(students <= 2)
      case TutorTeaches =>
        // Requirement 1: the tutor starts to teach only after both students
        // have arrived.
        assert(!teaching)
        if(students != 2) return false else teaching = true
      case TutorEnds => assert(teaching); teaching = false
      case StudentLeaves =>
        // Requirement 2: Students leave only after the tutor ends the
        // tutorial.
        assert(students >= 1)
        if(teaching) return false else students -= 1
    }
    true
  }

  /** Run a single test. */
  def runTest(stType: String) = {
    val st =
      if(stType == "monitor") new SleepingTutorMonitor
      else if(stType == "SCLMonitor") new SleepingTutorSCLMonitor
      else if(stType == "semaphore") new SleepingTutorSemaphore
      else sys.error(stType)
    val log = new Log[LogEvent](3)
    run(tutor(0, st, log) || student(1, st, log) || student(2, st, log))
    if(!checkLog(log.get)) sys.exit
  }

  def main(args: Array[String]) = {
    // Parse command-line argument
    var stType = "monitor"
    if(args.nonEmpty) args(0) match{
      case "--SCLMonitor" => stType = "SCLMonitor"
      case "--semaphore" => stType = "semaphore"
    }

    for(i <- 0 until reps){ runTest(stType); if(i%10 == 0) print(".") }
    println
  }
}
