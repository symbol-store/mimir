package mimir.exec;

import java.io._
import org.specs2.specification._
import scala.util.Random

import mimir.algebra._
import mimir.util._
import mimir.test._
import mimir.models._
import mimir.exec.mode._
import org.specs2.specification.core.Fragments

object TupleBundleSpec
  extends SQLTestSpecification("TupleBundler")
  with BeforeAll 
{

  sequential

  def beforeAll = 
  {
    //update("CREATE TABLE R(A int, B int, C int)")
    loadCSV(targetTable = "R", sourceFile = "test/r_test/r.csv", targetSchema = Seq("A", "B", "C"))
    update("CREATE LENS R_CLASSIC AS SELECT * FROM R WITH KEY_REPAIR(A)")
    // update("CREATE LENS R_FASTPATH AS SELECT * FROM R WITH KEY_REPAIR(A, ENABLE(FAST_PATH))")
  }

  val rand = new Random(42)
  val numSamples = 10
  val bundler = new TupleBundle((0 until numSamples).map { _ => rand.nextLong })
  def compileFlat(query: Operator) = bundler.compileFlat(query, db.models.get(_))
  val allWorlds = WorldBits.fullBitVector(numSamples)
  val columnNames = TupleBundle.columnNames(_:ID, numSamples)

  def conf(bv: Long): Double = WorldBits.confidence(bv, numSamples)

  def sanityCheckDisjointWorldset(disjointWorlds: Seq[Long])
  {
    val worlds = disjointWorlds.map( WorldBits.worlds(_, numSamples) ).toSeq

    // All samples should be represented
    worlds.flatten should beEqualTo((0 until numSamples).toSet)

    // Shouldn't be seeing any pairs that exist in the same world
    for(i <- 0 until worlds.size){
      for(j <- 0 until worlds.size){
        if(i != j){
          (worlds(i) & worlds(j)) must beEmpty
        }
      }
    }
  }

  "Tuple Bundle Evaluation" should {
    "Compile sanely" >> {

      val q1 = 
        // db.compiler.optimize(
          compileFlat(select("""
            SELECT * FROM R_CLASSIC WHERE B = 2
          """))._1
        // )
      q1.columnNames must contain(eachOf(
        ID("A"), 
        ID("MIMIR_SAMPLE_0_B"), 
        ID("MIMIR_SAMPLE_2_C"), 
        ID("MIMIR_WORLD_BITS")
      ))
    }

    "Evaluate Project Queries" >> {
      val q1 =
        compileFlat(select("""
          SELECT A, B FROM R_CLASSIC
        """))._1

      val r1 =
        db.query(q1){ _.map { row => 
          (
            row(ID("A")).asLong.toInt, 
            columnNames(ID("B")).map { row(_).asLong.toInt }.toSet
          ) 
        }.toMap }

      // Deterministic rows.  Should always be there
      r1 must contain(eachOf( (2 -> Set(2)), (4 -> Set(2)) ))

      // Nondeterministic rows.  Not a huge deal if this next case breaks.  
      // Poke the PRNG above or add more samples until this works again.
      r1(1) should contain(eachOf( 4, 2, 3 ))

    }

    "Evaluate Select-Project Queries" >> {
      val q1 =
        compileFlat(select("""
          SELECT A FROM R_CLASSIC WHERE B = 2
        """))._1

      q1.columnNames must beEqualTo(Seq(ID("A"), ID("MIMIR_WORLD_BITS")))

      val r1 =
        db.query(q1) { _.map { row => 
            (row(ID("A")).asInt, row(ID("MIMIR_WORLD_BITS")).asLong)
          }.toMap 
        }

      r1.keys should contain( eachOf(1, 2, 4) )
      if(r1 contains 2){
        r1(2) must be equalTo ( allWorlds )
      }

      // Nondeterministic rows.  Not a huge deal if this next case breaks.  
      // Poke the PRNG above or add more samples until this works again.
      conf(r1(1)) should beBetween(0.0, 0.6)

      // Deterministic rows.  Should always be there
      conf(r1(2)) must beEqualTo(1.0)
      conf(r1(4)) must beEqualTo(1.0)
    }

    "Evaluate Deterministic Aggregate Queries without Group-Bys" >> {
      val q1 = 
        compileFlat(select("""
          SELECT SUM(A) AS A FROM R_CLASSIC
        """))._1

      // This test assumes that compileFlat just adds a WORLDS_BITS column
      q1.columnNames must beEqualTo(Seq(ID("A"), ID("MIMIR_WORLD_BITS")))

      val r1 =
        db.query(q1){ _.map { row =>
            (row(ID("A")).asInt, row(ID("MIMIR_WORLD_BITS")).asLong)
          }.toIndexedSeq
        }

      // This particular result should be deterministic
      r1 must haveSize(1)
      r1(0)._1 must beEqualTo( 7 )
      r1(0)._2 must beEqualTo( allWorlds )
    }


    "Evaluate Aggregate Queries without Group-Bys" >> {
      val q1 = 
        compileFlat(select("""
          SELECT SUM(B) AS B FROM R_CLASSIC
        """))._1

      // This test assumes that compileFlat just splits 'B' into samples and 
      // adds a world bits column.
      q1.columnNames must beEqualTo(
        columnNames(ID("B")).toSeq ++ Seq(ID("MIMIR_WORLD_BITS"))
      )

      // Extract into (Seq(B values), worldBits)
      val r1 =
        db.query(q1) { _.map { row => 
          ( columnNames(ID("B")).map { row(_).asInt }.toSeq, row(ID("MIMIR_WORLD_BITS")).asLong )
        }.toIndexedSeq }

      // This particular result *row* should be deterministic
      r1 must haveSize(1)
      r1(0)._2 must beEqualTo( allWorlds )
      // A = 1 -> B = { 2, 3, 4 }
      // A = 2 -> B = 2
      // A = 4 -> B = 2
      // SUM = either 6, 7, or 8
      r1(0)._1.toSet should beEqualTo( Set( 6, 7, 8 ) )
    }

    "Evaluate Aggregate Queries with Nondeterministic Aggregates" >> {
      val q1 = 
        compileFlat(select("""
          SELECT A, SUM(B) AS B FROM R_CLASSIC GROUP BY A
        """))._1

      // This test assumes that compileFlat just adds a WORLDS_BITS column
      q1.columnNames must beEqualTo(
        Seq(ID("A"))++ columnNames(ID("B")) ++ Seq(ID("MIMIR_WORLD_BITS"))
      )

      val r1:Map[Int, (Seq[Int], Long)] =
        db.query(q1){ _.map { row => 
          ( row(ID("A")).asInt -> 
            ( columnNames(ID("B")).map { row(_).asInt }.toSeq, 
              row(ID("MIMIR_WORLD_BITS")).asLong
            )
          )
        }.toMap }

      // A is deterministic.  We should see all possibilities
      r1.keys must contain(eachOf( 1, 2, 4 ))

      // Thus each row should also be present in all worlds
      r1(1)._2 must beEqualTo( allWorlds )
      r1(2)._2 must beEqualTo( allWorlds )
      r1(4)._2 must beEqualTo( allWorlds )

      // B should only be nondeterministic for one table
      r1(1)._1.toSet should beEqualTo( Set( 2, 3, 4 ) )
      r1(2)._1.toSet should beEqualTo( Set( 2 ) )
      r1(4)._1.toSet should beEqualTo( Set( 2 ) )
    }

    "Evaluate Aggregate Queries with Nondeterministic Group-Bys" >> {
      val q1 = 
        compileFlat(select("""
          SELECT B, SUM(A) AS A FROM R_CLASSIC GROUP BY B
        """))._1

      q1.columnNames must beEqualTo(
        Seq(ID("B"))++columnNames(ID("A"))++Seq(ID("MIMIR_WORLD_BITS"))
      )

      val r1: Map[Int, (Set[Int], Set[Int])] =
        db.query(q1){ _.map { row => 
          val bv = row(ID("MIMIR_WORLD_BITS")).asLong
          ( row(ID("B")).asInt -> (
              TupleBundle.possibleValues(bv, columnNames(ID("A")).map { row(_) }).keySet.map { _.asInt },
              WorldBits.worlds(bv, numSamples)
            )
          )
        }.toMap }

      r1.keys must contain( 2 )
      r1.keys should contain(eachOf( 3, 4 ))
      r1(2)._1.toSet should be equalTo( Set(6, 7) )
      r1(3)._1.toSet should be equalTo( Set(1) )
      r1(4)._1.toSet should be equalTo( Set(1) )

      r1(2)._2.size should be equalTo(numSamples)
      r1(3)._2.size should be between(0, numSamples / 2)
      r1(4)._2.size should be between(0, numSamples / 2)

    }
  }
}
