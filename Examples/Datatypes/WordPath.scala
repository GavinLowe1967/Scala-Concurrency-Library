import scala.collection.mutable.{Set,ArrayBuffer}

/** A graph of words, corresponding to the dictionary file fName.  Two words
  * are neighbours if they differ by a single character. */
class WordGraph(fName: String) extends Graph[String]{
  /** A dictionary. */
  private val dict = Set[String]()

  // Initialise dict
  for(w <- scala.io.Source.fromFile(fName).getLines) dict += w

  def succs(w: String): List[String] = {
    var result = new ArrayBuffer[String]
    for(i <- 0 until w.length; c <- 'a' to 'z'; if c != w(i)){
      val w1 = w.patch(i,List(c),1) // replace ith character of w with c
      if(dict.contains(w1)) result += w1
    }
    result.toList
  }
}

// -------------------------------------------------------

object WordPath{
  def main(args: Array[String]) = {
    var conc = true
    val start = args(0); val target = args(1)
    val g = new WordGraph("knuth_words.txt")
    val searcher: GraphSearch[String] =
      if(conc) new ConcGraphSearch(g) else new SeqGraphSearch(g)
    searcher(start, (w: String) => w == target) match{
      case Some(p) => println(p.mkString(", "))
      case None => println("No solution found")
    }
  }
}
