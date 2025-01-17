package mimir.algebra.function

import mimir.algebra.PrimitiveValue
import mimir.algebra.{Type,ID}
import com.typesafe.scalalogging.LazyLogging


object SparkFunctions 
 extends LazyLogging {
  
  val sparkFunctions = scala.collection.mutable.Map[ID, (Seq[PrimitiveValue] => PrimitiveValue, Seq[Type] => Type)]()
  
  def addSparkFunction(fname:ID,eval:Seq[PrimitiveValue] => PrimitiveValue, typechecker: Seq[Type] => Type) : Unit = {
    sparkFunctions.put(fname, (eval, typechecker))
  }
  
  def register(fr: FunctionRegistry)
  {
    sparkFunctions.foreach(sfunc => {
      logger.debug("registering spark func: " + sfunc._1)
      fr.registerPassthrough(sfunc._1, sfunc._2._1, sfunc._2._2)
    })
  }
  
  
}
