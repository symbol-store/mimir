package mimir.ml.spark


import java.sql.Timestamp
import java.sql.Date

import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.ml.PipelineModel
import org.apache.spark.ml.feature.Imputer
import org.apache.spark.sql.{SparkSession, SQLContext, DataFrame, Row, Dataset}
import org.apache.spark.sql.catalyst.encoders.RowEncoder
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.util.DateTimeUtils
import org.apache.spark.sql.types.{
  DataType, 
  DoubleType, 
  LongType, 
  FloatType, 
  BooleanType, 
  IntegerType, 
  StringType, 
  StructField, 
  StructType,
  ShortType,
  DateType,
  TimestampType
}

import mimir.Database
import mimir.algebra._
import mimir.exec.spark.RAToSpark
import mimir.exec.spark.MimirSpark
import mimir.provenance.Provenance
import mimir.util.SparkUtils
import mimir.util.ExperimentalOptions


object SparkML {
  type SparkModel = PipelineModel
  case class SparkModelGeneratorParams(db:Database, predictionCol:ID, handleInvalid:String /*keep, skip, error*/) 
  type SparkModelGenerator = SparkModelGeneratorParams => PipelineModel
  
}

abstract class SparkML {
  def getSparkSession() : SparkContext = {
    MimirSpark.get.sparkSession.sparkContext
  }
  
  def getSparkSqlContext() : SQLContext = {
    MimirSpark.get
  }
  
  type DataFrameTransformer = (DataFrame) => DataFrame
  
  protected def nullValueReplacement(df:DataFrame): DataFrame = {
    import org.apache.spark.sql.functions.mean
    val imputerCols = df.schema.fields.flatMap(col => {
      if(df.filter(df(col.name).isNull).count > 0)
        col.dataType match {
          case IntegerType | LongType | DoubleType | FloatType => Some(col.name)
          case StringType => None
          case _ => None
        }
      else None
    }).toArray
    new Imputer().setInputCols(imputerCols) .setOutputCols(imputerCols).fit(df).transform(df)
  }
  
  def fillNullValues(df:DataFrame) : DataFrame = {
    df.schema.fields.foldLeft(df)((init, curr) => {
      curr.dataType match {
        case LongType => init.na.fill(0L, Seq(curr.name))
        case IntegerType => init.na.fill(0L, Seq(curr.name))
        case FloatType => init.na.fill(0.0, Seq(curr.name))
        case DoubleType => init.na.fill(0.0, Seq(curr.name))
        case ShortType => init.na.fill(0.0, Seq(curr.name))
        case DateType => init.na.fill(0, Seq(curr.name))
        case BooleanType => init.na.fill(0, Seq(curr.name))
        case TimestampType => init.na.fill(0L, Seq(curr.name))
        case x => init.na.fill("", Seq(curr.name))
      }
    })
  }
  
  def applyModelDB(model : PipelineModel, query : Operator, db:Database, dfTransformer:Option[DataFrameTransformer] = None) : DataFrame = {
    val data = db.query(query)(results => {
      results.toList.map(row => row.provenance +: row.tupleSchema.zip(row.tuple).filterNot(_._1._1.equalsIgnoreCase("rowid")).unzip._2)
    })
    applyModel(
      model, 
      (ID("rowid"), TString()) +:
        db.typechecker
          .schemaOf(query)
          .filterNot { _._1.equals(ID("rowid")) },
      data, 
      db,
      dfTransformer
    )
  }
  
  def applyModel( model : PipelineModel, cols:Seq[(ID, Type)], testData : List[Seq[PrimitiveValue]], db: Database, dfTransformer:Option[DataFrameTransformer] = None): DataFrame = {
    val sqlContext = getSparkSqlContext()
    import sqlContext.implicits._
    val modDF = dfTransformer.getOrElse((df:DataFrame) => df)
    model.transform(modDF(sqlContext.createDataFrame(
      getSparkSession().parallelize(testData.map( row => {
        Row(row.zip(cols).map(value => new RAToSpark(db).mimirExprToSparkExpr(null, value._1)):_*)
      })), StructType(cols.toList.map(col => StructField(col._1.id, RAToSpark.getSparkType(col._2), true))))))
  }
  
  def applyModel( model : PipelineModel, inputDF:DataFrame): DataFrame = {//inputPlan:LogicalPlan): DataFrame = {
    model.transform(inputDF)
  }
  
  def extractPredictions(model : PipelineModel, predictions:DataFrame, maxPredictions:Int = 5) : Seq[(String, (String, Double))]  
  
  def extractPredictionsForRow(model : PipelineModel, predictions:DataFrame, rowid:String, maxPredictions:Int = 5) : Seq[(String, Double)]
   
  def getNative(value:PrimitiveValue, t:Type): Any = {
    value match {
      case NullPrimitive() => t match {
        case TInt() => 0L
        case TFloat() => new java.lang.Double(0.0)
        case TDate() => RAToSpark.defaultDate
        case TString() => ""
        case TBool() => new java.lang.Boolean(false)
        case TRowId() => ""
        case TType() => ""
        case TAny() => ""
        case TTimestamp() => RAToSpark.defaultTimestamp
        case TInterval() => ""
        case TUser(name) => getNative(value, mimir.algebra.TypeRegistry.registeredTypes(name)._2)
        case x => ""
      }
      case RowIdPrimitive(s) => s
      case StringPrimitive(s) => s
      case IntPrimitive(i) => i
      case FloatPrimitive(f) => new java.lang.Double(f)
      case BoolPrimitive(b) => new java.lang.Boolean(b)
      case ts@TimestampPrimitive(y,m,d,h,mm,s,ms) => SparkUtils.convertTimestamp(ts)
      case dt@DatePrimitive(y,m,d) => SparkUtils.convertDate(dt)
      case x =>  x.asString
    }
  }
}
