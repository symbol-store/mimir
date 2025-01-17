package mimir.algebra

import java.sql._

import mimir.util.RandUtils
import com.typesafe.scalalogging.LazyLogging
import mimir.views.ViewManager

object OperatorUtils extends LazyLogging {
    
  /** 
   * Strip the expression required to compute a single column out
   * of the provided operator tree.  
   * Essentially a shortcut for an optimized form of 
   *
   * Project( [Var(col)], oper )
   * 
   * is equivalent to (and will often be slower than): 
   *
   * Project(ret(0)._1, ret(0)._2) UNION 
   *     Project(ret(1)._1, ret(1)._2) UNION
   *     ...
   *     Project(ret(N)._1, ret(N)._2) UNION
   */
  def columnExprForOperator(col: ID, oper: Operator): 
    Seq[(Expression, Operator)] =
  {
    oper match {
      case p @ Project(_, src) => 
        List[(Expression,Operator)]((p.bindings.get(col).get, src))
      case Union(lhs, rhs) =>
        columnExprForOperator(col, lhs) ++ 
        	columnExprForOperator(col, rhs)
      case _ => 
        List[(Expression,Operator)]((Var(col), oper))
    }
  }

  /**
   * Identify all tables on which this operator depends
   */
  def findTables(o: Operator, descendIntoViews: Boolean = false): Set[(ID, ID)] =
  {
    o match {
      case Table(name, source, _, _) => Set((source, name))
      case View(name, _, _) if !descendIntoViews => Set((ViewManager.SCHEMA, name))
      case AdaptiveView(schema, name, _, _) if !descendIntoViews => Set((schema, name))
      case _ => o.children.flatMap { findTables(_, descendIntoViews) }.toSet
    }
  }

  def extractUnionClauses(o: Operator): Seq[Operator] =
  {
    o match { 
      case Union(lhs, rhs) => extractUnionClauses(lhs) ++ extractUnionClauses(rhs)
      case _ => Seq(o)
    }
  }

  def makeUnion(terms: Seq[Operator]): Operator = 
  {
    if(terms.isEmpty){  }
    terms match {
      case Seq() => throw new SQLException("Union of Empty List")
      case Seq(singleton) => singleton
      case _ => {
        val (head, tail) = terms.splitAt(terms.size / 2)
        Union(makeUnion(head), makeUnion(tail))
      }
    }
  }

  def extractProjections(oper: Operator): (Seq[ProjectArg], Operator) =
  {
    oper match {
      case Project(cols, src) => (cols.map(col => ProjectArg(col.name, col.expression)), src)
      case _ => (oper.columnNames.map(col => ProjectArg(col, Var(col))), oper)
    }
  }

  def mergeWithColumn(target: ID, default: Expression, oper: Operator)(merge: Expression => Expression): Operator =
  {
    if(oper.columnNames.contains(target)){
      replaceColumn(target, merge(Var(target)), oper)
    } else {
      oper.addColumnsByID(target -> merge(default))
    }
  }

  def shallowRename(mapping: Map[ID, ID], oper: Operator): Operator =
  {
    // Shortcut if the mapping is a no-op
    if(!mapping.exists { 
      case (original, replacement) => !original.equals(replacement) 
    }) { return oper; }

    // Strip off any existing projections:
    val (baseProjections, input) = extractProjections(oper)

    // Then rename and reapply them
    Project(
      baseProjections.map { case ProjectArg(name, expr) => 
        ProjectArg(mapping.getOrElse(name, name), expr)
      }, 
      input
    )
  }

  def replaceColumn(target: ID, replacement: Expression, oper: Operator) =
  {
    val (cols, src) = extractProjections(oper)
    val bindings = cols.map(_.toBinding).toMap
    Project(
      cols.map( col => 
        if(col.name.equals(target)){
          ProjectArg(target, Eval.inline(replacement, bindings))  
        } else { col }
      ),
      src
    )
  }

  def applyFilter(condition: List[Expression], oper: Operator): Operator =
    applyFilter(condition.fold(BoolPrimitive(true))(ExpressionUtils.makeAnd(_,_)), oper)

  def applyFilter(condition: Expression, oper: Operator): Operator =
    condition match {
      case BoolPrimitive(true) => oper
      case _ => 
        oper match {
          case Select(otherCond, src) =>
            Select(ExpressionUtils.makeAnd(condition, otherCond), src)
          case _ => 
            Select(condition, oper)
        }
    }

  def joinMergingColumns(cols: Seq[(ID, (Expression,Expression) => Expression)], lhs: Operator, rhs: Operator) =
  {
    val allCols = lhs.columnNames.toSet ++ rhs.columnNames.toSet
    val affectedCols = cols.map(_._1).toSet & lhs.columnNames.toSet & rhs.columnNames.toSet
    val wrappedLHS = 
      Project(
        lhs.columnNames.map( x => 
          ProjectArg(if(affectedCols.contains(x)) { x.withPrefix("__MIMIR_LJ_") } else { x }, 
                     Var(x))),
        lhs
      )
    val wrappedRHS = 
      Project(
        rhs.columnNames.map( x => 
          ProjectArg(if(affectedCols.contains(x)) { x.withPrefix("__MIMIR_RJ_") } else { x }, 
                     Var(x))),
        rhs
      )
    Project(
      ((allCols -- cols.map(_._1).toSet).map( (x) => ProjectArg(x, Var(x)) )).toList ++
      cols.flatMap({
        case (name, op) =>
          if(affectedCols(name)){
            Some(ProjectArg(name, op(Var(ID("__MIMIR_LJ_",name)), Var(ID("__MIMIR_RJ_",name)))))
          } else {
            if(allCols(name)){
              Some(ProjectArg(name, Var(name)))
            } else { 
              None
            }
          }
        }),
      Join(wrappedLHS, wrappedRHS)
    )
  }

  /**
   * Safely join two columns together, even if there's some possibility that the two
   * joins have non-overlapping columns.
   * Columns on the right-hand-side of the join will be assigned new names.
   * @param lhs     The left hand side of the join to create
   * @param rhs     The right hand side of the join to create
   * @return        A conflict-free join, and a list of renamings for the right-hand-side columns.
   */
  def makeSafeJoin(lhs: Operator, rhs: Operator): (Operator, Map[ID,ID]) = 
  {
    def lhsCols = lhs.columnNames.toSet
    def rhsCols = rhs.columnNames.toSet
    def conflicts = lhsCols & rhsCols
    logger.trace(s"Make Safe Join: $lhsCols & $rhsCols = $conflicts => \n${Join(lhs, rhs)}")
    if(conflicts.isEmpty){
      (Join(lhs, rhs), Map())
    } else {
      var currRhs = rhs
      var rewrites = List[(ID, ID)]()
      for( conflict <- conflicts ){
        val (newConflictName, newRhs) = makeColumnNameUnique(conflict, lhsCols ++ rhsCols, currRhs)
        rewrites = (conflict, newConflictName) :: rewrites
        currRhs = newRhs
      }
      logger.debug(s"   RHS Rewritten $rewrites\n$currRhs")
      (Join(lhs, currRhs), rewrites.toMap)
    }

  }

  /**
   * Alpha-rename the specified column in the specified tree.  That is, take the specified
   * operator tree and modify it so that the column `name` is a name that is not part of
   * `conflicts`.  
   * @param name        The name of the column to rewrite.
   * @param conflicts   Names to avoid renaming `name` to.
   * @param oper        The operator tree to rewrite
   * @return            A 2-tuple: The new name of the renamed column, and the new operator tree
   */
  def makeColumnNameUnique(name: ID, conflicts: Set[ID], oper: Operator): (ID, Operator) =
  {
    if(!conflicts(name)){ return (name, oper); }
    if(!oper.columnNames.exists { _.equals(name) }){ 
      throw new RAException(s"Error in makeColumnNameUnique: Wanting to rename $name in \n$oper")
    }
    val allConflicts = conflicts ++ findRenamingConflicts(name, oper)
    val newName = RandUtils.uniqueName(name, allConflicts)
    (newName, deepRenameColumn(name, newName, oper))
  }

  private def findRenamingConflicts(name: ID, oper: Operator): Set[ID] =
  {
    oper match {
      case Select(cond, src) => 
        findRenamingConflicts(name, src)
      case Project(cols, _) => 
        cols.map(_.name).toSet
      case Aggregate(gb, agg, src) => 
        gb.map(_.name).toSet ++ agg.map(_.alias).toSet ++ (
          if(gb.exists( _.name.equals(name) )){
            findRenamingConflicts(name, src)
          } else { Set() }
        )
      case Union(lhs, rhs) => 
        findRenamingConflicts(name, lhs) ++ findRenamingConflicts(name, rhs)
      case Join(lhs, rhs) => 
        findRenamingConflicts(name, lhs) ++ findRenamingConflicts(name, rhs)
      case Table(_,_,_,_) | View(_,_,_) | AdaptiveView(_,_,_,_) | HardTable(_,_) => 
        oper.columnNames.toSet
      case Sort(_, src) =>
        findRenamingConflicts(name, src)
      case Limit(_, _, src) =>
        findRenamingConflicts(name, src)
      case LeftOuterJoin(lhs, rhs, cond) =>
        findRenamingConflicts(name, lhs) ++ findRenamingConflicts(name, rhs)
      case DrawSamples(_, source, _, _) => 
        findRenamingConflicts(name, source)
    }
  }

  private def deepRenameColumn(target: ID, replacement: ID, oper: Operator): Operator =
  {
    val rewrite = (e:Expression) => Eval.inline(e, Map(target -> Var(replacement)))
    oper match {
      case Project(cols, src) => {
        Project(
          cols.map { col => 
            if(col.name.equals(target)) { ProjectArg(replacement, col.expression)}
            else { col }
          }, src)
      }
      case Aggregate(gb, aggs, src) => {
        if(gb.exists( _.name.equals(target) )){
          Aggregate(
            gb.map { col => if(col.name.equals(target)){ Var(replacement) } else { col } },
            aggs.map { agg => 
              AggFunction(
                agg.function,
                agg.distinct,
                agg.args.map(rewrite(_)),
                agg.alias
              )
            },
            deepRenameColumn(target, replacement, src)
          )
        } else {
          Aggregate(gb, 
            aggs.map { agg => 
              if(agg.alias.equals(target)){
                AggFunction(
                  agg.function,
                  agg.distinct,
                  agg.args,
                  replacement
                )
              } else { agg }
            },
            src
          )
        }
      }
      case Join(lhs, rhs) => {
        if(lhs.columnNames.exists( _.equals(target) )){
          Join(deepRenameColumn(target, replacement, lhs), rhs)
        } else {
          Join(lhs, deepRenameColumn(target, replacement, rhs))
        }
      }
      case Union(lhs, rhs) => {
        Union(
          deepRenameColumn(target, replacement, lhs),
          deepRenameColumn(target, replacement, rhs)
        )
      }
      case LeftOuterJoin(lhs, rhs, cond) => {
        if(lhs.columnNames.exists( _.equals(target) )){
          LeftOuterJoin(deepRenameColumn(target, replacement, lhs), rhs, rewrite(cond))
        } else {
          LeftOuterJoin(lhs, deepRenameColumn(target, replacement, rhs), rewrite(cond))
        }
      }
      case Table(name, source, sch, meta) => {
        Table(name, source, 
          sch.map { col => if(col._1.equals(target)) { (replacement, col._2) } else { col } },
          meta.map { col => if(col._1.equals(target)) { (replacement, col._2, col._3) } else { col } }
        )
      }
      case HardTable(sch,data) => {
        HardTable(
          sch.map { case (col, t) => 
            if(col.equals(target)) { (replacement, t) } else { (col, t) }
          }, data
        )
      }
      case View(_, _, _) | AdaptiveView(_, _, _, _) => {
        Project(
          oper.columnNames.map { col =>
            if(col.equals(target)){ ProjectArg(replacement, Var(target)) }
            else { ProjectArg(col, Var(col)) }
          },
          oper
        )
      }
      case Sort(_, _) | Select(_, _) | Limit(_, _, _) | DrawSamples(_,_,_,_) =>
        oper.
          recurExpressions(rewrite(_)).
          recur(deepRenameColumn(target, replacement, _))
    }
  }
}
