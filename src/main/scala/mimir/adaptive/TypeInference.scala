package mimir.adaptive

import com.typesafe.scalalogging.slf4j.LazyLogging
import mimir.Database
import mimir.algebra._
import mimir.lenses._
import mimir.models._
import mimir.util.SqlUtils
import mimir.sql.SparkBackend
import mimir.sql.BackendWithSparkContext

object TypeInference
  extends Multilens
    with LazyLogging
{

  
  def detectType(v: String): Iterable[Type] = {
    Type.tests.flatMap({ case (t, regexp) =>
      regexp.findFirstMatchIn(v).map(_ => t)
    })++
      TypeRegistry.matchers.flatMap({ case (regexp, name) =>
        regexp.findFirstMatchIn(v).map(_ => TUser(name))
      })
  }

  def initSchema(db: Database, config: MultilensConfig): TraversableOnce[Model] =
  {
    logger.debug(s"Creating TypeInference: $config")
    val viewName = config.schema
    
    val stringDefaultScore: Double = 
      config.args match {
        case Seq() => 0.5
        case Seq(FloatPrimitive(f)) if (f >= 0.0 && f <= 1.0) => f
        case _ => throw new RAException(s"Invalid configuration for type inference lens: ${config.args}")
      }

    // Initialize the vote counters
    val modelColumns = 
      db.typechecker.schemaOf(config.query).flatMap({
        case (col, (TString() | TAny())) => Some(col)
        case _ => None
      }).toIndexedSeq

    val attributeTypeModel = 
      new TypeInferenceModel(
        s"MIMIR_TI_ATTR_${viewName}",
        modelColumns,
        stringDefaultScore,
        db.backend.asInstanceOf[BackendWithSparkContext].getSparkContext(),
        Some(db.backend.execute(config.query.limit(TypeInferenceModel.sampleLimit, 0)))
      )

    val warningModel = 
      new WarningModel(
        s"MIMIR_TI_WARNING_${viewName}",
        Seq(TString(), TString(), TString())
      )

    val columnIndexes = 
      modelColumns.zipWithIndex.toMap

    logger.debug(s"Training $attributeTypeModel.name on ${config.query}")
    //model.train(db.backend.execute(config.query))
    
    Seq(attributeTypeModel, warningModel)
  }

  def tableCatalogFor(db: Database, config: MultilensConfig): Operator =
  {
    HardTable(
      Seq(
        ("TABLE_NAME",TString())
      ),
      Seq(
        Seq(
          StringPrimitive("DATA")
        )
      )
    )
  }
  
  def attrCatalogFor(db: Database, config: MultilensConfig): Operator =
  {
    val model = db.models.get(s"MIMIR_TI_ATTR_${config.schema}").asInstanceOf[TypeInferenceModel]
    val columnIndexes = model.columns.zipWithIndex.toMap
    lazy val qSchema = db.typechecker.schemaOf(config.query).toMap
    HardTable(
      Seq(
        ("TABLE_NAME" , TString()), 
        ("ATTR_NAME" , TString()),
        ("IS_KEY", TBool()), 
        ("IDX", TInt()),
        ("HARD_TYPE", TType())
      ),
      config.query.columnNames.map(col => 
        Seq(
          StringPrimitive("DATA"), 
          StringPrimitive(col), 
          BoolPrimitive(false),
          IntPrimitive(columnIndexes.getOrElse(col, -1).toLong),
          if(columnIndexes contains col){ NullPrimitive() } 
            else { TypePrimitive(qSchema(col)) }
        )
      )
    ).addColumn(
      "ATTR_TYPE" -> 
        Var("HARD_TYPE")
          .isNull
          .thenElse {
            VGTerm(s"MIMIR_TI_ATTR_${config.schema}", 0, Seq(Var("IDX")), Seq())
          } {
            Var("HARD_TYPE")
          }
    ).removeColumns("IDX", "HARD_TYPE")
  }
        
  def viewFor(db: Database, config: MultilensConfig, table: String): Option[Operator] =
  {
    if(table.equals("DATA")){
      val model = db.models.get(s"MIMIR_TI_ATTR_${config.schema}").asInstanceOf[TypeInferenceModel]
      val columnIndexes = model.columns.zipWithIndex.toMap
      Some(Project(
        config.query.columnNames.map { colName => {
          ProjectArg(colName, 
            if(columnIndexes contains colName){ 
              val bestGuessType = model.bestGuess(0, Seq(IntPrimitive(columnIndexes(colName))), Seq())
              val castExpression = Function("CAST", Seq(
                  Var(colName),
                  bestGuessType
                ))
              Conditional(
                IsNullExpression(Var(colName)),
                NullPrimitive(),
                Conditional(
                  IsNullExpression(castExpression),
                  DataWarning(
                    s"MIMIR_TI_WARNING_${config.schema}",
                    NullPrimitive(),
                    Function("CONCAT", Seq(
                      StringPrimitive("Couldn't Cast [ "),
                      Var(colName),
                      StringPrimitive(" ] to "+bestGuessType+" on row "),
                      RowIdVar()
                    )),
                    Seq(StringPrimitive(colName), Var(colName), StringPrimitive(bestGuessType.toString))
                  ),
                  castExpression
                )
              )
            } else {
              Var(colName)
            }
          )
        }}, config.query
      ))  
    } else { None }
  }

  
}

