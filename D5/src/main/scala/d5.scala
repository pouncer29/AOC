import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, actorRef2Scala}

import scala.collection.mutable.ListBuffer
import scala.util.control.Exception


object Delegator{
  def props(rows:ListBuffer[ListBuffer[Char]]) = Props(new Delegator((rows)))
}

class Delegator(rows:ListBuffer[ListBuffer[Char]]) extends Actor{
  var locks = new Array[Boolean](0)
  var cols = new Array[ActorRef](0)
  override def receive: Receive = {
    case i:Int => print(s"received ${i}")
  }
  override def preStart(): Unit = {
    super.preStart()
    locks = new Array[Boolean](rows.last.length)
    init_cols();
  }

  def init_cols() = {
    try {

      //First, lets make things into arrays to get a fill on it
      val as_arrays = rows.map(row => {
        //Intialize a row-sized array of blanks
        val row_arr = Array.fill(locks.length)(' ')

        //For each index of the array, fill what we can
        for (i <- row.indices) {
          println(s"Adding ${row(i)} at ${i}")
          row_arr(i) = row(i)
        }
        row_arr.toList
      })
      println(s"Created_Arrays ${as_arrays}")
      println(s"Transposed is: ${as_arrays.transpose}")
    } catch {
      case e: Exception => println(s"EXCEPTION: ${e}")
    }
  }
}

object d5{

  def main(args:Array[String]): Unit = {

    // Start the system
    val system = ActorSystem("SYS")


    //Parse crates
    val fileName = "./D5/d5.test.crates"
    println("READING CRATES")

    //reset the source....
    var bufferedSource = scala.io.Source.fromFile(fileName)

    //Foreach line
    var line_num = 1;
    val rows = ListBuffer[ListBuffer[Char]]()
    for (line <- bufferedSource.getLines()) {
      val crates: ListBuffer[Char] = new ListBuffer[Char]
      for (i <- 1 until line.length by 4){
        crates.append(line.charAt(i))
      }
      rows.append(crates)
      println(s"appended ${line_num} --> ${crates.toList} row")
      line_num = line_num + 1
    }

    val delegator = system.actorOf(Delegator.props(rows))
    delegator ! 1
    println(s"Last Row is ${rows.last}")


    bufferedSource.close()
  }

}
