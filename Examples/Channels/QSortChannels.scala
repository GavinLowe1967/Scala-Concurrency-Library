import ox.scl._

object QSortChannels{
  // Quicksort: sort data received on in, and output on out
  def qSort(in: ?[Int], out: ![Int]): Computation = thread("QSort"){
    attempt{
      val pivot = in?()
      val toHigher, toLower, fromHigher, fromLower = new SyncChan[Int]

      // Main controller thread
      def controller = thread("Controller"){
	// Split data received on in between higher and lower, depending on
	// whether it is >= pivot or < pivot, respectively
	repeat{ val n = in?(); if(n < pivot) toLower!n else toHigher!n }
	// We've received the final input, so close the channels to the
	// sub-sorters.
	toHigher.close; toLower.close

	// Now output the results
	repeat{ out!(fromLower?()) }; out!pivot; repeat{ out!(fromHigher?()) }
	out.endOfStream
      }      

      // Put the system together, and run it
      run(
	controller || qSort(toHigher, fromHigher) || qSort(toLower, fromLower)
      )
    }{
      out.endOfStream // We've received no data, so just close
    }
  }
}

// =======================================================

import scala.util.Random

object QSortChannelsTest{
  // Number of elements to sort; range of input values.
  val N = 100; val Max = 100

  /** Run a single test.  Generate N random numbers.  Pass them in to a sorter.
    * Receive outputs.  Check result is as expected. */
  def doTest = {
    val xs = Array.fill(N)(Random.nextInt(Max))
    val ys = new Array[Int](N)
    val in, out = new SyncChan[Int]
    def sender = thread{ for(x <- xs) in!x; in.close }
    def receiver = thread{ var i = 0; repeat{ ys(i) = out?(); i += 1 } }
    run(sender || QSortChannels.qSort(in, out) || receiver)
    assert(xs.sorted.sameElements(ys))
  }

  def main(args : Array[String]) = {
    for(i <- 0 until 100){ doTest; if(i%10 == 0) print(".") }
    println
  }   
}
