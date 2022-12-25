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
class Compartmentalizer(evaluator:ActorRef) extends Actor{

  private def prioritize(items: String): List[Int] = {
    val as_priority:ListBuffer[Int] = new ListBuffer[Int]
    items.chars().forEach(char => {
      val priority:Int = char.toInt //Ascii value of char
      var appendMe:Int = 0

      //Handle CAPS then Non Caps
      if(priority >= 65 && priority <= 90)
        appendMe = priority - 38
       else if (priority >= 97 && priority <= 122)
        appendMe = priority - 96

      as_priority.append(appendMe)

      println(s"TO_INT takes ${char} and makes it ${appendMe}");
      as_priority.append(priority)
    })

    as_priority.toList
  }
  private def r_Sack(sack: Sack): Unit ={
    val sack_split = sack.comp_1.splitAt((sack.comp_1.length/2) - 1) //split in the middle.

    val compartment_1 = prioritize(sack_split._1) //comp 1/2
    val compartment_2 = prioritize(sack_split._2) //comp 2/2

    val comparitor= context.actorOf(Comparitor.props(evaluator)) //create evaluator to compare the halves
    comparitor ! Priority_Sack(compartment_1,compartment_2,sack.sack_id)
    self ! PoisonPill
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

  def r_Sack(sack: Priority_Sack): Unit = {

  }

  override def receive: Receive = {
    case s:Priority_Sack => r_Sack(s)
  }
}

/**
 * 4. sum priorities of duplicates per line
 * 5. sum those sums?
 */
class Accumulator extends Actor{
  override def receive: Receive = ???
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

    //Accumulator
    val accumulator = system.actorOf(Props[Accumulator]())

    //Parse the input
    val fileName = "./D3/d3.txt"
    val bufferedSource = scala.io.Source.fromFile(fileName)

    //Foreach line
    var line_num = 0;
    for (line <- bufferedSource.getLines()) {
      //println(s"Line ${line_num} --> ${line}")
      val comparitor = system.actorOf(Compartmentalizer.props(accumulator))
      comparitor ! Sack(line,"",line_num)
      line_num = line_num + 1;

    }

    bufferedSource.close()
  }
}