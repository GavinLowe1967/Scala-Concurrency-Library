import ox.scl._

/** Object to sort a using parallel recursive Quick Sort. */
class QSort(a: Array[Int]){
  /** Partition the segment a[l..r).
    * Permute a[l..r) and return k s.t. a[l..k) <= a(k) < a[k+1..r).  */
  private def partition(l: Int, r: Int): Int = {
    require(l < r)
    val x = a(l); var i = l+1; var j = r 
    // Inv: a[l..i) <= x < a[j..r)
    while(i < j){
      while(i < j && a(i) <= x) i += 1
      while(i < j && a(j-1) > x) j -= 1
      if(i < j){ // a(i) > x, a(j-1) <= x, so swap
        val t = a(i); a(i) = a(j-1); a(j-1) = t; i += 1; j -= 1
      }
    }
    // swap pivot into position
    a(l) = a(i-1); a(i-1) = x; i-1
  }


  /** Sort a[l..r) using recursive parallelism. */
  private def qsort(l: Int, r: Int): ThreadGroup = thread{
    if(l+1 < r){
      val m = partition(l, r)
      run(qsort(l, m) || qsort(m+1, r))
    }
  }

  /** Sort a using recursive parallelism. */
  def apply() = run(qsort(0, a.length))

  /** Maximum size of segment to sort recursively. */
  val parLimit = a.length / 30

  /** Sort a[l..r) using recursive parallelism for segments of size at least
    * parLimit. */
  private def qsortLimit(l: Int, r: Int): ThreadGroup = thread{
    if(l+1 < r){
      val m = partition(l, r)
      if(r-l >= parLimit) run(qsortLimit(l, m) || qsortLimit(m+1, r))
      else{ qsortLimit(l, m); qsortLimit(m+1, r) }
    }
  }

  /** Sort a using recursive parallelism for segments of size at least
    * parLimit. */
  def withLimit = run(qsortLimit(0, a.length))

  /** Sort a[l..r) sequentially. */
  private def qsortSeq(l: Int, r: Int): Unit = {
    if(l+1 < r){
      val m = partition(l, r)
      qsortSeq(l, m); qsortSeq(m+1, r) 
    }
  }

  /** Sort a sequentially. */
  def seq = qsortSeq(0, a.length)
}

// -------------------------------------------------------

import scala.util.Random

object QSortTest{

  def main(args: Array[String]) = {
    var reps = 1000; var size = 20; var i = 0; var testing = false
    while(i < args.length) args(i) match{
      case "--size" => size = args(i+1).toInt; i += 2
      case "--reps" => reps = args(i+1).toInt; i += 2
      case "--testing" => testing = true; i += 1
    }

    // Check a is sorted, if testing
    @inline def check(a: Array[Int]) = 
      if(testing) for(j <- 0 until size-1) assert(a(j) <= a(j+1))

    if(size <= 10000){
      println("Full recursive parallel.")
      val start = System.nanoTime
      for(i <- 0 until reps){
        val a = Array.fill(size)(Random.nextInt(100))
        new QSort(a)()
        check(a)
        if(i%100 == 0) print(".")
      }
      val duration = System.nanoTime-start
      println("Time taken: "+(duration/1000000)+"ms")
    }

    println("Recursive parallel with limit.")
    val start1 = System.nanoTime
    for(i <- 0 until reps){
      val a = Array.fill(size)(Random.nextInt(100))
      new QSort(a).withLimit
      check(a)
      if(i%100 == 0) print(".")
    }
    val duration1 = System.nanoTime-start1
    println("Time taken: "+(duration1/1000000)+"ms")

    println("Sequential")
    val start2 = System.nanoTime
    for(i <- 0 until reps){
      val a = Array.fill(size)(Random.nextInt(100))
      new QSort(a).seq
      check(a)
      if(i%100 == 0) print(".")
    }
    val duration2 = System.nanoTime-start2
    println("Time taken: "+(duration2/1000000)+"ms")
  }
}
