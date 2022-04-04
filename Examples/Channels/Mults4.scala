import ox.scl._

/** Program to illustrate channel communications.  The effect is to print
  * multiples of four. */
object Mults4{
  /** Send the naturals on out. */
  def nats(out: ![Int]) = thread{ 
    var n = 0; while(true){ out!n; n += 1 }
  }

  /** Repeatedly input on in, sending alternate elements on out. */
  def alts[A](in: ?[A], out: ![A]) = thread{ 
    while(true){ out!(in?()); in?() } 
  }

  /** Repeatedly input on `in`, printing the values received. */
  def console[A](in: ?[A]) = thread{ while(true) println(in?()) }

  /* Each channel xk passes multiples of k. */
  val x1, x2, x4 = new SyncChan[Int]

  /** Complete system. */
  def system = nats(x1) || alts(x1, x2) || alts(x2, x4) || console(x4)

  def main(args: Array[String]) = run(system)
}

