import akka.actor.Actor
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}



case class Sum_List(elf_id:Int,cal_list:List[Int],forward_to:ActorRef[Eval_Actor])
case class Summation(elf_id:Int,total:Int)

class Summation_Actor extends Actor{
  override def receive: Receive = {
    case l:Sum_List => r_Sum_List(l)
  }

  //Forwards the summation as a total
  private def r_Sum_List(do_sum:Sum_List):Unit= {
    val my_sum = Summation(do_sum.elf_id, do_sum.cal_list.sum)
    my_sum ! do_sum.forward_to
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
    println("LARGEST WAS: %i from %i",largest.total,largest.elf_id)
  }
}


object HelloWorldMain {
  def main(args: Array[String]): Unit = {

   //Parse the input




    // Start the system
    val system: ActorSystem[HelloWorldMain.SayHello] =
      ActorSystem(HelloWorldMain(), "hello")
  }
}