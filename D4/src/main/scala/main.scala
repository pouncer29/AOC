import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, actorRef2Scala}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer


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