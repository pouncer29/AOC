import akka.actor.Actor
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import scala.collection.mutable.ListBuffer

case class Game(opponent_move:Int,suggested_move:Int)

object Outcome_Evaluator{
  def props(accumulator:ActorRef):Props = Props(new Outcome_Evaluator(accumulator));
}

object Formatter{
  def props(accumulator:ActorRef):Props = Props(new Formatter(accumulator));
}

class Outcome_Evaluator(accumulator:ActorRef) extends Actor{

  override def receive: Receive = {
    case g:Game => r_Game(g)
  }

  def r_Game(game: Game): Unit = {
    val op_move = game.opponent_move
    val sug_move = game.suggested_move
    //Draw = 3
    if (op_move == sug_move){
      accumulator ! 3
    } else {
      //check Rock
      if (op_move == 1) {
        accumulator ! eval_rock(sug_move)
      } else if (op_move == 2) {
        accumulator ! eval_paper(sug_move)
      } else if (op_move == 3) {
        accumulator ! eval_scissors(sug_move)
      }
    }
    self ! PoisonPill
  }
  def eval_rock(suggested:Int): Int= {
    if(suggested == 2)
      0;
    else
      6
  }
  def eval_paper(suggested: Int): Int = {
    if (suggested == 3)
      0
    else
      6
  }

  def eval_scissors(suggested: Int):Int = {
    if (suggested == 1)
      0
    else
      6
  }

}

class Formatter(accumulator:ActorRef) extends Actor{


  override def receive: Receive = {
    case s:String => r_GameString(s)
  }
  def r_GameString(gs: String): Unit = {

    //Grab the coloumns
    val chars = gs.split(" ")

    //Format them To their values
    val opponent_move= chars(0)
      .replace('A','1')
      .replace('B','2')
      .replace('C','3')
    val suggested_move = chars(1)
      .replace('X', '1')
      .replace('Y', '2')
      .replace('Z', '3')

    //Rip the value and send for evaluation
    val suggested_move_value = suggested_move.toInt;
    accumulator ! suggested_move_value

    //Pass the evaluation of the game outcome
    val oc= context.actorOf(Outcome_Evaluator.props(accumulator))
    oc! Game(opponent_move = opponent_move.toInt, suggested_move =suggested_move_value)

  }
}


class AccumulatorActor extends Actor{
  private val scores: ListBuffer[Int] = new ListBuffer[Int]();
  override def receive: Receive = {
    case score:Int => scores.append(score)
    case "sum" => println(s"Final Score is: ${scores.sum}")
  }
}

object Strategy_Calculator{
  def main(args: Array[String]): Unit = {

    // Start the system
    val system = ActorSystem("SYS")
    val accumulator = system.actorOf(Props[AccumulatorActor]())

    //Parse the input
    val fileName = "./D2/d2.txt"
    val bufferedSource = scala.io.Source.fromFile(fileName)

    //Foreach line
    for (line <- bufferedSource.getLines()) {
      val formatter = system.actorOf(Formatter.props(accumulator))
      formatter ! line
    }

    bufferedSource.close()
    println("Sending DONE");
    accumulator ! "sum"
    accumulator ! PoisonPill
  }
}