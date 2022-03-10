import io.threadcso._
import scala.util.Random

/** Simulation of the Dining Philosophers example, using a timeout. */
object PhilsTO{
  val N = 5 // Number of philosophers

  // Simulate basic actions
  def Eat = Thread.sleep(500)
  def Think = Thread.sleep(500)
  def Pause = Thread.sleep(200)
  val waitTime = 1000000 // time to wait for second fork (in ns)

  // A channel to pick up a fork. 
  type PickChannel = channel.DeadlineManyOne[Unit]

  /** A single philosopher. */
  private def phil(me: Int, leftPick: PickChannel, rightPick: PickChannel, 
                   leftDrop: ![Unit], rightDrop: ![Unit])
    = proc("Phil"+me){
    repeat{
      Think
      println(s"$me sits"); Pause
      leftPick!(); println(s"$me picks up left fork"); Pause
      if(rightPick.writeBefore(waitTime)(())){
        println(s"$me picks up right fork"); Pause
        println(s"$me eats"); Eat
        leftDrop!(); Pause; rightDrop!(); Pause
        println(s"$me leaves")
      }
      else{
        println(s"$me fails to get right fork"); Pause
        // Pause for a random amount of time so that philosophers get out of
        // sync.
        leftDrop!(); Thread.sleep(200+Random.nextInt(200))
        println(s"$me leaves")
      }
    }
  }

  /** A single fork. */
  private def fork(me: Int, pick: PickChannel, drop: ?[Unit]) = proc("Fork"+me){
    repeat{ pick?(); drop?() }
  }

  /** The complete system. */
  private def system: PROC = {
    // Channels to pick up and drop the forks, indexed by forks' identities
    val pickChannels = Array.fill(N)(new PickChannel)
    val dropChannels = Array.fill(N)(ManyOne[Unit]())
    val allPhils = || ( 
      for (i <- 0 until N)
      yield phil(i, pickChannels(i), pickChannels((i+1)%N), 
                 dropChannels(i), dropChannels((i+1)%N))
    )
    val allForks = || ( 
      for (i <- 0 until N) yield fork(i, pickChannels(i), dropChannels(i)) 
    )
    allPhils || allForks
  }

  /** Run the system. */
  def main(args : Array[String]) = { system() }
}

  
