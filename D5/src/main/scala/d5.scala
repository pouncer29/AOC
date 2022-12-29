import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, actorRef2Scala}
import scala.collection.mutable.ListBuffer

case class Command(priority:Int,quantity:Int,source:Int,dest:Int)

object Delegator{
  def props(rows:ListBuffer[ListBuffer[Char]],commands:ListBuffer[Command]) =
    Props(new Delegator(rows,commands))
}

object Crate_Stack{
  def props(stack:ListBuffer[Char]) = Props(new Crate_Stack(stack))
}

class Delegator(rows:ListBuffer[ListBuffer[Char]],commads:ListBuffer[Command]) extends Actor{
  private var locks = new Array[Boolean](0)
  private var stacks= new Array[ActorRef](0)
  override def receive: Receive = {
    case i:Int => print(s"received ${i}")
  }
  override def preStart(): Unit = {
    super.preStart()
    val num_stacks = rows.last.length
    locks = new Array[Boolean](num_stacks)
    stacks = new Array[ActorRef](num_stacks + 1)
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

      //Send the transposed lists to col actors
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

  def create_command(id:Int,command_string:String):Command = {
    val  params = """\d""".r.findAllMatchIn(command_string).toList.map(i => i.toString().toInt)
    Command(id,params(0),params(1),params(2))
  }
  def main(args:Array[String]): Unit = {

    // Start the system
    val system = ActorSystem("SYS")


    //Parse crates
    var fileName = "./D5/d5.test.crates"
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
      //println(s"appended ${line_num} --> ${crates.toList} row")
      line_num = line_num + 1
    }


    //Parse instructions
    fileName = "./D5/d5.test.instructions"
    println("READING CRATES")

    //reset the source....
    bufferedSource = scala.io.Source.fromFile(fileName)

    val commands = ListBuffer[Command]()
    //Foreach line
    line_num = 1;
    for (line <- bufferedSource.getLines()) {
      //println(s"${line_num} --> ${line} row")
      commands += create_command(line_num,line)
      line_num = line_num + 1
    }

    val delegator = system.actorOf(Delegator.props(rows,commands))


    bufferedSource.close()
  }

}
