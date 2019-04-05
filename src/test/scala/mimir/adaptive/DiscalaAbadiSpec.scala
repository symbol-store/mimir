package mimir.adaptive

import java.io._

import org.specs2.specification._
import org.specs2.mutable._

import mimir.algebra._
import mimir.test._
import mimir.util._

object DiscalaAbadiSpec
  extends SQLTestSpecification("DiscalaAbadi")
  with BeforeAll
{
  def beforeAll =
  {
    db.loadTable(
      "test/data/cureSource.csv", 
      targetTable = Some(ID("SHIPPING")), 
      force = true, 
      format = ID("csv"), 
      detectHeaders = Some(true),
      inferTypes = Some(false)
    )
  }

  sequential

  "The Discala-Abadi Normalizer" should {

    "Support schema creation" >> {
      update("""
        CREATE ADAPTIVE SCHEMA SHIPPING
          AS SELECT * FROM SHIPPING
          WITH DISCALA_ABADI()
      """)

      querySingletonMetadata("""
        SELECT COUNT(*) FROM MIMIR_DA_FDG_SHIPPING
      """).asLong must be greaterThan(20l)
      queryMetadata("""
        SELECT NAME FROM MIMIR_ADAPTIVE_SCHEMAS
      """) {
        _.toSeq.map( m => m.tuple(0)) must contain(StringPrimitive("SHIPPING"))
      }
      queryMetadata("""
        SELECT NAME FROM MIMIR_MODELS
      """) {
        _.toSeq.map( m => m.tuple(0)) must contain(StringPrimitive("MIMIR_DA_CHOSEN_SHIPPING:MIMIR_FD_PARENT"))
      }
    }

    "Create a sane root attribute" >> {
      queryMetadata("""
        SELECT ATTR_NODE FROM MIMIR_DA_SCH_SHIPPING
        WHERE ATTR_NAME = 'ROOT'
      """){ 
        _.toSeq must haveSize(1)
      }
      queryMetadata("""
        SELECT ATTR_NODE FROM MIMIR_DA_SCH_SHIPPING
        WHERE ATTR_NAME = 'ROOT'
          AND ATTR_NODE >= 0
      """){
        _.toSeq must haveSize(0)
      }

      val spanningTree = 
        DiscalaAbadiNormalizer.spanningTreeLens(db, 
          MultilensConfig(ID("SHIPPING"), db.table("SHIPPING"), Seq())
        )
      LoggerUtils.debug(
          // "mimir.exec.Compiler"
      ){
        db.query(

          spanningTree
            .map("TABLE_NODE" -> Var(ID("MIMIR_FD_PARENT")))
            .distinct
            .filter( Comparison(Cmp.Gt, Arithmetic(Arith.Add, Var(ID("TABLE_NODE")), IntPrimitive(1)), IntPrimitive(0)) )
            .project( "TABLE_NODE" )

        ){ _.map { _(ID("TABLE_NODE")) }.toSeq must not contain(IntPrimitive(-1)) }
      }

    }

    "Create a schema that can be queried" >> {
      db.query(

        OperatorUtils.makeUnion(db.adaptiveSchemas.tableCatalogs(ID("DISCALA_ABADI")))
          .project( "TABLE_NAME", "SCHEMA_NAME" )

      ) { _.toSeq.map { row => 
          (
            row(ID("TABLE_NAME")).asString, 
            row(ID("SCHEMA_NAME")).asString,
            row.isDeterministic
          )
        } must contain( 
          ("ROOT", "SHIPPING", true),
          ("CONTAINER_1", "SHIPPING", false)
        )
      }

      db.query(

        OperatorUtils.makeUnion( db.adaptiveSchemas.attrCatalogs(ID("DISCALA_ABADI")) )
          .project( "TABLE_NAME", "ATTR_NAME", "IS_KEY" )
          .sort( "TABLE_NAME" -> true, "IS_KEY" -> false )

      ){ results =>
        val attrs = results.map { row => 
          (
            row(ID("TABLE_NAME")).asString, 
            row(ID("ATTR_NAME")).asString,
            row(ID("IS_KEY")).asInstanceOf[BoolPrimitive].v
          )
        }.toSeq 
        attrs must contain( eachOf( 
          ("ROOT","MONTH",false),
          ("BILL_OF_LADING_NBR","QUANTITY",false)
        ) )
        attrs.map( row => (row._1, row._2) ) must not contain( ("ROOT", "ROOT") )
      }
    }

    "Allocate all attributes to some relation" >> {
      db.query(

        OperatorUtils.makeUnion( db.adaptiveSchemas.attrCatalogs(ID("DISCALA_ABADI")) )
          .project( "TABLE_NAME", "ATTR_NAME", "IS_KEY" )
          .sort( "TABLE_NAME" -> true, "IS_KEY" -> false )

      ){ _.map { row => 
          row(ID("ATTR_NAME")).asString
        }.toSet must be equalTo(
          db.table("SHIPPING").columnNames.map { _.id }.toSet
        )
      }
    }

    "Start off with no feedback" >> {
      db.models
        .get(ID("MIMIR_DA_CHOSEN_SHIPPING:MIMIR_FD_PARENT"))
        .isAcknowledged(0, Seq(StringPrimitive("FOREIGN_DESTINATION"))) must beFalse
    }

    "Allow native SQL queries over the catalog tables" >> {
      LoggerUtils.debug(
        // "mimir.exec.Compiler"
      ) {
        queryMetadata("""
          SELECT TABLE_NAME, SCHEMA_NAME FROM SYS_TABLES
        """){ results =>
          val tables = results.map { row => (row(ID("TABLE_NAME")).asString, row(ID("SCHEMA_NAME")).asString) }.toSeq 

          tables must contain( ("ROOT", "SHIPPING") )
          //tables must contain( ("MIMIR_VIEWS", "BACKEND") )
          tables must contain( ("shipping_raw", "BACKEND") )
        }
      } 


      
      queryMetadata("""
        SELECT TABLE_NAME, ATTR_NAME FROM SYS_ATTRS
      """) { results =>
        val attrs = results.map { row => (row(ID("TABLE_NAME")).asString, row(ID("ATTR_NAME")).asString) }.toSeq 
        attrs must contain( ("ROOT", "MONTH") )
        attrs must contain( ("BILL_OF_LADING_NBR", "QUANTITY") )
      }

      LoggerUtils.debug(
        // "mimir.exec.Compiler",
        // "mimir.exec.mode.BestGuess$"
      ) {
        queryMetadata("""
          SELECT ATTR_NAME FROM SYS_ATTRS
          WHERE SCHEMA_NAME = 'SHIPPING'
            AND TABLE_NAME = 'ROOT'
        """) { results =>
          val attrStrings = results.map { row => (row(ID("ATTR_NAME")).asString, row.isDeterministic) }.toSeq 
          attrStrings must contain(
            ("FOREIGN_DESTINATION", true)
          )
        }
      }
    }

    "Be introspectable" >> {
      val baseQuery = """
          SELECT ATTR_NAME, ROWID() AS ID FROM SYS_ATTRS
          WHERE SCHEMA_NAME = 'SHIPPING'
            AND TABLE_NAME = 'BILL_OF_LADING_NBR'
        """

      
      LoggerUtils.debug(
         // "mimir.exec.Compiler"
      ){
        queryMetadata(baseQuery){ results =>
          val attrStrings = results.map { row => 
            (
              row(ID("ATTR_NAME")).asString, 
              ( row(ID("ID")).asString, 
                row.isDeterministic
              )
            ) 
          }.toMap
           
          attrStrings.keys must contain("QUANTITY")
          attrStrings("QUANTITY")._2 must beFalse

          attrStrings("QUANTITY")._1 must contain('|')

          val explanation =
            explainRow(baseQuery, attrStrings("QUANTITY")._1)
    
          explanation.reasons.map(_.reason).head must contain(
            "QUANTITY could be organized under any of BILL_OF_LADING_NBR"
          )
        }
      }
      ok

    }


    "Create queriable relations" >> {
      queryOneColumn("""
        SELECT QUANTITY FROM `SHIPPING`.`BILL_OF_LADING_NBR`"""
      ){ _.toSeq must contain(StringPrimitive("1")) }
    }

    "Generate legitimate explanations on query results" >> {
      db.explainer.explainEverything(db.sqlToRA(stmt("""
        SELECT QUANTITY FROM SHIPPING.BILL_OF_LADING_NBR
      """).asInstanceOf[mimir.parser.SQLStatement].body)).flatMap(_.all(db)).map(_.reason) must contain(
        "QUANTITY could be organized under any of BILL_OF_LADING_NBR (19), ROOT (-1); I chose BILL_OF_LADING_NBR"
      )
    }

  }


}