import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, actorRef2Scala}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

case class Command(priority:Int,quantity:Int,source:Int,dest:Int)

object Delegator{
  def props(rows:ListBuffer[ListBuffer[Char]],commands:ListBuffer[Command]) =
    Props(new Delegator(rows,commands))
}

object Crate_Stack{
  def props(stack:ListBuffer[Char]) = Props(new Crate_Stack(stack))
}

class Delegator(rows:ListBuffer[ListBuffer[Char]],commands:ListBuffer[Command]) extends Actor{
  private var locks = new Array[Boolean](0)
  private var stacks= new Array[ActorRef](0)
  private var retry_queue = new mutable.PriorityQueue[Command]()(Ordering.by(compare))

  private def compare(c:Command) = c.priority

  private def check_lock(c:Command):Boolean = {
    //println(s"Checking Lock: ${locks(c.source-1)} ${locks(c.dest-1)}")
    !(locks(c.source-1) && locks(c.dest-1))
  }

  private def unlock(c:Command): Unit = {
    println(s"unlocked ${c.source} && ${c.dest}")
    locks(c.source - 1) = false
    locks(c.dest - 1) = false
  }

  private def lock(c:Command): Unit = {
    println(s"locked ${c.source} && ${c.dest}")
    locks(c.source - 1) = true
    locks(c.dest - 1) = true
  }

  override def receive: Receive = {
    case ack:Command => unlock(ack)
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

      //Send the transposed lists to col actors
      println(s"TRANSPOSED: ${transposed}")
      transposed.foreach(row => {
        val send_row = row.reverse

        // grab id
        //println(s"processing ${send_row.head.toString} for ${send_row}")
        val stack_id = send_row.head.toString.toInt
        //println(s"${stack_id} processed")

        //flag lock as not_locked
        locks(stack_id - 1) = false
        println(s"Initialized ${stack_id - 1} to ${locks(stack_id -1)}")

        //Create lock
        val stack = context.actorOf(Crate_Stack.props(row.reverse))
        stacks(stack_id - 1) = stack // add to
      })
    } catch {
      case e: Exception => println(s"EXCEPTION: ${e}")
    }

    distribute_commands()
  }
  private def distribute_commands(): Unit = {
    println("Distributing")
    while (commands.nonEmpty){

      //select command
      var command:Command = null
      if(retry_queue.nonEmpty){
        //println(s"addressing priority queue command: ${command}")
        command = retry_queue.dequeue()
      } else {
        //println("Pulling from command queue")
        command = commands.head
        commands.remove(0)
      }

      //test command
      if(check_lock(command)){
        //println(s"Sending ${Command}")
        lock(command)
        stacks(command.source) ! command
      } else {
        //println(s"sending ${command} to priority queue")
        retry_queue.enqueue(command)
      }

    }
  }

}

class Crate_Stack(stack:ListBuffer[Char]) extends Actor{
  var id: Int = 0
  def r_Command(command: Command): Unit = {
    println(s"${id} got command ${command}")
    sender() ! command
  }

  override def receive: Receive = {
    case c:Command => r_Command(c)
  }

  override def preStart(): Unit = {
    super.preStart()
    id = stack.head.toString.toInt
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
      println(s"appended ${line_num} --> ${crates.toList} row")
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
      println(s"${line_num} --> ${line} row")
      commands += create_command(line_num,line)
      line_num = line_num + 1
    }

    val delegator = system.actorOf(Delegator.props(rows,commands))


    bufferedSource.close()
  }

}
