package mimir.models;

import scala.util.Random

import mimir.algebra._
import mimir.util._
import mimir.Database
import play.api.libs.json.JsObject
import play.api.libs.json.JsArray
import com.typesafe.scalalogging.LazyLogging

/**
 * A model representing a key-repair choice.
 * 
 * The index is ignored.
 * The one argument is a value for the key.  
 * The return value is an integer identifying the ordinal position of the selected value, starting with 0.
 */
@SerialVersionUID(1002L)
class GeocodingModel(override val name: ID, addrCols:Seq[Expression], geocoder:ID, apiKey:String, source: Operator) 
  extends Model(name) 
  with Serializable
  with NeedsReconnectToDatabase
  with ModelCache
  with SourcedFeedback
  with FiniteDiscreteDomain
  with LazyLogging
{
  
  
  val latlonLabel = Seq("Latitude", "Longitude")
  val (bestGuessResultPathLat, bestGuessResultPathLon) = 
    Map(
      ID("GOOGLE") -> (
        ".results[0].geometry.location.lat", 
        ".results[0].geometry.location.lng"
      ), 
      ID("OSM") -> (
        "[0].lat", 
        "[0].lon"
      )
    ).get(geocoder).get
  val geogoderLabel = Map(
      ID("GOOGLE") -> "Google", 
      ID("OSM") -> "Open Streets"
    ).get(geocoder).get
  
  @transient var db: Database = null
  
  def getCacheKey(idx: Int, args: Seq[PrimitiveValue], hints: Seq[PrimitiveValue] ) : ID = {
    ID(args(0).asString)
  }
  
   def getFeedbackKey(idx: Int, args: Seq[PrimitiveValue] ) : ID = {
     ID(s"${idx}_${args(0).asString}")
   }
  
  def argTypes(idx: Int) = {
      Seq(TRowId()).union(addrCols.map(_ => TString()))
  }
  def varType(idx: Int, args: Seq[Type]) = TFloat()
  def bestGuess(idx: Int, args: Seq[PrimitiveValue], hints: Seq[PrimitiveValue]  ) = {
    val rowid = RowIdPrimitive(args(0).asString)
    getFeedback(idx, args) match {
      case Some(v) => v.asInstanceOf[PrimitiveValue]
      case None => {
        (getCache(idx, args, hints) match {
          case Some(StringPrimitive(v)) => {
            Some(v)
          }
          case Some(x) => None
          case None => makeGeocodeRequest(args)
        }) match {
          case Some(jsonStr) => jsonToPrimitiveValue(idx, jsonStr)
          case None => NullPrimitive()
        }
      }    
    }
  }
  def sample(idx: Int, randomness: Random, args: Seq[PrimitiveValue], hints: Seq[PrimitiveValue]) = {
    NullPrimitive()
  }
  def reason(idx: Int, args: Seq[PrimitiveValue],hints: Seq[PrimitiveValue]): String = {
    val rowid = RowIdPrimitive(args(0).asString)
    val houseNumber = args(1).asString
    val streetName = args(2).asString
    val city = args(3).asString
    val state = args(4).asString
    getFeedback(idx, args) match {
      case Some(v) =>
        s"${getReasonWho(idx,args)} told me that $houseNumber $streetName, $city, $state has ${latlonLabel(idx)} = ${v.asInstanceOf[PrimitiveValue]} on row $rowid"
      case None => 
        getCache(idx, args, hints) match {
          case Some(StringPrimitive(jsonStr)) => {
            val v = jsonToPrimitiveValue(idx, jsonStr)
            s"I used a geocoder (${geogoderLabel}) to determine that $houseNumber $streetName, $city, $state has ${latlonLabel(idx)} = ${v.asInstanceOf[PrimitiveValue]} on row $rowid "
          }
          case x =>
            s"The location of (${geogoderLabel}) to determine that $houseNumber $streetName, $city, $state is unknown"
        }
      }
  }
  def feedback(idx: Int, args: Seq[PrimitiveValue], v: PrimitiveValue): Unit = { 
    setFeedback(idx, args, v)
  }
  def isAcknowledged (idx: Int, args: Seq[PrimitiveValue]): Boolean = {
    hasFeedback(idx, args)
  }
  def hintTypes(idx: Int): Seq[mimir.algebra.Type] = Seq()
   
  def getDomain(idx: Int, args: Seq[PrimitiveValue], hints: Seq[PrimitiveValue]): Seq[(PrimitiveValue,Double)] = 
  {
    (getCache(idx, args, hints) match {
      case Some(StringPrimitive(jsonStr)) => Some(jsonStr)
      case Some(x) => None
      case None => makeGeocodeRequest(args) 
    }) match {
      case Some(jsonStr) => {
        val geoJson = play.api.libs.json.Json.parse(jsonStr)
        geocoder match {
          case ID("GOOGLE") => {
            val jsonresults = geoJson.as[JsObject].value("results").as[JsArray]
            jsonresults.value.map(geoEntry => (
                FloatPrimitive(JsonUtils.seekPath( geoEntry, ".geometry.location."+(if(idx==0)"lat"else"lng")).toString().replaceAll("\"", "").toDouble), 
                1.0) )
          }
          case ID("OSM") => {
            val jsonresults = geoJson.as[JsArray]
            jsonresults.value.map(geoEntry => (
                FloatPrimitive(JsonUtils.seekPath( geoEntry, if(idx==0)".lat"else".lon").toString().replaceAll("\"", "").toDouble), 
                JsonUtils.seekPath( geoEntry, ".importance").toString().replaceAll("\"", "").toDouble) )
          }
        }
      }
      case None => Seq()
    }
  }
  
  def reconnectToDatabase(db: Database) = { 
    this.db = db 
  }

  def confidence(idx: Int, args: Seq[PrimitiveValue], hints:Seq[PrimitiveValue]): Double = {
    getFeedback(idx, args) match {
      case Some(v) => 1.0
      case None =>
        getCache(idx, args, hints) match {
          case Some(v) => 1.0
          case None => 0.0
        }
    }
  }
  
  private def makeGeocodeRequest(args: Seq[PrimitiveValue]) : Option[String] = {
    val houseNumber = args(1) match { case NullPrimitive() => "" ; case x => x.asString }
    val streetName = args(2) match { case NullPrimitive() => "" ; case x => x.asString }
    val city = args(3) match { case NullPrimitive() => "" ; case x => x.asString }
    val state = args(4) match { case NullPrimitive() => "" ; case x => x.asString }
    val url = geocoder match {
      case ID("GOOGLE") => (s"https://maps.googleapis.com/maps/api/geocode/json?address=${s"$houseNumber+${streetName.replaceAll(" ", "+")},+${city.replaceAll(" ", "+")},+$state".replaceAll("\\+\\+", "+")}&key=$apiKey")
      case ID("OSM") | _ => (s"https://geocode.em-sys.net/?format=json&street=$houseNumber%20$streetName&city=$city&state=$state")
    }
    try {
      val geoRes = HTTPUtils.get(url) 
      setCache(0, args, Seq(), StringPrimitive(geoRes))
      Some(geoRes)
    } catch {
        case ioe: Throwable =>  {
          logger.error(s"Exception with Geocoding Request: $url", ioe)
          None
        }
    }       
  }

  private def jsonToPrimitiveValue(idx:Int, jsonStr:String) : PrimitiveValue = {
    try {
      val geoJson = play.api.libs.json.Json.parse(jsonStr)
      FloatPrimitive(idx match {
        case 0 => JsonUtils.seekPath( geoJson, bestGuessResultPathLat).toString().replaceAll("\"", "").toDouble
        case 1 => JsonUtils.seekPath( geoJson, bestGuessResultPathLon).toString().replaceAll("\"", "").toDouble
        case x => throw new Exception(s"idx: $x is not valid for this geocoding model.")
      })
    }catch {
        case ioe: Throwable =>  {
          // println(ioe.toString())
          NullPrimitive()
        }
    }
  }
}
