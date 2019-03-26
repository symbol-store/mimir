package mimir.sql;

import java.sql._
import java.util

import sparsity.statement.{Statement => SparsityStatement}
import sparsity.expression.{
  Expression  => SparsityExpression,
  LongPrimitive    => SparsityLong,
  DoublePrimitive  => SparsityDouble,
  StringPrimitive  => SparsityString,
  NullPrimitive    => SparsityNull,
  BooleanPrimitive => SparsityBool,
  PrimitiveValue   => SparsityPrimitive,
  Not              => SparsityNot,
  Function         => SparsityFunction,
  Comparison       => SparsityComparison,
  Arithmetic       => SparsityArithmetic,
  Cast             => SparsityCast,
  IsNull           => SparsityIsNull,
  Column,
  IsBetween,
  InExpression,
  CaseWhenElse
} 
import sparsity.select.{
  SelectBody,
  SelectExpression,
  SelectAll,
  SelectTable,
  FromElement,
  FromSelect,
  FromTable,
  FromJoin,
  Join => SparsityJoin
}

import mimir.Database
import mimir.algebra._
import mimir.ctables.CTPercolator
import mimir.util._
import org.joda.time.LocalDate
import com.typesafe.scalalogging.slf4j.LazyLogging

import scala.collection.JavaConversions._
import scala.collection.{immutable, mutable}
import scala.collection.mutable.ListBuffer
import mimir.provenance.Provenance

;

class SqlToRA(db: Database) 
  extends LazyLogging
{
  private val vizierNameMap = scala.collection.mutable.Map[String, ID]()
  
  def registerVizierNameMapping(vizierName:String,mimirName:ID) : Unit = {
    vizierNameMap.put(vizierName, mimirName)
  }
  
  def getVizierNameMapping(vizierName:String) : Option[ID] =  vizierNameMap.get(vizierName)

  def unhandled(feature : String) = {
    println("ERROR: Unhandled Feature: " + feature)
    throw new SQLException("Unhandled Feature: "+feature)
  }

  type TableName             = sparsity.Name
  type SparsityAttribute     = sparsity.Name
  type MimirAttribute        = ID
  type Bindings              = Map[SparsityAttribute, MimirAttribute]
  type ReverseBindings       = Map[MimirAttribute, SparsityAttribute]
  type SourceSchema          = Seq[(TableName, Seq[MimirAttribute])]
  
  def apply(v: sparsity.statement.Statement): Operator = 
    v match { 
      case sel: sparsity.statement.Select => convertSelect(sel)._1
      case _ => throw new SQLException(s"Not a Query: $v")
    }
  def apply(v: sparsity.statement.Select): Operator = convertSelect(v)._1
  def apply(v: sparsity.select.SelectBody): Operator = convertSelectBody(v)._1
  def apply(v: sparsity.expression.PrimitiveValue): PrimitiveValue = convertPrimitive(v)
  def apply(v: sparsity.expression.Expression): Expression = convertExpression(v)
  def apply(v: sparsity.expression.Expression,
            bindings: (SparsityAttribute => MimirAttribute)): Expression = convertExpression(v, bindings)

  def literalBindings(x:SparsityAttribute) = ID.upper(x)

  def convertSelect(
    s : sparsity.statement.Select, 
    alias: Option[TableName] = None
  ) : (Operator, Seq[(SparsityAttribute, MimirAttribute)]) = {
    convertSelectBody(s.body, alias)
  }
  
  /**
   * Convert a PlainSelect into an Operator + A projection map.
   */
  def convertSelectBody(
    select: sparsity.select.SelectBody, 
    tableAlias: Option[TableName] = None
  ) : (Operator, Seq[(SparsityAttribute, MimirAttribute)]) =
  {
    // Start with an empty table.  The following variable will be incrementally
    // built up over the course of this function
    var ret:Operator = HardTable(Seq(),Seq())

    //////////////////////// CONVERT FROM CLAUSE /////////////////////////////
    val fromClauses   = select.from.map { convertFromElement(_) }
    val fromOperators = fromClauses.map { _._1 }
    val fromSchemas   = fromClauses.flatMap { _._2 }

    // If there are from elements, we use those instead of the 
    // blank table.
    if(!fromOperators.isEmpty){ 
      ret = fromOperators
              .tail
              .fold(fromOperators.head) 
                   { Join(_, _) }
    }

    // Unlike SQL, Mimir's relational algebra does not use range variables.  Rather,
    // variables are renamed into the form TABLENAME_VAR to prevent name conflicts.
    // Because SQL allows variables without a matching range variable, we need to keep
    // track of the variable's "base" name (without alias) and a mapping back to the
    // variable's real name.  
    //
    // The following variables facilitate this conversion
    // 
    // Bindings: A map from the base name to the extended name (or an arbitrary
    //           name, if there are multiple source tables with the same variable)
    val bindings:Bindings = 
      fromSchemas.flatMap { _._2 }
                 .toMap

    // ReverseBindings: A map from the extended name back to the base name of the
    //                  variable (useful for inferring aliases)
    val reverseBindings:ReverseBindings = 
      fromSchemas.flatMap { _._2 }
                 .map { case (sparsityA, mimirA) => mimirA -> sparsityA }
                 .toMap

      scala.collection.mutable.Map[String, String]()
    // Sources: A map from table name to the matching set of *extended* variable names
    val sources: Seq[(TableName, Seq[MimirAttribute])] = 
      fromSchemas.map { case (table, cols) => table -> cols.map { _._2 }.toSeq }

    //////////////////////// CONVERT WHERE CLAUSE /////////////////////////////

    // This one's super simple.  The where clause just becomes a Select on top
    // of the return value.
    select.where match {
      case Some(where) => 
        ret = Select(
          convertExpression(where, bindings.toMap),
          ret
        )
      case None => ()
    }


    //////////////////// CONVERT SORT AND LIMIT CLAUSES ///////////////////////

    // Sort and limit are an odd and really really really annoying case, because of
    // how they interact with aggregate and non-aggregate queries.  
    // 
    // - For non-aggregate queries, SORT and LIMIT get applied BEFORE projection.
    //     - This is actually not entirely true... some SQL variants allow you to
    //       use names in both the target and source columns...  eeeew
    // - For aggregate queries, SORT and LIMIT get applied AFTER aggregation.
    //
    // Because they could get applied in either of two places, we don't actually
    // do the conversion here.  Instead we define the entire conversion in one
    // place (here), wrapped in a function that gets called at the right place 
    // below, once we figure out whether we're dealing with an aggregate or 
    // flat query.
    //
    val applySortAndLimit = 
    () => {
      if(!select.orderBy.isEmpty){
        ret = Sort(
          select.orderBy.map { ob => SortColumn(
            convertExpression(ob.expression, bindings.toMap), 
            ob.ascending
          )},
          ret
        )
      };
      if(!select.offset.isEmpty || !select.limit.isEmpty) {
        ret = Limit(
          select.offset.getOrElse(0),
          select.limit,
          ret
        )
      }
    }

    //////////////////////// CONVERT SELECT TARGETS /////////////////////////////

    // Utility function to compute expansions for table wildcard 
    // targets (i.e., table.*).  
    // Returns a 2-tuple: 
    //   t._1 : The base name of the variable in the output.
    //   t._2 : The expression of the variable in the input.
    // Some examples:
    //   SELECT A FROM R
    //     -> ("A", Var("A"))
    //   SELECT A AS B FROM R
    //     -> ("B", Var("A"))
    val defaultTargetsForTable:(TableName => Seq[(SparsityAttribute, sparsity.expression.Expression)]) = 
      (name: TableName) => {
        sources.find { _._1.equals(name) }
               .get._2
               .map { x => (
                  reverseBindings(x), 
                  sparsity.expression.Column(reverseBindings(x), Some(name))
                ) }
      }

    // Start by converting to Mimir expressions and expanding clauses
    // Follows the same pattern as the utility function above
    val baseTargets: Seq[(SparsityAttribute, sparsity.expression.Expression)] = 
      select.target.flatMap({
        case se@SelectExpression(expr, _) => {
          // Come up with a name for the expression
          // Note, this doesn't need to be unique (yet).  We assign unique
          // names in a post-processing step
          val alias: SparsityAttribute = SqlUtils.getAlias(se);
          Some( (alias, expr) )
        }

        case SelectAll() => 
          sources.map(_._1).flatMap { defaultTargetsForTable(_) }

        case SelectTable(table) =>
          defaultTargetsForTable(table)
      })

    // After wildcard expansion, do some post-processing on the targets.
    // First, it's possible that the aliases assigned here might contain 
    // duplicates.  Make sure that all of the aliases have unique names
    val uniqueAliases:Seq[SparsityAttribute] = 
      SqlUtils.makeAliasesUnique(baseTargets.map(_._1))

    val targetsWithUniqueAliases:Seq[(SparsityAttribute, SparsityExpression)] =
      uniqueAliases.zip(baseTargets.map(_._2))

    // We're also responsible for assigning a globally visible name based
    // on the table alias (if it's present), and retaining a mapping back
    // to the original name.  To do this, we need to create the global
    // name by prepending the table alias we've been given.  The globally
    // visible schema is what the ProjectArgs should use.
    val targets: Seq[(SparsityAttribute, MimirAttribute, SparsityExpression)] =
      tableAlias match { 
        case None =>
          // No table alias given.  The globally visible alias is just the one we picked
          targetsWithUniqueAliases.map({ case (alias, expr) => 
                                            (alias, ID.upper(alias), expr) })
        case Some(tableAlias) =>
          // Table alias exists.  The globally visible alias needs the table name prepended
          targetsWithUniqueAliases.map({ case (alias, expr) => 
                                            (alias, ID(ID.upper(tableAlias),"_",ID.upper(alias)), expr) })
      }


    // Check if this is an Aggregate Select or Flat Select
    // This is an Aggregate Select if ...
    //   ... any target column references an aggregate function
    //   ... there is a group by clause
    //   ... there is a having clause
    val containsAggregates = targets.map { _._3 }.exists { SqlUtils.expressionContainsAggregate(_, db.aggregates) }
    val hasGroupByRefs     = select.groupBy != None
    val hasHavingClause    = select.having != None
    val isAggSelect = hasGroupByRefs || hasHavingClause || containsAggregates

    if(!isAggSelect){
      // NOT an aggregate select.  

      // Apply the sort and limit clauses before the projection if necessary
      applySortAndLimit()

      // Create a simple projection around the return value.
      ret = 
        Project(
          targets.map({
            case (baseName, extendedName, inputExpr) =>
              ProjectArg(
                extendedName, 
                convertExpression(inputExpr, bindings.toMap)
              )
          }),
          ret
        )
    } else {
      // This is an aggregate select.  
      
      // It's legitimate SQL to write an aggregate query with a 
      // post-processing projection.  For example:
      // SELECT A + SUM(B) FROM R GROUP BY A
      // is entirely acceptable.  
      // 
      // Below, we've defined a function that segments this larger
      // expression, extracting the post-processing step: 
      //   (A + TMP)
      // And returning the set of actual aggregates that we need
      // to compute: 
      //   TMP -> SUM(B)

      val fragmentedTargets = 
        targets.map( tgt => fragmentAggregateExpression(tgt._3, tgt._2.withPrefix("MIMIR_AGG_"), bindings ) )

      val (targetPostProcExprs, perTargetGBVars, perTargetAggExprs) =
        fragmentedTargets.unzip3

      // The full set of referenced group by variables 
      var referencedGBVars: Set[Var] = perTargetGBVars.flatten.toSet

      // The full list of aggregate expressions we need to compute
      var allAggFunctions = 
        perTargetAggExprs.flatten
          .map({ case (aggName, distinct, args, alias) =>
            AggFunction(aggName, distinct, args.map { convertExpression(_, bindings) }, alias)
          })

      // And pull out the list of group by variables that the user has declared for
      // this expression
      val declaredGBVars: Seq[Var] = 
        select.groupBy
              .toSeq
              .flatten
              .map { convertExpression(_, bindings.toMap )}
              .map { 
                case v: Var => v
                case _ => unhandled("GroupBy[NonVar]")
              }

      // Column names in the output schema
      val targetNames = targets.map( _._2 )

      // The having clause.  "Some" if there is one, "None" if not.
      // The Boolean is TRUE if the having expression is applied to the
      // post-aggregate schema and FALSE if applied to the pre-aggregate schema
      val havingExprAndIsPostAgg: Option[(Expression, Boolean)] = 
        select.having.map { having =>
          // SQL is Ugh...
          // HAVING can be interpreted in one of two ways: Either it applies
          // to the output of the SELECT (i.e., referencing variables in the
          // output schema of the aggregate), or it can define new aggregate
          // values.  Worse, the signaling about which is which is fairly weak
          //
          // We adopt a pragmatic approach: If you use an aggregate function
          // in the HAVING clause, we assume it uses the pre-aggregate schema.
          // Otherwise it's the post-aggregate schema.

          val postAggregateBindings =
            targetNames.map( tgt => (tgt, tgt) ) ++
            declaredGBVars.map { v => (
              reverseBindings(v.name),
              v.name
            )}

          // Our first attempt at conversion: A post-aggregate
          val postAggregateHavingExpr: Option[Expression] =
            // Notably... the conversion could also fail due to missing
            // bindings.  This is our backup signal to fail over.
            ///
            // TODO: It might be useful if we had a function to do the 
            // AggFunction test prior to conversion.  That is, if we
            // had a version of expressionContainsAggregate that worked
            // on SQL Expressions.
            try {
              if(SqlUtils.expressionContainsAggregate(having, db.aggregates)){ None }
              else { Some(convertExpression(having, postAggregateBindings.toMap)) }
            } catch { case e: SQLException => None }

          // At this point, if `postAggregateHavingExpr` has something, then
          // it's safe to convert it directly.  Otherwise, we need to do some 
          // finagling to create temporary attributes in the aggregate.
          postAggregateHavingExpr match {
            case Some(havingExpr) => (havingExpr, true) 

            case None => {
              // If we're here, it means that we're dealing with a pre-aggregate
              // HAVING expression.  Use the original bindings to convert the
              // expression.  If this fails, the SQL actually does have an error.
              val (havingPostExpression, havingGBVars, havingAggExprs) =
                fragmentAggregateExpression(having, ID("MIMIR_HAVING"), bindings)

              // Tack on the newly generated aggregate expressions
              referencedGBVars = referencedGBVars ++ havingGBVars
              allAggFunctions = allAggFunctions ++ 
                havingAggExprs.map({ case (aggName, distinct, args, alias) =>
                  AggFunction(aggName, distinct, args.map { convertExpression(_, bindings) }, alias)
                })

              val havingBindings: Bindings = (
                declaredGBVars.map { gb => reverseBindings(gb.name) -> gb.name } ++
                allAggFunctions.map { fn => fn.alias.quoted -> fn.alias }
              ).toMap

              // And then the PostExpression is what we want to use in the
              // Select() that gets attached to the query.
              /* return */ (convertExpression(havingPostExpression, havingBindings), false)
            }
          }


        } 

      // Sanity Check: We should not be referencing a variable that's not in the GB list.
      val referencedNonGBVars = referencedGBVars -- declaredGBVars
      if(!referencedNonGBVars.isEmpty){
        throw new SQLException(s"Variables $referencedNonGBVars not in group by list")
      }

      val interAggregateBindings = (
        declaredGBVars.map { gb => reverseBindings(gb.name) -> gb.name } ++
        allAggFunctions.map { fn => fn.alias.quoted -> fn.alias }
      ).toMap

      // Assemble the Aggregate
      ret = Aggregate(declaredGBVars, allAggFunctions, ret)

      // Generate the post-processing projection targets
      val postProcTargets = 
        targetNames.zip(targetPostProcExprs).
          map( tgt => ProjectArg(tgt._1, convertExpression(tgt._2, interAggregateBindings)) )

      // If the having clause is applied to the pre-aggregate schema,
      // then we need to apply it before projecting down to the post-aggregate
      // schema.
      havingExprAndIsPostAgg match {
        case Some( (havingExpr, false) ) => {
          ret = Select(havingExpr, ret)
        }
        case _ => ()
      }

      // Assemble the post-processing Project
      ret = Project(postProcTargets, ret)

      // If the having clause is applied to the post-aggregate schema,
      // then we need to apply it after projecting down to the post-aggregate
      // schema.
      havingExprAndIsPostAgg match {
        case Some( (havingExpr, true) ) => {
          ret = Select(havingExpr, ret)
        }
        case _ => ()
      }

      // Apply sort and limit if necessary
      applySortAndLimit()
    }

    //////////////////////////// Convert Distinct /////////////////////////////
    if(select.distinct) {
      ret = ret.distinct
    }

    // We're responsible for returning bindings for this specific
    // query, so extract those from the target expressions we
    // produced earlier
    val returnedBindings =
      targets.map( tgt => (tgt._1, tgt._2) )

    // The base operator should now be fully assembled.  Recur into any 
    // unions if necessary and return.
    select.union match {
      case Some( (t, rhs) ) => {
        val (rhsOper, rhsBindings) = convertSelectBody(rhs, tableAlias)

        //TODO: Check that returnedBindings == rhsBindings

        ret = Union(ret, rhsOper)
        t match {
          case sparsity.select.Union.Distinct => ret = ret.distinct
          case sparsity.select.Union.All      => ()
        }
      }
      case None => ()
    }

    return (ret, returnedBindings)
  }

  def convertFromElement(
    from : FromElement
  ) : (Operator, Seq[(TableName, Bindings)]) =
    from match {
      case FromSelect(query, alias) => {
        val (ret, bindings) = convertSelectBody(query, Some(alias))
        (
          ret, 
          Seq(alias -> bindings.toMap)
        )
      }
      case FromTable(schemaMaybe, table, aliasMaybe) => {
        val alias = aliasMaybe.getOrElse(table)
        val tableOp:Operator = 
          schemaMaybe match { 
            case None => 
              if(db.metadataTables.contains(ID.upper(table.name))) {
                db.metadataTable(ID.upper(table.name), ID.upper(alias))
              } else {
                if(table.quoted) { db.table(ID(table.name), ID.upper(alias)) }
                else             { db.table(table.name,     ID.upper(alias)) }
              }

            case Some(schema) =>
              db.adaptiveSchemas.viewFor(ID.upper(schema), ID.upper(table)).get
          }
        val bindings = 
          tableOp.columnNames.map { col => sparsity.Name(col.id) -> ID(ID.upper(alias), "_", col) }
        val rewrites =
          tableOp.columnNames.map { col => ID(ID.upper(alias), "_", col) -> Var(col) }
        logger.trace(s"From Table: $table( $rewrites )")
        (
          tableOp.mapByID( rewrites:_* ),
          Seq(alias -> bindings.toMap)
        )
      }
      case FromJoin(lhsFrom, rhsFrom, t, onClause, aliasMaybe) => {
        val (lhs, lhsAliasedBindings) = convertFromElement(lhsFrom)
        val (rhs, rhsAliasedBindings) = convertFromElement(rhsFrom)
        val bindings = (lhsAliasedBindings++rhsAliasedBindings).flatMap { _._2 }
        val onExpr = convertExpression(onClause, bindings.toMap)
        val joinOp = 
          t match {
            case SparsityJoin.Inner => 
              lhs.join(rhs).filter { onExpr }
            case SparsityJoin.LeftOuter =>
              LeftOuterJoin(lhs, rhs, onExpr)
            case SparsityJoin.RightOuter =>
              LeftOuterJoin(rhs, lhs, onExpr)
            case SparsityJoin.FullOuter =>
              unhandled("FromElement[FullOuterJoin]")
          }

        aliasMaybe match { 
          case None =>
            (
              joinOp,
              lhsAliasedBindings ++ rhsAliasedBindings
            )
          case Some(alias) => {
            val newBindings = 
              joinOp.columnNames.map { col => sparsity.Name(col.id) -> ID(ID.upper(alias), "_", col) }
            val rewrites =
              bindings.map { case (_, id) => ID(ID.upper(alias), "_", id) -> Var(id) }
            (
              joinOp.mapByID( rewrites:_* ),
              Seq(alias -> bindings.toMap)
            )
          }
        }
      }

    }
  
  // 
  def convertPrimitive(e: SparsityPrimitive) : PrimitiveValue =
  {
    e match { 
      case SparsityLong(v)   => return IntPrimitive(v)
      case SparsityDouble(v) => return FloatPrimitive(v)
      case SparsityString(v) => return StringPrimitive(v)
      case SparsityBool(v)   => return BoolPrimitive(v)
      case SparsityNull()    => return NullPrimitive()
    }    
  }

  def convertExpression(
    e : sparsity.expression.Expression, 
    bindings: (SparsityAttribute => MimirAttribute) = Map()
  ) : Expression = {
    e match {
      case prim: SparsityPrimitive => convertPrimitive(prim)
      case SparsityNot(child) => 
        return Not(convertExpression(child, bindings))
      case SparsityComparison(lhs, op, rhs) => 
        Comparison(op, convertExpression(lhs, bindings), 
                       convertExpression(rhs, bindings))
      case SparsityArithmetic(lhs, op, rhs) => 
        Arithmetic(op, convertExpression(lhs, bindings), 
                       convertExpression(rhs, bindings))
      
      case col:Column => return convertColumn(col, bindings)

      case SparsityFunction(rawName, Some(args), false) => 
        // Special-case some function conversions
        (ID.lower(rawName), args) match {
          case (ID("rowid"), Seq()) => 
            RowIdVar()
          case (ID("rowid"), Seq(x:SparsityPrimitive)) => 
            RowIdPrimitive(convertPrimitive(x).asString)
          case (name, _) => 
            Function(name, 
              args.map { 
                convertExpression(_, bindings)
              } 
            )
        }

      case SparsityFunction(_, _, _) =>
        throw new SQLException("Error: Can't interpret aggregate function in normal expression")

      case SparsityCast(target, t) =>
        CastExpression(
          convertExpression(target, bindings), 
          Type.fromString(t.lower)
        )

      case IsBetween(lhsRaw, lowRaw, highRaw) => {
        val lhs  = convertExpression(lhsRaw, bindings)
        val low  = convertExpression(lowRaw, bindings)
        val high = convertExpression(highRaw, bindings)
        Arithmetic(
          Arith.And,
          Comparison(Cmp.Lte, low, lhs),
          Comparison(Cmp.Lte, lhs, high)
        )
      }

      case InExpression(targetRaw, Left(options)) => 
        val target = convertExpression(targetRaw, bindings)
        ExpressionUtils.makeOr(
          options.map { convertExpression(_, bindings) }
                 .map { Comparison(Cmp.Eq, target, _) }
        )

      case InExpression(targetRaw, Right(query)) =>
        unhandled("InExpression[Query]")

      case CaseWhenElse(targetRaw, whenClauses, elseClause) => {
        val inlineSwitch: Expression => Expression = 
          targetRaw match { 
            case None => (x => x)
            case Some(target) => Comparison(Cmp.Eq, convertExpression(target, bindings), _)
          }
        return ExpressionUtils.makeCaseExpression(
          whenClauses.map { case (condition, thenClause) => 
            ( 
              inlineSwitch(convertExpression(condition, bindings)),
              convertExpression(thenClause, bindings)
            )
          },
          convertExpression(elseClause, bindings)
        )
      }

      case SparsityIsNull(target) => 
        IsNullExpression( convertExpression(target, bindings) )

    }
  }

  def convertColumn(c: Column, bindings: (SparsityAttribute => MimirAttribute)): Var =
  {
    c.table match {
      case None => 
        val binding = 
          try {
            bindings(c.column);
          } catch {
            case _:NoSuchElementException => 
              throw new SQLException(s"Unknown Variable: ${c.column} in ${bindings}")
          }
        return Var(binding)
      case Some(table) => 
        return Var(ID(ID.upper(table), "_", ID.upper(c.column)))
    }
  }

  /**
   * Split an aggregate target expression into its component parts.
   * 
   * This is needed to handle complex aggregate expressions.
   * For example (if X is a group-by var):
   * > X + SUM(Y) / COUNT(*) AS FOO
   * This expression will return:
   * > X + (FOO_1_0 / FOO_1_1)
   * > [X]
   * > [(SUM, [Y], FOO_1_0), (COUNT, [], FOO_1_1)]
   *
   * In short, this function descends through the expression tree
   * and picks out all aggregates and var leaves that it hits.
   *
   * Aggregate expressions are removed from the nested expression
   * and replaced by unique placeholder variables (as long as 
   * alias is a unique prefix), and the entire expression is 
   * reassembled.  
   * 
   * - Raw Variables are returned in the second tuple element
   * - Aggregates are returned in the third tuple element
   * 
   * Unique placeholder variables are assigned unique names based
   * on the path through the operator tree.
   */
  def fragmentAggregateExpression(
    expr: sparsity.expression.Expression, 
    alias: MimirAttribute,
    bindings: (SparsityAttribute => MimirAttribute)
  ): (
    SparsityExpression,                                      // The wrapper expression
    Set[Var],                                                // Referenced Group-By Variables
    Seq[(ID,Boolean,Seq[SparsityExpression],MimirAttribute)] // Referenced Expressions (fn, distinct, args, alias)
  ) =  {
    logger.debug(s"Fragmenting: $alias <- $expr")
    val recur = () => {
      val fragmentedChildren = 
        expr.children.zipWithIndex.
          map { case (child, index) => 
                  fragmentAggregateExpression(child, ID(alias,"_"+index), bindings) }

      val (childExprs, childGBVars, childAggs) =
        fragmentedChildren.unzip3

      (
        expr.rebuild(childExprs), 
        childGBVars.flatten.toSet,
        childAggs.flatten
      )
    }

    expr match {
      case c:Column => 
        (
          c, 
          Set(convertColumn(c, bindings)), 
          List()
        )

      case SparsityFunction(function, args, distinct) 
            if distinct || db.aggregates.isAggregate(ID.lower(function))
          => 
        ( 
          Column(alias.quoted, None), 
          Set(),
          Seq((
            ID.lower(function), 
            distinct,
            args.toSeq.flatten,
            alias
          ))
        )

      case _ => recur()
    }
  }

}
