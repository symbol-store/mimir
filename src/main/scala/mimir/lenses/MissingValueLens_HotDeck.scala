package mimir.lenses

import mimir.algebra._
import mimir.models._

object MissingValueLens_HotDeck extends MissingValueLens {

  override def getModel():Map[ID,ModelRegistry.ImputationConstructor] =
  {
    ModelRegistry.hotdeck_imputations
  }
}