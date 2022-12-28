import akka.actor.Actor
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import scala.collection.mutable.ListBuffer

object Accumulator {
  def props(limit):Props = Props(new Accumulator(limit))
}

class Accumulator(limit:Int) extends Actor{
  private val scores: ListBuffer[Int] = new ListBuffer[Int]();

  private def r_Score(score: Int): Unit = {
    scores.append(score)
    if(scores.length == limit){
      println(s"Final Sum is: ${scores.sum} len is: ${scores.length}")
      self ! PoisonPill
    }
  }

  override def receive: Receive = {
    case score:Int => r_Score(score)
  }
}