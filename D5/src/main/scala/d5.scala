import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, actorRef2Scala}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

case class Command(priority:Int,quantity:Int,source:Int,dest:Int)

case class Move_Order(crates:List[Char],delegator:ActorRef)

object Delegator{
  def props(rows:ListBuffer[ListBuffer[Char]],command_tuples:ListBuffer[(Int,Int,Int,Int)]) =
    Props(new Delegator(rows,command_tuples))
}

object Crate_Stack{
  def props(stack:ListBuffer[Char]) = Props(new Crate_Stack(stack))
}

class Delegator(rows:ListBuffer[ListBuffer[Char]],command_tuples:ListBuffer[(Int,Int,Int,Int)]) extends Actor{
  private var locks = new Array[Boolean](0)
  private var stacks= new Array[ActorRef](0)
  private var retry_queue = new mutable.PriorityQueue[Command]()(Ordering.by(compare))
  private var command_queue = new mutable.PriorityQueue[Command]()(Ordering.by(compare))

  private def compare(c:Command) = c.priority * -1

  private def check_lock(c:Command):Boolean = {
    val is_locked = !(locks(c.source-1) || locks(c.dest-1))
    println(s"Checking Lock: ${locks(c.source-1)} ${locks(c.dest-1)} --> ${is_locked}")
    is_locked
  }

  private def unlock(c:Command): Unit = {
    println(s"r_ack: unlocked ${c.source} && ${c.dest}")
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
    case "DISTRIBUTE" => distribute_commands()
  }
  override def preStart(): Unit = {
    super.preStart()
    val num_stacks = rows.last.length

    //setup arrays
    locks = new Array[Boolean](num_stacks)
    stacks = new Array[ActorRef](num_stacks)
    command_tuples.foreach(command => {
      command_queue.enqueue(Command(command._1,command._2,command._3,command._4))
    })

    //Setup col actors
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
      //println(s"TRANSPOSED: ${transposed}")
      transposed.foreach(row => {
        val send_row = row.reverse

        // grab id
        //println(s"processing ${send_row.head.toString} for ${send_row}")
        val stack_id = send_row.head.toString.toInt
        //println(s"${stack_id} processed")

        //flag lock as not_locked
        locks(stack_id - 1) = false
        //println(s"Initialized ${stack_id - 1} to ${locks(stack_id -1)}")

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
      //println("Distributing")
      //select command
      var command:Command = null
      if(retry_queue.nonEmpty){
        command = retry_queue.dequeue()
        println(s"addressing priority queue command: ${command}")
      } else if (command_queue.nonEmpty){
        println("Pulling from command queue")
        command = command_queue.dequeue()
      } else {
        println("DONE DELGATING")
        stacks.foreach(stack => stack ! PoisonPill)
        self ! PoisonPill
        return
      }

      //test command
      if(check_lock(command)){
        lock(command)
        println(s"Sending ${command}")
        stacks(command.source -1) ! command
      } else {
        println(s"assigning ${command} to priority queue")
        retry_queue.enqueue(command)
      }

      self ! "DISTRIBUTE"
    }

}

class Crate_Stack(stack:ListBuffer[Char]) extends Actor{
  var id: Int = 0
  var crates = ListBuffer[Char]()

  /**
   * Sources recieve commands, destinations receive crates
    * @param command
   */
  def r_Command(command: Command): Unit = {

    val move_crates = crates.takeRight(command.quantity)
    for (i <- (crates.length-1) to (crates.length - command.quantity)){
      println(s"REMOVIG INNDEX: ${i}")
      crates.remove(i)
    }
    println(s"${id} got command ${command}, moving ${move_crates} crates is: ${crates}")

    sender() ! Command(command.priority,command.quantity,command.source,command.dest)
  }

  override def receive: Receive = {
    case c:Command => r_Command(c)
  }

  override def preStart(): Unit = {
    super.preStart()
    id = stack.head.toString.toInt
    //remove the ID we just harvested
    stack.remove(0)

    //remove any blanks
    crates = stack.filter(c => c != ' ')
    println(s"ID: ${id}, remainder: ${crates} ")
  }
}


object d5{
  private def create_command_tuple(id:Int, command_string:String):(Int,Int,Int,Int)= {
    val  params = """\d""".r.findAllMatchIn(command_string).toList.map(i => i.toString().toInt)
    (id,params(0),params(1),params(2))
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
    println("READING Instructions")

    //reset the source....
    bufferedSource = scala.io.Source.fromFile(fileName)

    val commands = ListBuffer[(Int,Int,Int,Int)]()
    //Foreach line
    line_num = 1;
    for (line <- bufferedSource.getLines()) {
      //println(s"${line_num} --> ${line} row")
      commands += create_command_tuple(line_num,line)
      line_num = line_num + 1
    }

    val delegator = system.actorOf(Delegator.props(rows,commands),"Delegator")


    bufferedSource.close()
  }

}
