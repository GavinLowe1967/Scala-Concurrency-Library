import io.threadcso._
import scala.language.postfixOps

/** Simulation of the Dining Philosophers example. */
object Phils{
  val N = 5 // Number of philosophers

  // Simulate basic actions
  def Eat = Thread.sleep(500)
  def Think = Thread.sleep(scala.util.Random.nextInt(900))
  def Pause = Thread.sleep(500)

  // Each philosopher will send "pick" and "drop" commands to her forks, which
  // we simulate using the following values.
  type Command = Boolean
  val Pick = true; val Drop = false
 
  /** A single philosopher. */
  private def phil(me: Int, left: ![Command], right: ![Command])
    = proc("Phil"+me){
    repeat{
      Think
      println(s"$me sits"); Pause
      left!Pick; println(s"$me picks up left fork"); Pause
      right!Pick; println(s"$me picks up right fork"); Pause
      println(s"$me eats"); Eat
      left!Drop; Pause; right!Drop; Pause
      println(s"$me leaves")
    }
  }

  /** A single fork. */
  private def fork(me: Int, left: ?[Command], right: ?[Command])
    = proc("Fork"+me){
    serve(
      left =?=> {
        x => assert(x == Pick); val y = left?; assert(y == Drop)
      }
      |
      right =?=> {
        x => assert(x == Pick); val y = right?; assert(y == Drop)
      }
    )
  }

  /** The complete system. */
  private def system = {
    // Channels to pick up and drop the forks:
    val philToLeftFork, philToRightFork = Array.fill(5)(OneOne[Command])
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
  def main(args : Array[String]) = { system() }
}

  
