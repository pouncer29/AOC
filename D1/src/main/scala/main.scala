import akka.actor.Actor
import akka.actor.{ActorRef, ActorSystem,Props}
import scala.collection.mutable.ListBuffer

case class Sum_List(elf_id:Int,cal_list:List[Int],forward_to:ActorRef)
case class Summation(elf_id:Int,total:Int)

class Summation_Actor extends Actor{
  override def receive: Receive = {
    case l:Sum_List => r_Sum_List(l)
  }

  //Forwards the summation as a total
  private def r_Sum_List(do_sum:Sum_List):Unit= {
    val my_sum = Summation(do_sum.elf_id, do_sum.cal_list.sum)
    do_sum.forward_to ! my_sum
  }
}

class Eval_Actor extends Actor {
  private var largest: Summation = null;

  override def receive: Receive = {
    case s: Summation => r_Summation(s)
    case "done" => r_Done();
  }

  //Set the new largest if it was received
  private def r_Summation(summation: Summation): Unit = {
    if (largest == null)
      this.largest = summation
    else if (this.largest.total <= summation.total)
      this.largest = summation;
  }

  private def r_Done(): Unit = {
    println("LARGEST WAS: %d from %d".format(largest.total,largest.elf_id))
  }
}


object CalorieCounter{
  def main(args: Array[String]): Unit = {

    // Start the system
    val system = ActorSystem("Elf_Calorie_Counter")

    val eval_Actor = system.actorOf(Props[Eval_Actor],"Eval_Actor")

    //Parse the input
    val fileName = "input.txt"
    var elf_id = 0
    val bufferedSource = scala.io.Source.fromFile(fileName)

    //Create a list to hold totals
    val cal_list: ListBuffer[Int] = new ListBuffer[Int]()

    //Foreach line
    for (line <- bufferedSource.getLines()) {
      if(line.isEmpty){

        //Create and send the full list to the actors
        val elf_counter =system.actorOf(Props[Summation_Actor](),"Elf_sum" + elf_id)
        elf_counter ! Sum_List(elf_id,cal_list.toList,eval_Actor)

        //Increment and prepare for the next
        elf_id = elf_id + 1
        cal_list.clear()
        //Create/send to Sum_Actor
      }
      else {
        cal_list += line.toInt //make and add it to the list
      }
    }
    bufferedSource.close()
    eval_Actor ! "done"
  }
}

