package mimir

import java.io._
import scala.sys.process.Process


import org.specs2.mutable._
import org.specs2.specification._

import mimir.sql._

class MimirVizierSpec 
  extends Specification
  with BeforeAll
{

  def beforeAll = {
    val dbFileName = "MimirVizierSpec.db"

    val args = Seq(
      "--db", dbFileName
    )
    Mimir.conf = new MimirConfig(args);
    val database = Mimir.conf.dbname().split("[\\\\/]").last.replaceAll("\\..*", "")
    val sback = new SparkBackend(database)
    MimirVizier.db = new Database(sback, new JDBCMetadataBackend(Mimir.conf.backend(), Mimir.conf.dbname()))
    MimirVizier.db.metadataBackend.open()
    MimirVizier.db.backend.open()
    val otherExcludeFuncs = Seq("NOT","AND","!","%","&","*","+","-","/","<","<=","<=>","=","==",">",">=","^","|","OR")
    sback.registerSparkFunctions(MimirVizier.db.functions.functionPrototypes.map(el => el._1).toSeq ++ otherExcludeFuncs , MimirVizier.db.functions)
    sback.registerSparkAggregates(MimirVizier.db.aggregates.prototypes.map(el => el._1).toSeq, MimirVizier.db.aggregates)
    MimirVizier.vizierdb.sparkSession = sback.sparkSql.sparkSession
    MimirVizier.db.initializeDBForMimir()

    if(!MimirVizier.db.tableExists("CPUSPEED")){
      MimirVizier.db.loadTable(
        "CPUSPEED",
        new File("test/data/CPUSpeed.csv"), 
        force = true,
        format = ("CSV", Seq())
      )
    }
  }

  "MimirVizier" should {

    "be set up properly" >> { 
      MimirVizier.db.tableExists("CPUSPEED") must beTrue
      MimirVizier.db.tableExists("NOT_A_TABLE") must beFalse

      val schema = MimirVizier.db.tableSchema("CPUSPEED") 
      schema must not be(None) 
      schema.get.map { _._1 } must contain("BUS_SPEED_MHZ")
    }

    "create missing value lenses properly" >> { 
      if(MimirVizier.db.tableExists("CPUSPEED_MISSING")){
        MimirVizier.db.lenses.drop("CPUSPEED_MISSING")
      }
      MimirVizier.db.tableExists("CPUSPEED_MISSING") must beFalse
      val response = MimirVizier.createLens(
        "CPUSPEED",
        Seq("BUS_SPEED_MHZ"),
        "MISSING_VALUE",
        false,
        false
      )
      MimirVizier.db.query(
        MimirVizier.db.table(response.lensName).limit(1)
      ) { response => response.toSeq } must not beEmpty
    }

  }
}