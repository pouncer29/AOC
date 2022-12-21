import akka.actor.Actor
import akka.actor.{ActorRef, ActorSystem,Props,PoisonPill}
import scala.collection.mutable.ListBuffer

case class Sum_List(elf_id:Int,cal_list:List[Int],forward_to:ActorRef)
case class Summation(elf_id:Int,total:Int)

case class Done(top_n:Int);
class Summation_Actor extends Actor{
  override def receive: Receive = {
    case l:Sum_List => r_Sum_List(l)
  }

  //Forwards the summation as a total
  private def r_Sum_List(do_sum:Sum_List):Unit= {
    val my_sum = Summation(do_sum.elf_id, do_sum.cal_list.sum)
//    println("%s sending sum %d to %s"
//      .format(self.path.name,
//        do_sum.cal_list.sum,
//        do_sum.forward_to.path.name
//      ))
    do_sum.forward_to ! my_sum
  }
}

class Eval_Actor extends Actor {
  private var sums: ListBuffer[Summation] = null;

  override def receive: Receive = {
    case s: Summation => r_Summation(s)
    case d:Done => r_Done(d);
  }

  //Set the new largest if it was received
  private def r_Summation(summation: Summation): Unit = {

    //println("Received %s 's summation of %d".format(summation.elf_id,summation.total));
    //If null, insert, if incoming -gt, prepend, if -lte, append
    if (sums == null) {
      this.sums = ListBuffer[Summation](summation);
    } else if (this.sums.head.total < summation.total) {
      this.sums.prepend(summation)
    } else {
      this.sums.append(summation)
    }
  }

  private def r_Done(d:Done): Unit = {
    println("Got Done!")
    for (sum <- 0 until d.top_n)
      println("%d th is elf %d with %d".format(sum+1,sums(sum).elf_id,sums(sum).total))

    val top_n:ListBuffer[Int] = this.sums.slice(0,d.top_n).map(s => s.total);
    println("Their Sum is %d".format(top_n.sum))

    self ! PoisonPill
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
    println("Sending DONE");
    eval_Actor ! Done(3)
  }
}

