import akka.actor.Actor
import akka.actor.{ActorRef, ActorSystem,Props,PoisonPill}
import scala.collection.mutable.ListBuffer

case class Game(opponent_move:Char,suggested_move:Char){}

object Strategy_Calculator{
  def main(args: Array[String]): Unit = {

    // Start the system
    val system = ActorSystem("Elf_Calorie_Counter")

    //Parse the input
    val fileName = "./D2/d2.txt"
    val bufferedSource = scala.io.Source.fromFile(fileName)

    //Create a list to hold totals
    val cal_list: ListBuffer[Int] = new ListBuffer[Int]()

    //Foreach line
    for (line <- bufferedSource.getLines()) {
    }

    bufferedSource.close()
    println("Sending DONE");
  }
}