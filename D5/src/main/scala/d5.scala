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

/**
 * The Delegator class simply delegates tasks to each of the 9 cranes that manage crate stacks.
 * It maintains 2 queues the "command_queue" of all commands in order, and the "retry_queue" which
 * holds any move-order that couldn't be executed.
 * @param rows This is a list of the stacks (List of List of chars)
 * @param command_tuples The original set of commands, in order
 */
class Delegator(rows:ListBuffer[ListBuffer[Char]],command_tuples:ListBuffer[(Int,Int,Int,Int)]) extends Actor{
  val logger:Logger = LoggerFactory.getLogger(Delegator.getClass)

  /**
   * Holds whether or not a stack "isLocked", that is, has a command being executed on it
   */
  private var locks = new Array[Boolean](0)

  /**
   * Holds the ActorRef of each of the stack actors (cranes)
   */
  private var stacks= new Array[ActorRef](0)

  /**
   * The Tails of each stack (last item) as this is what the question demands
   */
  private var tails= new Array[Char](0)

  /**
   * The # of tail reports we should expect to receive
   */
  private var tail_count = (rows.length - 1)

  /**
   * The queue of commands to retry
   */
  private var retry_queue = new mutable.PriorityQueue[Command]()(Ordering.by(compare))

  /**
   * The queue of commands as they were read from the instructions file
   */
  private var command_queue = new mutable.PriorityQueue[Command]()(Ordering.by(compare))

  /**
   * Because commands are ordered from 0 - N, and vanilla comparitor says that
   * higher # --> Greater priority, we negate the priority to ensure 0 is the
   * greatest priority
   * @param c A "Move Command" who's priority field we need
   * @return the negated command priority
   */
  private def compare(c:Command) = c.priority * -1

  /**
   * Checks the locks relevant to a passed command to see if it can be carried out
   * @param c A "Move Command" with locks we need to checked
   * @return True if either stack is locked
   */
  private def check_lock(c:Command):Boolean = {
    //Offset 1 for instruction naming convention starting stacks at 1
    val is_locked = !(locks(c.source-1) || locks(c.dest-1))
    println(s"Checking Lock: ${locks(c.source-1)} ${locks(c.dest-1)} --> ${is_locked}")
    is_locked
  }

  /**
   * Unlock the stack that sent us the unlock message by setting it's lock to false
   * @param stack - the stack which is now free
   */
  private def unlock(stack:Unlock): Unit = {
    println(s"r_ack: unlocked ${stack.id}")
    locks(stack.id -1) = false
  }

  /**
   * Lock the stacks that pertain to the command
   * @param c - the command which we are going to execute, and who's
   *          stacks we must lock
   */
  private def lock(c:Command): Unit = {
    println(s"locked ${c.source} && ${c.dest}")
    locks(c.source - 1) = true
    locks(c.dest - 1) = true
  }

  /**
   * receives a tail report, records it, and if we have received all tails, we die.
   * @param tr - A Tail repport from a stack actor that has been asked to report tails
   */
  def r_TailReport(tr: Tail_Report): Unit = {
    tails(tr.id -1) = tr.tail
    println(s"RECEIVED TAIL COUNT: ${tr}: ${tail_count}")
    if(tail_count == 0){
      println(s"TAILS: ${tails.toList}")
      self ! PoisonPill
    }
    tail_count = tail_count - 1
  }

  /**
   * Handles coordinating received messages
   * @return
   */
   def receive: Receive = {
    case ack:Unlock => unlock(ack)
    case tail:Tail_Report => r_TailReport(tail)
    case "DISTRIBUTE" => distribute_commands()
  }

  /**
   * Sets up the delegator with locks, stacks, and tails as appropriately
   * sized arrays and enqueues each of the commands before
   * setting up the stack actors (col actors)
   */
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
    val move_crates = crates.takeRight(command.quantity)//.reverse For Part 1
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
  /**
   * Creates a command tuple and assigns it a priority
   *
   * @param priority       - integer priority of the command where lower # --> Higher priority
   * @param command_string - the "command" from the file in the form "move <quant> from <src> to <dest>
   * @return a tuple in the form <priority,quant,src,dest>
   */
  private def create_command_tuple(id: Int, command_string: String): (Int, Int, Int, Int) = {
    val params = command_string.split(" ")
    val tuple = (id, params(1).toInt, params(3).toInt, params(5).toInt)
    //println(s"Created ${tuple}")
    tuple

  }

  def main(args: Array[String]): Unit = {

    // Start the system
    val system = ActorSystem("SYS")


    //Parse crates
    var fileName = "./D5/d5.crates"
    println("READING CRATES")

    //reset the source....
    var bufferedSource = scala.io.Source.fromFile(fileName)

    // Basically transpose d5.crates as a list of chars indexed by
    // crate ID [1-9]
    var line_num = 1;
    val rows = ListBuffer[ListBuffer[Char]]()
    for (line <- bufferedSource.getLines()) {
      val crates: ListBuffer[Char] = new ListBuffer[Char]
      //Each crate is 4 characters apart (i.e. [A] [B] [C])
      for (i <- 1 until line.length by 4) {
        //append the char that represents the crate stack for the
        crates.append(line.charAt(i))
      }
      rows.append(crates)
      //println(s"appended ${line_num} --> ${crates.toList} row")
      line_num = line_num + 1
    }


    //Parse instructions
    fileName = "./D5/d5.instructions"
    println("READING Instructions")

    //reset the source to instructions
    bufferedSource = scala.io.Source.fromFile(fileName)

    // Convert the command text to a tuple of ints (PRIORITY (ORDER),QUANT,SRC,DST)
    val commands = ListBuffer[(Int, Int, Int, Int)]()
    //Foreach line
    line_num = 1; //We start at 1 because this is how it is known in the instructions. Each line is a stack
    for (line <- bufferedSource.getLines()) {
      //println(s"${line_num} --> ${line} row")
      commands += create_command_tuple(line_num, line)
      line_num = line_num + 1
    }

    //Create the Delegator
    val delegator = system.actorOf(Delegator.props(rows, commands), "Delegator")


    bufferedSource.close()
  }
}
