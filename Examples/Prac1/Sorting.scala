import ox.scl._

/** Object implementing various sorting networks. */
object Sorting{
  /** A single comparator, inputting on in0 and in1, and outputting on out0
    * (smaller value) and out1 (larger value). */
  def comparator(in0: ?[Int], in1: ?[Int], out0: ![Int], out1: ![Int])
    = thread("comparator"){
    var x0 = -1; var x1 = -1
    repeat{
      run(thread{ x0 = in0?() } || thread{ x1 = in1?() })
      run(thread{ out0!(x0 min x1) } || thread{ out1!(x0 max x1) })
    }
    in0.closeIn; in1.closeIn; out0.closeOut; out1.closeOut
  }

  /** A sorting network for four values. */
  def sort4(ins: List[?[Int]], outs: List[![Int]]): Computation = {
    require(ins.length == 4 && outs.length == 4)
    val c0, c1, c2, c3, c4, c5 = new SyncChan[Int]
    /* The network is as below
     * ins(0) --*-----c0--*--------- outs(0)
     *          |         |
     * ins(1) --|--*--c1--*--c4--*-- outs(1)
     *          |  |             |
     * ins(2) --*--|--c2--*--c5--*-- outs(2)
     *             |      |
     * ins(3) -----*--c3--*--------- outs(3)
     */
    comparator(ins(0), ins(2), c0, c2) ||
      comparator(ins(1), ins(3), c1, c3) ||
      comparator(c0, c1, outs(0), c4) ||
      comparator(c2, c3, c5, outs(3)) ||
      comparator(c4, c5, outs(1), outs(2))
  }

  /** Insert a value input on in into a sorted sequence input on ins. 
    * Pre: ins.length = n && outs.length = n+1, for some n >= 1.
    * If the values xs input on ins are sorted, and x is input on in, then a
    * sorted permutation of x::xs is output on ys. */
  def insert(ins: List[?[Int]], in: ?[Int], outs: List[![Int]]): Computation = {
    val n = ins.length; require(n >= 1 && outs.length == n+1)
    if(n == 1) comparator(ins(0), in, outs(0), outs(1))
    else{
      /* We use a circuit as below.
       * 
       * in ------*------------ outs(0)
       *          |     ____
       * ins(0) --*--c--| i |-- outs(1)
       *                | n |
       * ins(1)---------| s |-- outs(2)
       *                | e |
       * ...            | r |   ...
       *                | t |
       * ins(n-1)-------|   |-- outs(n-1)
       *                ----- 
       */
      val c = new SyncChan[Int]
      comparator(in, ins(0), outs(0), c) || insert(ins.tail, c, outs.tail)
    }
  }

  /** Insert a value input on in into a sorted sequence input on ins. 
    * Pre: ins.length = n && outs.length = n+1, for some n >= 1.
    * If the values xs input on ins are sorted, and x is input on in, then a
    * sorted permutation of x::xs is output on ys. */
  def insert1(ins: List[?[Int]], in: ?[Int], outs: List[![Int]]): Computation = {
    val n = ins.length; require(n >= 1 && outs.length == n+1)
    if(n == 1) comparator(ins(0), in, outs(0), outs(1))
    else if(n == 2){
      /* We use a circuit as below.
       * 
       * in ------*-------- outs(0)
       *          |    
       * ins(0) --*--c--*-- outs(1)
       *                |
       * ins(1)---------*-- outs(2)
       */
      val c = new SyncChan[Int]
      comparator(in, ins(0), outs(0), c) ||
        comparator(c, ins(1), outs(1), outs(2))
    }
    else{
      /* We use a circuit as below, splitting ins as before++(mid::after), and
       * outs as outs1++outs2
       * 
       *             -----
       *  in ---*-c0-| i |-- 
       *        |    | n |   o
       *  b  ---|----| s |-- u
       *  e     |    | e |   t
       *  f  ---|----| r |-- s
       *  o     |    | t |   1
       *  r  ---|----|   |--
       *  e     |    -----
       *        |    _____
       *  mid --*-c1-| i |--
       *             | n |   o
       *  a  --------| s |-- u
       *  f          | e |   t
       *  t  --------| r |-- s
       *  e          | t |   2
       *  r  --------|   |--
       *             -----
       */
      val halfN = n/2; val c0, c1 = new SyncChan[Int]
      val (before, mid::after) = ins.splitAt(halfN)
      val (outs1, outs2) = outs.splitAt(halfN+1)
      comparator(in, mid, c0, c1) || insert1(before, c0, outs1) ||
        insert1(after, c1, outs2)
    }
  }

  /** Insertion sort.
    * @param useLogInsert should the logarithmic version (insert1) be used?  */
  def insertionSort(useLogInsert: Boolean)(ins: List[?[Int]], outs: List[![Int]])
      : Computation = {
    val n = ins.length; require(n >= 2 && outs.length == n)
    if(n == 2) comparator(ins(0), ins(1), outs(0), outs(1))
    else{
      /* We use a circuit as below.
       *                          _____
       * ins(0) ------------------| i |--outs(0)
       *           _____          | n |
       * ins(1) ---| i |--mid(0)--| s |--outs(1)
       *           | S |          | e |
       *  ...   ---| o |-- ... ---| r |-- ...
       *           | r |          | t |
       * ins(n-1)--| t |-mid(n-2)-|   |-- outs(n-1)
       *           -----          -----
       */
      val mids = List.fill(n-1)(new SyncChan[Int])
      insertionSort(useLogInsert)(ins.tail, mids) ||
        (if(useLogInsert) insert1(mids, ins(0), outs)
         else insert(mids, ins(0), outs))
    }
  }

  /** Split xs into two lists, alternately.
    * @return the lists [xs(0), xs(2), xs(4),...] and [xs(1), xs(3), xs(5), ...]
    */
  private def split[T](xs: List[T]): (List[T], List[T]) = 
    if(xs.isEmpty) (List[T](), List[T]())
    else{ val (ys, zs) = split(xs.tail); (xs.head::zs, ys) }

  /** A merging network for 2^k values.  If the values received on
    * ins1 and ins2 are sorted, then their merger is output on outs. */
  def merge(k: Int)(ins1: List[?[Int]], ins2: List[?[Int]], outs: List[![Int]])
      : Computation = {
    val n = 1 << k; val halfN = n/2
    require(k >= 1 && ins1.length == halfN && ins2.length == halfN &&
              outs.length == n)
    if(k == 1) // n = 2
      comparator(ins1(0), ins2(0), outs(0), outs(1))
    else{
      val (ins11, ins12) = split(ins1); val (ins21, ins22) = split(ins2)
      val mids1, mids2 = List.fill(halfN)(new SyncChan[Int])
      merge(k-1)(ins11, ins22, mids1) || merge(k-1)(ins12, ins21, mids2) ||
        || (for(i <- 0 until halfN) yield
              comparator(mids1(i), mids2(i), outs(2*i), outs(2*i+1)))
    }
  }

  /** A bitonic sorting network for 2^k values. */
  def bitonicSort(k: Int)(ins: List[?[Int]], outs: List[![Int]]): Computation = {
    val n = 1 << k; val halfN = n/2
    require(ins.length == n && outs.length == n)
    if(k == 1) // n = 2
      comparator(ins(0), ins(1), outs(0), outs(1))
    else{
      val mids1, mids2 = List.fill(halfN)(new SyncChan[Int])
      bitonicSort(k-1)(ins.take(halfN), mids1) ||
        bitonicSort(k-1)(ins.drop(halfN), mids2) ||
        merge(k)(mids1, mids2, outs)
    }
  }
}

// -------------------------------------------------------

import scala.util.Random

/** Tests on the sorting networks. */
object SortingTest{
  /** A process that sends the values in xs on the corresponding out ports in
    * chans. */
  def sender(chans: List[![Int]], xs: Array[Int]) = thread("sender"){
    val n = chans.length; require(xs.length == n)
    for(i <- 0 until n) chans(i)!xs(i)
    chans.foreach(_.closeOut)
  }

  /** Receive values on chans and check that they equal a sorted version of
    * ys. */
  def receiver(chans: List[?[Int]], ys: Array[Int]) = thread("receiver"){
    val n = chans.length; require(ys.length == n)
    val ysSorted = ys.sorted; val zs = new Array[Int](n)
    for(i <- 0 until n) zs(i) = chans(i)?()
    assert(ysSorted.sameElements(zs),
           "Inputs: "+ys.mkString(", ")+"\nExpected: "+ysSorted.mkString(", ")+
             "\nReceived: "+zs.mkString(", "))
    chans.foreach(_.closeIn)
  }

  /** Run a single test on sorter.  
    * Pre: sorter is a sorting network for n values. */
  def sorterTest(n: Int, sorter: (List[?[Int]], List[![Int]]) => Computation) = {
    val ins, outs = List.fill(n)(new SyncChan[Int])
    val xs = Array.fill(n)(Random.nextInt(100))
    run(sender(ins, xs) || sorter(ins, outs) || receiver(outs, xs))
  }

  /** Run a single test on Sorting.merge(k). */
  def mergeTest(k: Int) = {
    val n = 1 << k; val halfN = n/2
    val ins1, ins2 = List.fill(halfN)(new SyncChan[Int])
    val outs = List.fill(n)(new SyncChan[Int])
    // Two lists of input data, each sorted
    val xs1, xs2 = Array.fill(halfN)(Random.nextInt(100)).sorted
    run (sender(ins1, xs1) || sender(ins2, xs2) ||
       Sorting.merge(k)(ins1, ins2, outs) || receiver(outs, xs1++xs2))
  }

  /** Run a single test on insert with n+1 inputs (n sorted, one to insert).
    * @param useLogInsert should the logarithmic version (insert1) be used? */
  def insertTest(useLogInsert: Boolean, n: Int) = {
    val ins = List.fill(n)(new SyncChan[Int]); val in = new SyncChan[Int]
    val outs = List.fill(n+1)(new SyncChan[Int])
    // Data to send: xs(0) is sent on in; xs.tail is sent on ins
    val xs = Array.fill(n+1)(Random.nextInt(100)).sorted
    run(sender(in::ins, xs) ||
       (if(useLogInsert) Sorting.insert1(ins, in, outs)
        else Sorting.insert(ins, in, outs)) ||
       receiver(outs, xs))
  }

  val usage = "Usage: scala SortingTest <options>\n"+
    "Options for tests:\n"+
    "* --sort4: the 4-input sorting network;\n"+
    "* --iSort: insertion sort;\n"+
    "* --bitonic: bitonic search;\n"+
    "* --merge: merge;\n"+
    "* --insert: insertion.\n"+
    "Parameters:\n"+
    "* --size: the number of inputs (with --iSort or --insert);\n"+
    "* --logSize: the log of the number of inputs (with --merge of --bitonic);\n"+
    "* --logInsert: the logarithmic sorting network (with --Sort or --insert)."

  /** The main function. */
  def main(args: Array[String]) = {
    // Flags to show which tests to perform.
    var doSort4Test = false; var doMergeTest = false; var doBitonicTest = false
    var doISortTest = false; var doInsert = false; var useLogInsert = false
    var size = 2; var logSize = 2; var i = 0
    // parse arguments
    while(i < args.length) args(i) match{
      case "--sort4" => doSort4Test = true; i += 1
      case "--merge" => doMergeTest = true; i += 1
      case "--bitonic" => doBitonicTest = true; i += 1
      case "--iSort" => doISortTest = true; i += 1
      case "--insert" => doInsert = true; i += 1
      case "--logInsert" => useLogInsert = true; i += 1
      case "--size" => size = args(i+1).toInt; i += 2
      case "--logSize" => logSize = args(i+1).toInt; i += 2
      case arg => println("Argument not recognised: "+arg+"\n"+usage); sys.exit
    }
    // Run the tests
    for(i <- 0 until 1000){
      if(doSort4Test) sorterTest(4, Sorting.sort4 _)
      if(doMergeTest) mergeTest(logSize)
      if(doInsert) insertTest(useLogInsert, size)
      if(doISortTest) sorterTest(size, Sorting.insertionSort(useLogInsert))
      if(doBitonicTest) sorterTest(1 << logSize, Sorting.bitonicSort(logSize) _)
      if(i%100 == 0) print(".")
    }
    println
  }
}
