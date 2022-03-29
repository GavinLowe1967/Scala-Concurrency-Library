import ox.scl._
import scala.util.Random

/** Simulation of the Dining Philosophers example. */
object PhilsLog{
  val N = 5 // Number of philosophers

  // Simulate basic actions
  def Eat = Thread.sleep(500)
  def Think = Thread.sleep(Random.nextInt(900))
  def Pause = Thread.sleep(500)

  // Each philosopher will send "pick" and "drop" commands to her forks, which
  // we simulate using the following values.
  type Command = Boolean
  val Pick = true; val Drop = false

  val log = new ox.scl.debug.Log[String](N)
 
  /** A single philosopher. */
  def phil(me: Int, left: ![Command], right: ![Command]) = thread("Phil"+me){
    repeat{
      Think
      log.add(me, s"$me sits"); Pause
      left!Pick; log.add(me, s"$me picks up left fork"); Pause
      right!Pick; log.add(me, s"$me picks up right fork"); Pause
      log.add(me, s"$me eats"); Eat
      left!Drop; Pause; right!Drop; Pause
      log.add(me, s"$me leaves")
      if(me == 0) print(".")
    }
  }

  /** A single fork. */
  def fork(me: Int, left: ?[Command], right: ?[Command]) = thread("Fork"+me){
    serve(
      left =?=> {
        x => assert(x == Pick); val y = left?(); assert(y == Drop)
      }
      |
      right =?=> {
        x => assert(x == Pick); val y = right?(); assert(y == Drop)
      }
    )
  }

  /** The complete system. */
  val system = {
    // Channels to pick up and drop the forks:
    val philToLeftFork, philToRightFork = Array.fill(5)(new SyncChan[Command])
    // philToLeftFork(i) is from Phil(i) to Fork(i);
    // philToRightFork(i) is from Phil(i) to Fork((i-1)%N)
    val allPhils = || ( 
      for (i <- 0 until N)
      yield phil(i, philToLeftFork(i), philToRightFork(i))
    )
    val allForks = || ( 
      for (i <- 0 until N) yield
        fork(i, philToRightFork((i+1)%N), philToLeftFork(i))
    )
    allPhils || allForks
  }

  /** Run the system. */
  def main(args : Array[String]) = {
    log.writeToFileOnShutdown("philsLog.txt")
    run(system)
  }
}

  
