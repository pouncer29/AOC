import akka.actor.Actor
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import scala.collection.mutable.ListBuffer

case class Game(opponent_move:Int,suggested_move:Int,line_num:Int)

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

  private def r_Game(game: Game): Unit = {
    val op_move = game.opponent_move
    val sug_move = game.suggested_move
    //Draw = 3
    if (op_move == sug_move){
      accumulator ! 3
    } else {
      var outcome_score = -1
      //check Rock
      if (sug_move == 1) {
        outcome_score = eval_rock(op_move)
      } else if (sug_move == 2) {
        outcome_score = eval_paper(op_move)
      } else if (sug_move == 3) {
        outcome_score = eval_scissors(op_move)
      } else {
        println(s"************** ${op_move} NOT RECOGNIZED ***************")
      }

      println(s"Score for line ${game.line_num} (${game.opponent_move},${game.suggested_move}) --> ${outcome_score}")
      accumulator ! outcome_score

    }
    self ! PoisonPill
  }
  private def eval_rock(suggested:Int): Int= {
   //Paper = 2, scissors = 3
    if(suggested == 2)
      0
    else
      6
  }
  private def eval_paper(suggested: Int): Int = {
    //Rock = 1 scissors = 3
    if (suggested == 3)
      0
    else
      6
  }

  private def eval_scissors(suggested: Int):Int = {
    //Rock = 1, paper = 2
    if (suggested == 1)
      0
    else
      6
  }

}

class Formatter(accumulator:ActorRef) extends Actor{


  override def receive: Receive = {
    case s:(String,Int) => r_GameString(s)
  }
  private def r_GameString(gs: (String,Int)): Unit = {

    //println(s"GOT LINE: ${gs._2} --> ${gs._1}")
    //Grab the coloumns
    val chars = gs._1.split(" ")

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
    //println(s"line ${gs} --> Suggested move ${suggested_move} score is ${suggested_move_value}");
    accumulator ! suggested_move_value

    //Pass the evaluation of the game outcome
    val oc= context.actorOf(Outcome_Evaluator.props(accumulator))
    oc! Game(opponent_move = opponent_move.toInt, suggested_move =suggested_move_value,gs._2)

  }
}


class AccumulatorActor extends Actor{
  private val scores: ListBuffer[Int] = new ListBuffer[Int]();

  private def r_Score(score: Int): Unit = {
    scores.append(score)
    if(scores.length == 5000){
      println(s"Final Score is: ${scores.sum} len is: ${scores.length}")
      self ! PoisonPill
    }
  }

  override def receive: Receive = {
    case score:Int => r_Score(score)
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
    var line_num = 0;
    for (line <- bufferedSource.getLines()) {
      val formatter = system.actorOf(Formatter.props(accumulator))
      formatter ! (line,line_num)
      line_num = line_num + 1;
    }

    bufferedSource.close()
  }
}