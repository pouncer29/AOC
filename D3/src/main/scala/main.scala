import akka.actor.Actor
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

case class Sack(comp_1:String,comp_2:String,sack_id:Int)
case class Priority_Sack(comp_1:List[Int],comp_2:List[Int],sack_id:Int)
object Compartmentalizer{
  def props(accumulator:ActorRef):Props = Props(new Compartmentalizer(accumulator));
}

/**
 * 1. split into 2 compartments (half of each string)
 * 2. convert to priority [a-z] -> [1-26] [A-Z] -> [27-52]
 */
class Compartmentalizer(accumulator:ActorRef) extends Actor{

  private def prioritize(items: String): List[Int] = {
    var as_priority: Set[Int] = Set()
    items.chars().forEach(char => {
      val priority:Int = char //Ascii value of char
      var appendMe:Int = 0

      //Handle CAPS then Non Caps
      //A = 65, a = 97
      if(priority >= 65 && priority <= 90)
        appendMe = priority - 38
       else if (priority >= 97 && priority <= 122)
        appendMe = priority - 96


      //as_priority.append(appendMe)
      as_priority = as_priority + appendMe
    })

    as_priority.toList
  }
  private def r_Sack(sack: Sack): Unit ={

    val sack_split = sack.comp_1.splitAt(sack.comp_1.length/2) //split in the middle.

    val compartment_1 = prioritize(sack_split._1) //comp 1/2
    val compartment_2 = prioritize(sack_split._2) //comp 2/2

   // create comparitor
    val comparitor = context.actorOf(Comparitor.props(accumulator));

    //accumulator ! Priority_Sack(compartment_1,compartment_2,sack.sack_id)
    comparitor ! Priority_Sack(compartment_1,compartment_2,sack.sack_id)
  }

  override def receive: Receive = {
    case s:Sack => r_Sack(s)
  }
}

object Comparitor{
  def props(accumulator: ActorRef):Props = Props(new Comparitor(accumulator));
}

/**
 * 3. Find common priorities between rucksacks,
 */
class Comparitor(accumulator:ActorRef) extends Actor{

  private def r_Sack(sack: Priority_Sack): Unit = {
    val similarity = sack.comp_1.filter(item => sack.comp_2.contains(item))
    //val as_chars = similarity.map(priority => priority.toChar)
    println(s"Similarity of ${sack.sack_id} = ${similarity}")

    accumulator ! similarity.sum
  }

  override def receive: Receive = {
    case s:Priority_Sack => r_Sack(s)
  }
}


object Accumulator{
  def props(num_sacks:Int):Props = Props(new Accumulator(num_sacks))
}

/**
 * 4. sum priorities of duplicates per line
 * 5. sum those sums?
 */
class Accumulator(num_sacks:Int) extends Actor{

  private var compartment_1: ListBuffer[Int] = new ListBuffer[Int]()
  private var compartment_2: ListBuffer[Int] = new ListBuffer[Int]()
  private val ids:ListBuffer[Int] = ListBuffer[Int]()
  private var total: ListBuffer[Int] = new ListBuffer[Int]()

  private def r_Sack(sack: Priority_Sack):Unit = {

    if(sack.comp_1.nonEmpty)
      compartment_1 = compartment_1 ++ sack.comp_1

    if(sack.comp_2.nonEmpty)
      compartment_2 = compartment_2 ++ sack.comp_2

    ids.append(sack.sack_id)

    if( ids.length == num_sacks ) {
      println(s"BEGINNING THE TALLY: ${compartment_2.length} and ${compartment_2.length}")

      val similar = compartment_1.filter(item => compartment_2.contains(item))
      println(s"sum ${similar.sum}")
    } else {
      println(s"Handled sack ${sack.sack_id}")
    }
  }

  def r_Sum(sub_total: Int): Unit = {
    println(s"received: ${sub_total}")
    total.append(sub_total)
    if(total.length == num_sacks)
      println(s"SUM IS: ${total.sum} of ${total}")
  }

  override def receive: Receive = {
    case s:Priority_Sack => r_Sack(s)
    case sum:Int => r_Sum(sum)
  }
}
/**
 * TODO
 * 1. split into 2 compartments (half of each string)
 * 2. convert to priority [a-z] -> [1-26] [A-Z] -> [27-52]
 * 3. Find common priorities between rucksacks,
 * 4. sum priorities of duplicates per line
 * 5. sum those sums?
 */

object main{
  def main(args: Array[String]): Unit = {

    // Start the system
    val system = ActorSystem("SYS")


    //Parse the input
    val fileName = "./D3/d3.txt"
    var bufferedSource = scala.io.Source.fromFile(fileName)

    println("READING LINES")
    var lines = bufferedSource.getLines()
    //Accumulator
    val accumulator = system.actorOf(Accumulator.props(300))
    //bufferedSource.close();

    //reset the source....
    //bufferedSource = scala.io.Source.fromFile(fileName)

    //Foreach line
    var line_num = 0;
    for (line <- bufferedSource.getLines()) {
      //println(s"Line ${line_num} --> ${line}")
      val comparitor = system.actorOf(Compartmentalizer.props(accumulator))
      comparitor ! Sack(line,"",line_num)
      line_num = line_num + 1
    }


    bufferedSource.close()
  }
}