import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, actorRef2Scala}

/**
 * 1. Split ranges into case object
 * 2. Send to comparitor for evaluation
 * 3. if range encapsuated, accumulate
 */

case class Range_String(range_1:String,range_2:String)
case class Range_Set(range_1: (Int,Int),range_2:(Int,Int))

object Formatter{
  def props(accumulator:ActorRef,id:Int):Props = Props( new Formatter(accumulator,id))
}

class Formatter(accumulator:ActorRef,id:Int) extends Actor{

  def r_RangeString(range_string: Range_String): Unit = {
    println(s"Actor ${id} received: ${range_string.range_1} vs ${range_string.range_2}")

    //Grab range 1 integers
    val lower_1 = range_string.range_1.split('-')(0).toInt
    val upper_1 = range_string.range_1.split('-')(1).toInt

    //grab range 2 integers
    val lower_2 = range_string.range_2.split('-')(0).toInt
    val upper_2 = range_string.range_2.split('-')(1).toInt

    //Ship it!
    self ! Range_Set((lower_1,upper_1),(lower_2,upper_2))
  }

  def r_RangeSet(range_set: Range_Set): Unit = {
    val lower_A = range_set.range_1._1
    val upper_A = range_set.range_1._2
    val lower_B = range_set.range_2._1
    val upper_B = range_set.range_2._2

    //If one range encapsulates the other, send a tally
    if(lower_A <= lower_B && upper_A >= upper_B)
      accumulator ! 1
     else if (lower_B <= lower_A && upper_B >= upper_A)
      accumulator ! 1
    else
      accumulator ! 0
  }

  override def receive: Receive = {
    case r:Range_String => r_RangeString(r)
    case rs:Range_Set => r_RangeSet(rs)

  }
}


object main{
  def main(args: Array[String]): Unit = {

    // Start the system
    val system = ActorSystem("SYS")


    //Parse the input
    val fileName = "./D4/d4.input.txt"
    println("READING LINES")

    //reset the source....
    var bufferedSource = scala.io.Source.fromFile(fileName)
    val accumulator = system.actorOf(Accumulator.props(1000))

    //Foreach line
    var line_num = 1;
    for (line <- bufferedSource.getLines()) {
      val ranges = line.split(',')
      val comparitor = system.actorOf(Formatter.props(accumulator,line_num))
      comparitor ! Range_String(ranges(0),ranges(1))
      //println(s"Line ${line_num} --> ${line}")
      line_num = line_num + 1
    }


    bufferedSource.close()
  }
}
