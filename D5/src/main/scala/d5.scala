import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, actorRef2Scala}
object d5{

  def main(args:Array[String]): Unit = {

    // Start the system
    val system = ActorSystem("SYS")


    //Parse crates
    val fileName = "./D5/d5.test.crates"
    println("READING CRATES")

    //reset the source....
    var bufferedSource = scala.io.Source.fromFile(fileName)

    //Foreach line
    var line_num = 1;
    for (line <- bufferedSource.getLines()) {
      val crates = for (i <- 1 until line.length by 4)
        yield line.charAt(i)
      println(s"Line ${line_num} --> ${crates}")
      line_num = line_num + 1
    }


    bufferedSource.close()
  }

}
