import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, actorRef2Scala}

import scala.collection.mutable.ListBuffer
import scala.util.control.Exception


object Delegator{
  def props(rows:ListBuffer[ListBuffer[Char]]) = Props(new Delegator((rows)))
}

object Crate_Stack{
  def props(stack:ListBuffer[Char]) = Props(new Crate_Stack(stack))
}

class Delegator(rows:ListBuffer[ListBuffer[Char]]) extends Actor{
  var locks = new Array[Boolean](0)
  var stacks= new Array[ActorRef](0)
  override def receive: Receive = {
    case i:Int => print(s"received ${i}")
  }
  override def preStart(): Unit = {
    super.preStart()
    val num_stacks = rows.last.length
    locks = new Array[Boolean](num_stacks)
    stacks = new Array[ActorRef](num_stacks)
    init_cols();
  }
  def init_cols() = {
    try {

      //First, lets make things into arrays to get a fill on it
      val transposed = rows.map(row => {
        //Intialize a row-sized array of blanks
        val row_arr = Array.fill(locks.length)(' ')

        //For each index of the array, fill what we can
        for (i <- row.indices) {
          row_arr(i) = row(i)
        }
        row_arr.toList
      }).transpose

      //TODO: Send the transposed lists to col actors
      transposed.foreach(row => {
        val send_row = row.reverse
        val stack_id= send_row.head.toString.toInt
        val stack = context.actorOf(Crate_Stack.props(row.reverse))
        stacks(stack_id) = stack
      })


    } catch {
      case e: Exception => println(s"EXCEPTION: ${e}")
    }
  }
}

class Crate_Stack(stack:ListBuffer[Char]) extends Actor{
  override def receive: Receive = {
    case i:Int => println(i)
  }

  override def preStart(): Unit = {
    super.preStart()
    val id = stack.head
    stack.remove(0)
    println(s"ID: ${id}, remainder: ${stack} ")
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
