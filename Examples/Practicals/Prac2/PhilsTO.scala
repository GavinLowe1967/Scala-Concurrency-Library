import ox.scl._
import scala.util.Random

/** Simulation of the Dining Philosophers example, using a timeout. */
object PhilsTO{
  val N = 5 // Number of philosophers

  // Simulate basic actions
  def Eat = Thread.sleep(500)
  def Think = Thread.sleep(500)
  def Pause = Thread.sleep(200)
  val waitTime = 400 // time to wait for second fork (in ms)  

  // Each philosopher will send "pick" and "drop" commands to her forks, which
  // we simulate using the following values.
  type Command = Boolean
  val Pick = true; val Drop = false

  /** A single philosopher. */
  private def phil(me: Int, left: ![Command], right: ![Command])
  = thread("Phil"+me){
    repeat{
      Think
      println(s"$me sits"); Pause
      left!Pick; println(s"$me picks up left fork"); Pause
      if(right.sendWithin(waitTime)(Pick)){
        println(s"$me picks up right fork"); Pause
        println(s"$me eats"); Eat
        left!Drop; Pause; right!Drop; Pause
        println(s"$me leaves")
      }
      else{
        println(s"$me fails to get right fork"); Pause
        // Pause for a random amount of time so that philosophers get out of
        // sync.  A larger amount of randomness here means they get out of
        // sync faster.
        left!Drop; Thread.sleep(200+Random.nextInt(800))
        println(s"$me leaves")
      }
    }
  }

  /** A single fork. */
  private def fork(me: Int, left: ?[Command], right: ?[Command])
  = thread("Fork"+me){
    serve(
      left =?=> { x => assert(x == Pick); val y = left?(); assert(y == Drop) }
      |
      right =?=> { x => assert(x == Pick); val y = right?(); assert(y == Drop) }
    )
  }

  /** The complete system. */
  private def system: ThreadGroup = {
    // Channels to pick up and drop the forks, indexed by forks' identities
    val philToLeftFork, philToRightFork = Array.fill(N)(new SyncChan[Command])
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
  def main(args : Array[String]) = { run(system) }
}

  
