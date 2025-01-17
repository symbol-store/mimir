package mimir.models;

import scala.util.Random

import mimir.algebra._
import mimir.util._

/**
 * A dumb, default Meta-Model to stand in until we get something better.
 *
 * This meta model always ignores VG arguments and picks the first model
 * in the list.
 */
@SerialVersionUID(1001L)
class DefaultMetaModel(name: ID, context: String, models: Seq[ID]) 
  extends Model(name) 
  with DataIndependentFeedback 
  with NoArgModel
  with FiniteDiscreteDomain
{
  def varType(idx: Int, args: Seq[Type]): Type = TString()
  def bestGuess(idx: Int, args: Seq[PrimitiveValue], hints: Seq[PrimitiveValue]): PrimitiveValue =
    choices(idx).getOrElse( StringPrimitive(models.head.id) )
  def sample(idx: Int, randomness: Random, args: Seq[PrimitiveValue], hints: Seq[PrimitiveValue]): PrimitiveValue =
    StringPrimitive(RandUtils.pickFromList(randomness, models).id)
  def reason(idx: Int, args: Seq[PrimitiveValue], hints: Seq[PrimitiveValue]): String =
  {
    choices(idx) match {
      case None => {
        val bestChoice = models.head
        val modelString = models.mkString(", ")
        s"I defaulted to guessing with '$bestChoice' (out of $modelString) for $context"
      }
      case Some(choiceStr) => 
        s"${getReasonWho(idx,args)} told me to use $choiceStr for $context"
    }
  }
  def validateChoice(idx: Int, v: PrimitiveValue) = models.contains(v.asString)

  def getDomain(idx: Int, args: Seq[PrimitiveValue], hints: Seq[PrimitiveValue]): Seq[(PrimitiveValue,Double)] =
    models.map( x => (StringPrimitive(x.id), 0.0) )

  def confidence (idx: Int, args: Seq[PrimitiveValue], hints:Seq[PrimitiveValue]) : Double = 1.0/models.size

}