package mimir.views

import mimir.Database
import mimir.algebra._
import mimir.data.ViewSchemaProvider

class TemporaryViewManager(db: Database)
  extends scala.collection.mutable.LinkedHashMap[ID, Operator]
  with ViewSchemaProvider
{
  def listTables: Iterable[ID] = keys
  def tableSchema(table: ID): Option[Seq[(ID, Type)]] = 
    get(table).map { db.typechecker.schemaOf(_) }
  def view(table: ID) = get(table).get
}

object TemporaryViewManager
{
  val SCHEMA = ID("TEMPORARY")
}