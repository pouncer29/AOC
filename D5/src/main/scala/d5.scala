import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, actorRef2Scala}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer



case class Command(priority:Int,quantity:Int,source:Int,dest:Int)
case class Unlock(id:Int)
case class Move_Order(crates:List[Char],delegator:ActorRef)

case class Show_Stack()

case class Tail_Report(id:Int,tail:Char)

object Delegator{
  def props(rows:ListBuffer[ListBuffer[Char]],command_tuples:ListBuffer[(Int,Int,Int,Int)]) =
    Props(new Delegator(rows,command_tuples))
}

object Crate_Stack{
  def props(stack:ListBuffer[Char],stacks:Array[ActorRef]) = Props(new Crate_Stack(stack,stacks))
}

class Delegator(rows:ListBuffer[ListBuffer[Char]],command_tuples:ListBuffer[(Int,Int,Int,Int)]) extends Actor{
  val logger:Logger = LoggerFactory.getLogger(Delegator.getClass)
  private var locks = new Array[Boolean](0)
  private var stacks= new Array[ActorRef](0)
  private var tails= new Array[Char](0)
  private var tail_count = (rows.length - 1)
  private var retry_queue = new mutable.PriorityQueue[Command]()(Ordering.by(compare))
  private var command_queue = new mutable.PriorityQueue[Command]()(Ordering.by(compare))

  private def compare(c:Command) = c.priority * -1

  private def check_lock(c:Command):Boolean = {
    val is_locked = !(locks(c.source-1) || locks(c.dest-1))
    println(s"Checking Lock: ${locks(c.source-1)} ${locks(c.dest-1)} --> ${is_locked}")
    is_locked
  }

  private def unlock(c:Unlock): Unit = {
    println(s"r_ack: unlocked ${c.id}")
    locks(c.id -1) = false
  }

  private def lock(c:Command): Unit = {
    println(s"locked ${c.source} && ${c.dest}")
    locks(c.source - 1) = true
    locks(c.dest - 1) = true
  }

  def r_TailReport(tr: Tail_Report): Unit = {
    tails(tr.id -1) = tr.tail
    println(s"RECEIVED TAIL COUNT: ${tr}: ${tail_count}")
    if(tail_count == 0){
      println(s"TAILS: ${tails.toList}")
      self ! PoisonPill
    }
    tail_count = tail_count - 1
  }

   def receive: Receive = {
    case ack:Unlock => unlock(ack)
    case tail:Tail_Report => r_TailReport(tail)
    case "DISTRIBUTE" => distribute_commands()
  }
  override def preStart(): Unit = {
    super.preStart()
    val num_stacks = rows.last.length

    //setup arrays
    locks = new Array[Boolean](num_stacks)
    stacks = new Array[ActorRef](num_stacks)
    tails = new Array[Char](num_stacks)
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
        val stack = context.actorOf(Crate_Stack.props(row.reverse,stacks))
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
        //println(s"addressing priority queue command: ${command}")
      } else if (command_queue.nonEmpty){
        //println("Pulling from command queue")
        command = command_queue.dequeue()
      } else {
        println("DONE DELGATING")
        stacks.foreach(stack => stack ! Show_Stack())
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

class Crate_Stack(stack:ListBuffer[Char],stacks:Array[ActorRef]) extends Actor{
  val logger:Logger = LoggerFactory.getLogger(classOf[Crate_Stack])
  var id: Int = 0
  var crates = ListBuffer[Char]()

  /**
   * Sources recieve commands, destinations receive crates
    * @param command
   */
  def r_Command(command: Command): Unit = {

    //grab the crates
    val move_crates = crates.takeRight(command.quantity).reverse
    val lower_range = crates.length - command.quantity
    val remove_count = command.quantity
    println(s"${id} -> REMOVING BOUNDS: ${lower_range} to ${crates.length - 1} for ${crates} on ${command}")
    //(s"${id} -> REMOVING BOUNDS: ${lower_range} to ${crates.length - 1} for ${crates} on ${command}")


    //REmove them
    crates.remove(lower_range,remove_count)

    println(s"${id} got command ${command}, moving ${move_crates} crates is: ${crates}")

    //send the crates to the dest!
    stacks(command.dest - 1) ! Move_Order(move_crates.toList,sender())

    //unlock yourself
    sender() ! Unlock(id)
  }

  /**
   * Moves the crates ordered
   * @param move_order
   */
  def r_MoveOrder(move_order: Move_Order): Unit = {

    //Move the crates
    //println(s"${id} Crates were: ${crates}")
    try{
      crates.appendAll(move_order.crates);
    } catch {
      case e:Exception => println(s"${e} - Move Order")
    }
    //println(s"${id} Crates are now: ${crates}")

    //Unlock the stack
    move_order.delegator ! Unlock(id)
  }

  def r_ShowStack(): Unit = {
    println(s"${id}->tail:${crates.last} ${crates}")
    sender() ! Tail_Report(id,crates.last)
    self ! PoisonPill
  }

  override def receive: Receive = {
    case c:Command => r_Command(c)
    case mo:Move_Order => r_MoveOrder(mo)
    case _:Show_Stack => r_ShowStack()
  }

  override def preStart(): Unit = {
    super.preStart()
    id = stack.head.toString.toInt
    //remove the ID we just harvested
    stack.remove(0)

    //remove any blanks
    crates = stack.filter(c => c != ' ')
    //println(s"ID: ${id}, remainder: ${crates} ")
  }
}


object d5{
  private def create_command_tuple(id:Int, command_string:String):(Int,Int,Int,Int)= {
    val params = command_string.split(" ")
    val tuple = (id,params(1).toInt,params(3).toInt,params(5).toInt)
    //println(s"Created ${tuple}")
    tuple

  }
  def main(args:Array[String]): Unit = {

    // Start the system
    val system = ActorSystem("SYS")


    //Parse crates
    var fileName = "./D5/d5.crates"
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
    fileName = "./D5/d5.instructions"
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
