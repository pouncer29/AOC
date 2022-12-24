import akka.actor.Actor
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import scala.collection.mutable.ListBuffer

case class Game_1(opponent_move:Int,suggested_move:Int,line_num:Int)
case class Game(opponent_move:Int,target_outcome:Char,line_num:Int)

object Outcome_Evaluator{
  def props(accumulator:ActorRef):Props = Props(new Outcome_Evaluator(accumulator));
}
object Outcome_Decider{
  def props(accumulator:ActorRef):Props = Props(new Outcome_Decider(accumulator));
}
object Formatter{
  def props(accumulator:ActorRef):Props = Props(new Formatter(accumulator));
}

class Outcome_Evaluator(accumulator:ActorRef) extends Actor{

  override def receive: Receive = {
    case g:Game_1 => r_Game(g)
  }

  private def r_Game(game: Game_1): Unit = {
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

class Outcome_Decider(accumulator:ActorRef) extends Actor{
  override def receive: Receive = {
    case g: Game => r_Game(g)
  }

  private def r_Game(g: Game): Unit = {
    var score = 0;
    if (g.target_outcome == 'W')
      score = do_Win(g.opponent_move)
    else if (g.target_outcome == 'D')
      score = do_Draw(g.opponent_move)
    else if (g.target_outcome == 'L')
      score = do_Lose(g.opponent_move)
    else
      println(s"****** UNRECOGNIZED TARGET ${g.target_outcome}")

    println(s"${g.line_num} --> ${g.opponent_move},${g.target_outcome} = <to> + ${score}")
    accumulator ! score

  }

  private def do_Win(opponent_move: Int): Int = {
    accumulator ! 6 //WE know we are going to win, so send the win total
    //Rock = 1, paper = 2, scissors = 3
    if (opponent_move == 1)
      2 //paper beats rock
    else if (opponent_move == 2)
      3 //scissors beats paper
    else if (opponent_move == 3)
      1 //rock beats scissors
    else {
      println(s"BAD OPPONENT MOVE ${opponent_move}");
      -1
    }
  }

  private def do_Draw(opponent_move: Int): Int = {
    accumulator ! 3 //WE know we are going to tie , so send the draw total
    opponent_move //Draws will always be the same as opponent move
  }

  private def do_Lose(opponent_move: Int): Int = {
    accumulator ! 0 //WE know we are going to tie , so send the draw total
    //Rock = 1, paper = 2, scissors = 3
    if (opponent_move == 1)
      3 //scissors loses to rock
    else if (opponent_move == 2)
      1 //rock loses to paper
    else if (opponent_move == 3)
      2 // paper loses to scissors
    else {
      println(s"BAD OPPONENT MOVE ${opponent_move}");
      -1
    }
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
    val target_outcome = chars(1)
      .replace('X', 'L')
      .replace('Y', 'D')
      .replace('Z', 'W')

    //Rip the value and send for evaluation
    val desired_outcome = target_outcome.charAt(0)

    //Pass the evaluation of the game outcome
    val od= context.actorOf(Outcome_Decider.props(accumulator))
    od ! Game(opponent_move.toInt, desired_outcome,gs._2)
    //println(s"SENT OD: ${opponent_move},${desired_outcome} --> ${gs._2}")

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