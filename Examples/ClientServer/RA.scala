// Simple client-server resource allocation mechanism

import ox.scl._

trait RAServer{
  /** Client identities. */
  type ClientId = Int

  /** Resource identities. */
  type Resource = Int

  /** Request a resource. */
  def requestResource(me: ClientId): Option[Resource]

  /** Return a resource. */
  def returnResource(me: ClientId, r: Resource)

  /** Shut down the server. */
  def shutdown

  def mkChan[A](buffChan: Boolean) = 
    if(buffChan) new BuffChan[A](5) else new SyncChan[A]
}

// -------------------------------------------------------

/** A resource server. 
  * This version assumes the number of clients is known initially. 
  * @param clients the number of clients.
  * @param numResources the number of resources.  */
class RAServer1(clients: Int, numResources: Int, buffChan: Boolean)
    extends RAServer{

  /* Channel for requesting a resource. */
  private val acquireRequestChan = mkChan[ClientId](buffChan)
  /* Channels for optionally returning a resouce, indexed by client IDs. */
  private val acquireReplyChan = 
    Array.fill(clients)(mkChan[Option[Resource]](buffChan))
  /* Channel for returning a resource. */
  private val returnChan = mkChan[Resource](buffChan)
  /* Channel for shutting down the server. */
  private val shutdownChan = mkChan[Unit](buffChan)

  private def server = thread{
    // Record whether resource i is available in free(i)
    val free = Array.fill(numResources)(true)

    serve(
      acquireRequestChan =?=> { c => 
	// Find free resource
	var r = 0
	while(r < numResources && !free(r)) r += 1
	if(r == numResources) acquireReplyChan(c)!None
        else{  // Pass resource r back to client c
	  free(r) = false; acquireReplyChan(c)!Some(r)
        }
      }
      | returnChan =?=> { r => free(r) = true }
      | shutdownChan =?=> { _ =>
          acquireRequestChan.close; returnChan.close; shutdownChan.close
      }
    )
  }

  // Fork off the server
  server.fork

  /** Request a resource. */
  def requestResource(me: ClientId): Option[Resource] = {
    acquireRequestChan!me  // send request
    acquireReplyChan(me)?() // wait for response
  }

  /** Return a resource. */
  def returnResource(me: ClientId, r: Resource) = returnChan!r

  /** Shut down the server. */
  def shutdown = shutdownChan!()
}

// -------------------------------------------------------

import scala.util.Random

/** An object simulating use of the resourceServer. */
object RA{
  val p = 5 // number of clients
  val numResources = 10 // number of resources

  // Create Resource Server object
  val resourceServer = new RAServer3(numResources)
  // new RAServer1(p, numResources)

  type Resource = resourceServer.Resource
  type ClientId = resourceServer.ClientId

  /** A client */
  def client(me: ClientId) = thread{
    var got = new scala.collection.mutable.Queue[Resource]()
    repeat{
      if(Random.nextInt(2) == 0){
	// Acquire new resource
	val or = resourceServer.requestResource(me)
        if(or.nonEmpty){
	  val r = or.get; got.enqueue(r)
	  println("Client "+me+" got resource "+r)
        }
        // otherwise go back round the loop
      }
      else if(got.nonEmpty){
	// Return resource
	val r = got.dequeue
	resourceServer.returnResource(me, r)
	println("Client "+me+" returned resource "+r)
      }
      Thread.sleep(100)
    }
  }

  // Put the system together
  def clients = || (for (c <- 0 until p) yield client(c))

  def main(args : Array[String]) = clients() 

}

// -------------------------------------------------------

/** A resource server. 
  * This version assumes the number of clients is not known initially.
  * @param numResources the number of resources.  */
class RAServer2(numResources: Int) extends RAServer{
  private type ReplyChan = Chan[Option[Resource]]
  /* Channel for requesting a resource. */
  private val acquireRequestChan = ManyOne[ReplyChan]
  /* Channel for returning a resource. */
  private val returnChan = ManyOne[Resource]
  /* Channel for shutting down the server. */
  private val shutdownChan = ManyOne[Unit]

  private def server = thread{
    // Record whether resource i is available in free(i)
    val free = Array.fill(numResources)(true)

    serve(
      acquireRequestChan =?=> { replyChan => 
	// Find free resource
	var r = 0
	while(r < numResources && !free(r)) r += 1
	if(r == numResources) replyChan!None
        else{  // Pass resource r back to client 
	  free(r) = false; replyChan!Some(r)
        }
      }
      | returnChan =?=> { r => free(r) = true }
      | shutdownChan =?=> { _ =>
          acquireRequestChan.close; returnChan.close; shutdownChan.close
      }
    )
  }

  // Fork off the server
  server.fork

  /** Request a resource. */
  def requestResource(me: ClientId): Option[Resource] = {
    val replyChan = OneOne[Option[Resource]]
    acquireRequestChan!replyChan  // send request
    replyChan?() // wait for response
  }

  /** Return a resource. */
  def returnResource(me: ClientId, r: Resource) = returnChan!r

  /** Shut down the server. */
  def shutdown = shutdownChan!()
}


// -------------------------------------------------------

/** A resource server. 
  * This version buffers requests until they can be served.
  * @param numResources the number of resources.  */
class RAServer3(numResources: Int) extends RAServer{
  private type ReplyChan = Chan[Option[Resource]]
  /* Channel for requesting a resource. */
  private val acquireRequestChan = ManyOne[ReplyChan]
  /* Channel for returning a resource. */
  private val returnChan = ManyOne[Resource]
  /* Channel for shutting down the server. */
  private val shutdownChan = ManyOne[Unit]

  private def server = thread{
    // Record whether resource i is available in free(i)
    val free = Array.fill(numResources)(true)
    // Reply channels for requests that cannot be served immediately.
    val pending = new scala.collection.mutable.Queue[ReplyChan]
    // Invariant: if pending is non-empty, then all entries in free are false.

    serve(
      acquireRequestChan =?=> { replyChan => 
	// Find free resource
	var r = 0
	while(r < numResources && !free(r)) r += 1
	if(r == numResources) pending.enqueue(replyChan) // client has to wait
        else{  // Pass resource r back to client 
	  free(r) = false; replyChan!Some(r)
        }
      }
      | returnChan =?=> { r =>
          if(pending.nonEmpty)
            pending.dequeue!Some(r) // allocate r to blocked client
          else free(r) = true
      }
      | shutdownChan =?=> { _ =>
          acquireRequestChan.close; returnChan.close; shutdownChan.close
      }
    )
  }

  // Fork off the server
  server.fork

  /** Request a resource. 
    * In fact, this version never returns None. */
  def requestResource(me: ClientId): Option[Resource] = {
    val replyChan = OneOne[Option[Resource]]
    acquireRequestChan!replyChan  // send request
    replyChan?() // wait for response
  }

  /** Return a resource. */
  def returnResource(me: ClientId, r: Resource) = returnChan!r

  /** Shut down the server. */
  def shutdown = shutdownChan!()
}
