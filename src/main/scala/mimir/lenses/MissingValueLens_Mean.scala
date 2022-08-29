package mimir.lenses

import mimir.algebra._
import mimir.models._

object MissingValueLens_Mean extends MissingValueLens {

  override def getModel():Map[ID,ModelRegistry.ImputationConstructor] =
  {
    ModelRegistry.mean_imputations
  }
}
