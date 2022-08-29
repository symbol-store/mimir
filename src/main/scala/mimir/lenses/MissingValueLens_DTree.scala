package mimir.lenses

import mimir.algebra._
import mimir.models._

object MissingValueLens_DTree extends MissingValueLens {

  override def getModel():Map[ID,ModelRegistry.ImputationConstructor] =
  {
    ModelRegistry.dtree_imputations
  }
}
