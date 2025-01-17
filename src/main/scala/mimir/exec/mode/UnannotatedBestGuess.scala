package mimir.exec.mode

import com.typesafe.scalalogging.LazyLogging
import mimir.Database
import mimir.algebra._
import mimir.exec._
import mimir.exec.result._
import mimir.provenance._
import mimir.ctables._

object UnannotatedBestGuess
  extends CompileMode[ResultIterator]
  with LazyLogging
{
  type MetadataT = Seq[ID]

  /**
   * Rewrite the specified operator
   */
  def rewrite(db: Database, operRaw: Operator): (Operator, Seq[ID], MetadataT) =
  {
    var oper = operRaw

    // Force the typechecker to run
    db.typechecker.schemaOf(oper)
      
    // The names that the provenance compilation step assigns will
    // be different depending on the structure of the query.  As a 
    // result it is **critical** that this be the first step in 
    // compilation.  
    val provenance = Provenance.compile(oper)

    oper                       = provenance._1
    val provenanceCols:Seq[ID] = provenance._2

    logger.debug(s"WITH-PROVENANCE (${provenanceCols.mkString(", ")}): $oper")

    oper = db.views.resolve(oper)

    logger.debug(s"INLINED: $oper")

    // Replace VG-Terms with their "Best Guess values"
    oper = InlineVGTerms(oper, db)

    logger.debug(s"GUESSED: $oper")

    // Tack on a single ROWID column
    val jointProvenanceExpression = 
      Function(Provenance.mergeRowIdFunction, provenanceCols.map( Var(_) ) )
    oper = oper.mapByID(
              (
                operRaw.columnNames.map { col => col -> Var(col) }
                  :+ (
                    Provenance.rowidColnameBase -> jointProvenanceExpression
                  )
              ):_*
            )

    // Clean things up a little... make the query prettier, tighter, and 
    // faster
    oper = db.compiler.optimize(oper)

    logger.debug(s"OPTIMIZED: $oper")

    (
      oper,
      operRaw.columnNames,
      Seq()
    )
  }

  /**
   * Wrap a resultset generated for the specified operator with a 
   * specific type of resultIterator.
   */
  def wrap(db: Database, results: ResultIterator, query: Operator, meta: MetadataT): ResultIterator =
  { 
    return results;
  }
}