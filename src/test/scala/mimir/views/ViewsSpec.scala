package mimir.views;

import org.specs2.specification._
import mimir.exec._
import mimir.algebra._
import mimir.util._
import mimir.test._
import mimir.ctables._
import mimir.provenance._
import java.io.File
import org.apache.spark.sql.Encoders

object ViewsSpec 
  extends SQLTestSpecification("ViewsTest")
  with BeforeAll
{

  def beforeAll = {
    //loadCSV("R",Seq(("A", "int"), ("B", "int"), ("C", "int")), new File("test/data/views_r.csv"))
    LoadCSV.handleLoadTableRaw(db, 
      ID("R"), 
      "test/data/views_r.csv",  
      Some(Seq(
        ID("A") -> TInt(), 
        ID("B") -> TInt(), 
        ID("C") -> TInt()
      )), 
      Map(
        "DELIMITER" -> ",",
        "ignoreLeadingWhiteSpace"->"true",
        "ignoreTrailingWhiteSpace"->"true", 
        "mode" -> /*"PERMISSIVE"*/"DROPMALFORMED", 
        "header" -> "false"
      ) 
    )
  }

  sequential

  "The View Manager" should {
    "Not interfere with CSV Imports" >> {
      //loadCSV("S",Seq(("C", "int"), ("D", "int")), new File("test/data/views_s.csv"))
      LoadCSV.handleLoadTableRaw(db, 
        ID("S"), 
        "test/data/views_s.csv",  
        Some(Seq(
          ID("C") -> TInt(),
          ID("D") -> TInt()
        )), 
        Map(
          "DELIMITER" -> ",",
          "ignoreLeadingWhiteSpace"->"true",
          "ignoreTrailingWhiteSpace"->"true",
           "mode" -> /*"PERMISSIVE"*/"DROPMALFORMED",
           "header" -> "false"
        ) 
      )
      ok
    }

    "Support Simple SELECTs" >> {
      db.views.create(ID("RAB"), select("SELECT A, B FROM R"))
      queryOneColumn("SELECT A FROM RAB"){ 
        _.toSeq must contain(i(1), i(1), i(1), i(2), i(4))
      }
    }

    "Support Joins" >> {
      db.views.create(ID("RS"), select("SELECT A, B, R.C, D FROM R, S WHERE R.C = S.C"))

      queryOneColumn("SELECT B FROM RS"){ 
        _.toSeq must contain(i(3),i(3),i(3),i(3),i(2),i(2))
      }
      
    }

    "Process CREATE VIEW statements" >> {
      update("CREATE VIEW TEST1 AS SELECT A, B FROM R")
      queryOneColumn("SELECT A FROM TEST1"){ 
        _.toSeq must contain(i(1), i(1), i(1), i(2), i(4))
      }
    }

  }

  "Materialized Views" should {

    "Be creatable" >> {

      update("CREATE VIEW MATTEST AS SELECT A, B FROM R WHERE C = 1")

      db.views(ID("MATTEST")).operator must be equalTo
        View(ID("MATTEST"),
          db.
            table("R").
            filter(expr("C = 1")).
            project("A", "B")
        )

      update("ALTER VIEW MATTEST MATERIALIZE")
      //could also do this
      //val sparkOp = db.backend.execute(db.views.get("MATTEST").get.materializedOperator)
      //instead of
      val sparkOp = db.backend.execute(Table(ID("MATTEST"), ID("MATTEST"), Seq(
        ID("A") -> TInt(), 
        ID("B") -> TInt(), 
        ViewAnnotation.taintBitVectorColumn -> TInt(), 
        ID("MIMIR_ROWID_0") -> TRowId()
      ), Seq()))
      val results = 
        sparkOp.collect().toList.map( row => {
          ( row.getLong(0), row.getLong(1), row.getLong(2), row.getString(3).toLong ) 
        })
        
      results must contain(eachOf(
        (1l, 3l, 7l, db.query(select("SELECT ROWID() FROM R WHERE A = 1 AND B = 3 AND C = 1"))(_.toList.head(0).asLong)),
        (2l, 2l, 7l, db.query(select("SELECT ROWID() FROM R WHERE A = 2 AND B = 2 AND C = 1"))(_.toList.head(0).asLong))
      ))

    }

    "Support raw queries" >> {

      db.compiler.optimize(
        db.views.resolve(db.views(ID("MATTEST")).operator)
      ) must be equalTo(
        Project(Seq(ProjectArg(ID("A"), Var(ID("A"))),ProjectArg(ID("B"), Var(ID("B")))),
          Table(ID("MATTEST"),ID("MATTEST"), db.views(ID("MATTEST")).materializedSchema, Seq())
        )
      )

    }

    "Support provenance-compiled queries" >> {

      val (query, rowidCols) = Provenance.compile(db.views(ID("MATTEST")).operator)

      db.compiler.optimize(
        db.views.resolve(query)
      ) must be equalTo(
        Project(Seq(
            ProjectArg(ID("A"), Var(ID("A"))),
            ProjectArg(ID("B"), Var(ID("B")))
          ) ++ rowidCols.map { col => ProjectArg(col, Var(col)) },
          Table(ID("MATTEST"),ID("MATTEST"), db.views(ID("MATTEST")).materializedSchema, Seq())
        )
      )

    }

    "Support provenance and bestguess-compiled queries" >> {

      val (query, rowidCols) = Provenance.compile(db.views(ID("MATTEST")).operator)

      db.compiler.optimize(
        db.views.resolve(OperatorDeterminism.compile(query, db.models.get(_))) 
      ) must be equalTo(
        Project(Seq(
            ProjectArg(ID("A"), Var(ID("A"))),
            ProjectArg(ID("B"), Var(ID("B"))),
            ProjectArg(OperatorDeterminism.mimirColDeterministicColumn(ID("A")), Comparison(Cmp.Eq, Arithmetic(Arith.BitAnd, Var(ViewAnnotation.taintBitVectorColumn), IntPrimitive(2)), IntPrimitive(2))),
            ProjectArg(OperatorDeterminism.mimirColDeterministicColumn(ID("B")), Comparison(Cmp.Eq, Arithmetic(Arith.BitAnd, Var(ViewAnnotation.taintBitVectorColumn), IntPrimitive(4)), IntPrimitive(4))),
            ProjectArg(OperatorDeterminism.mimirColDeterministicColumn(ID("MIMIR_ROWID_0")), Comparison(Cmp.Eq, Arithmetic(Arith.BitAnd, Var(ViewAnnotation.taintBitVectorColumn), IntPrimitive(8)), IntPrimitive(8))),
            ProjectArg(OperatorDeterminism.mimirRowDeterministicColumnName, Comparison(Cmp.Eq, Arithmetic(Arith.BitAnd, Var(ViewAnnotation.taintBitVectorColumn), IntPrimitive(1)), IntPrimitive(1)))
          ) ++ rowidCols.map { col => ProjectArg(col, Var(col)) },
          Table(ID("MATTEST"),ID("MATTEST"), db.views(ID("MATTEST")).materializedSchema, Seq())
        )
      )
      
    }
  }

}
