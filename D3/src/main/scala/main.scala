import akka.actor.Actor
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import scala.collection.mutable.ListBuffer

object Rucksacks{
  def main(args: Array[String]): Unit = {

    // Start the system
    val system = ActorSystem("SYS")

    //Parse the input
    val fileName = "./D3/d3.txt"
    val bufferedSource = scala.io.Source.fromFile(fileName)

    //Foreach line
    var line_num = 0;
    for (line <- bufferedSource.getLines()) {
      println(s"Line ${line_num} --> ${line}")
      line_num = line_num + 1;
    }

    bufferedSource.close()
  }
}