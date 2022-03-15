import ox.scl._
import ox.scl.debug.Log
import scala.util.Random

/** An object to test the resource allocation server. */
object RATest{
  /* The following should really be specifiable via the command line... */
  var p = 5 // number of clients
  var iters = 1000 // # iterations by each client
  var numResources = 10 // # resources

  type Resource = Int
  type ClientId = Int

  // Events put into the log
  abstract class LogEvent
  case class GotResource(c: ClientId, r: Resource) extends LogEvent
  case class ReturnedResource(c: ClientId, r: Resource) extends LogEvent

  /** A client */
  def client(me: ClientId, resourceServer: RAServer, log: Log[LogEvent]) 
  = thread(s"client $me"){
    var got = new scala.collection.mutable.Queue[Resource]()
    val random = new Random(me+System.nanoTime)
    for(_ <- 0 until iters){
      if(random.nextInt(2) == 0){ // Acquire new resource
	resourceServer.requestResource(me) match{
          case Some(r) =>  log.add(me, GotResource(me, r)); got.enqueue(r)
          case None => {}  // try again
        }
      }
      else if(!got.isEmpty){     // Return resource
        // if(Random.nextInt(1000) == 0) assert(false)
	val r = got.dequeue
        log.add(me, ReturnedResource(me, r))
	resourceServer.returnResource(me, r)
      }
    }
    // Return resources held.  This reduces the number of deadlocks with
    // RAServer3.
    for(r <- got){
      log.add(me, ReturnedResource(me, r))
      resourceServer.returnResource(me, r)
    }
    // println(s"client $me done")
  }

  /** Check that events represents a valid log: if a GotResource event happens,
    * no thread is currently holding the resource.
    * @return true if the log is valid.  */
  def checkLog(events: Array[LogEvent]): Boolean = {
    // Array showing which resources are held
    val held = Array.fill(numResources)(false)
    var error = false; var i = 0
    while(i < events.size && !error){
      events(i) match{
        case GotResource(_, r) =>
          if(held(r)){ // error!
            println("Error found:")
            println(events.take(i+1).mkString("\n"))
            error = true
          }
          else held(r) = true
        case ReturnedResource(_, r) => held(r) = false
      }
      i += 1
    }
    !error
  }

  /** Run a single test. */
  def runTest(resourceServer: RAServer) = {
    //println
    val log = new Log[LogEvent](p)
    val clients = || (for (i <- 0 until p) yield client(i, resourceServer, log))
    try{ run(clients) } finally{ log.toFile("logFile") }
    resourceServer.shutdown
    if(!checkLog(log.get)) sys.exit
  }

  def main(args: Array[String]) = {
    // Parse command line arguments
    var rsType = 1 // Which resource server to use
    var i = 0; var buffered = false
    var reps = 1000 // # times to repeat
    while(i < args.length) args(i) match{
      case "-1" => rsType = 1; i += 1
      case "-2" => rsType = 2; i += 1
      case "-3" => rsType = 3; i += 1
      case "--buffered" => buffered = true; i += 1
      case "--iters" => iters = args(i+1).toInt; i += 2
      case "--reps" => reps = args(i+1).toInt; i += 2
      case arg => println("Unrecognised argument: "+arg); sys.exit
    }

    // If RSServer runs out of resources, it deadlocks; the following tries to
    // avoid this.
    if(rsType == 3){ p = 30; numResources = 90; iters = 15 }
    /* A better approach might be to prevent clients from requesting another
     * resource if they already hold two, and to choose numResources such that
     * p < numResources < 2*p.  This cannot deadlock, but will require threads
     * to wait. */

    for(r <- 0 until reps){
      val resourceServer =
        if(rsType == 1) new RAServer1(p, numResources, buffered)
        else if(rsType == 2) new RAServer2(numResources, buffered) 
        else{ assert(rsType == 3); new RAServer3(numResources, buffered) }
      runTest(resourceServer)
      if(r%10 == 0) print(".")
      // println
    }
    println
  }

}
