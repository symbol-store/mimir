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
    loadCSV("SHIPPING", new File("test/data/cureSource.csv"))
  }

  sequential

  "The Discala-Abadi Normalizer" should {

    "Support schema creation" >> {
      update("""
        CREATE ADAPTIVE SCHEMA SHIPPING
          AS SELECT * FROM SHIPPING
          WITH DISCALA_ABADI()
      """)

      querySingleton("""
        SELECT COUNT(*) FROM MIMIR_DA_FDG_SHIPPING
      """).asLong must be greaterThan(20l)
      querySingleton("""
        SELECT NAME FROM MIMIR_ADAPTIVE_SCHEMAS
      """) must be equalTo(StringPrimitive("SHIPPING"))
      querySingleton("""
        SELECT NAME FROM MIMIR_MODELS
      """) must be equalTo(StringPrimitive("MIMIR_DA_CHOSEN_SHIPPING:MIMIR_FD_PARENT"))
    }

    "Create a sane root attribute" >> {
      query("""
        SELECT ATTR_NODE FROM MIMIR_DA_SCH_SHIPPING
        WHERE ATTR_NAME = 'ROOT'
      """).allRows.toSeq must haveSize(1)
      query("""
        SELECT ATTR_NODE FROM MIMIR_DA_SCH_SHIPPING
        WHERE ATTR_NAME = 'ROOT'
          AND ATTR_NODE >= 0
      """).allRows.toSeq must haveSize(0)

      val spanningTree = 
        DiscalaAbadiNormalizer.spanningTreeLens(db, 
          MultilensConfig("SHIPPING", db.getTableOperator("SHIPPING"), Seq())
        )
      LoggerUtils.debug(
        "mimir.exec.Compiler", () =>
        db.query(
          Project(Seq(ProjectArg("TABLE_NODE", Var("TABLE_NODE"))),
            Select(Comparison(Cmp.Gte, Var("TABLE_NODE"), IntPrimitive(0)),
              OperatorUtils.makeDistinct(
                Project(Seq(ProjectArg("TABLE_NODE", Var("MIMIR_FD_PARENT"))),
                  spanningTree
                )
              )
            )
          )
        ).allRows.flatten
      ) must not contain(IntPrimitive(-1))

    }

    "Create a schema that can be queried" >> {
      val tables = 
        db.query(
          OperatorUtils.projectDownToColumns(
            Seq("TABLE_NAME", "SCHEMA"),
            OperatorUtils.makeUnion(
              db.adaptiveSchemas.tableCatalogs
            )
          )
        ).mapRows { row => 
          (row(0).asString, row(1).asString, row.deterministicRow)
        }
      tables must contain( eachOf( 
        ("ROOT", "SHIPPING", true),
        ("CONTAINER_1", "SHIPPING", false)
      ))

      val attrs =
        db.query(
          Sort(Seq(SortColumn(Var("IS_KEY"), true), SortColumn(Var("TABLE_NAME"), true)),
            OperatorUtils.projectDownToColumns(
              Seq("TABLE_NAME", "ATTR_NAME", "IS_KEY"),
              OperatorUtils.makeUnion(
                db.adaptiveSchemas.attrCatalogs
              )
            )
          )
        ).mapRows { row => 
          (row(0).asString, row(1).asString, row(2).asInstanceOf[BoolPrimitive].v)
        } 
      attrs must contain( eachOf( 
        ("ROOT","QUANTITY",false),
        ("BILL_OF_LADING_NBR","WORLD_REGION_BY_COUNTRY_OF_ORIGIN",false)
      ) )
      attrs.map( row => (row._1, row._2) ) must not contain( ("ROOT", "ROOT") )

    }

  }


}