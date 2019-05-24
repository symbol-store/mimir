package mimir.metadata;

import java.sql._

import mimir.Database
import mimir.algebra._
import mimir.util.JDBCUtils
import mimir.backend.sqlite._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import com.typesafe.scalalogging.slf4j.LazyLogging

class JDBCMetadataBackend(val protocol: String, val filename: String)
  extends MetadataBackend
  with LazyLogging
{
  var conn: Connection = null
  var openConnections = 0
  var inliningAvailable = false;

  def driver() = protocol

  val tableSchemas: scala.collection.mutable.Map[ID, Seq[(ID, Type)]] = mutable.Map()
  val manyManys = mutable.Set[ID]()

  def open() = 
  {
    this.synchronized({
      assert(openConnections >= 0)
      if (openConnections == 0) {
        conn = protocol match {
          case "sqlite" =>
            Class.forName("org.sqlite.JDBC")
            val path = java.nio.file.Paths.get(filename).toString
            var c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + path)
            SQLiteCompat.registerFunctions(c)
            c

          case x =>
            println("Unsupported backend! Exiting..."); System.exit(-1); null
        }
      }

      assert(conn != null)
      openConnections = openConnections + 1
    })
  }

  def close(): Unit = {
    this.synchronized({
      if (openConnections > 0) {
        openConnections = openConnections - 1
        if (openConnections == 0) {
          conn.close()
          conn = null
        }
      }

      assert(openConnections >= 0)
      if (openConnections == 0) assert(conn == null)
    })
  }

  def query(
    query: String, 
    schema: Seq[Type],
    args: Seq[PrimitiveValue] = Seq()
  ): Seq[Seq[PrimitiveValue]] =
  {
    val stmt = conn.prepareStatement(query)
    if(args.size > 0){
      JDBCUtils.setArgs(stmt, args)
    }
    val results = stmt.executeQuery(query)
    var ret = List[Seq[PrimitiveValue]]()
    val extractRow = () => {
      schema.zipWithIndex.map { case (t, i) => JDBCUtils.convertField(t, results, i) }
    }
    while(results.next){ 
      ret = extractRow() :: ret
    }
    results.close()
    return ret.reverse
  }

  def update(
    update: String,
    args: Seq[PrimitiveValue] = Seq()
  ) {
    val stmt = conn.prepareStatement(update)
    if(args.size > 0){
      JDBCUtils.setArgs(stmt, args)
    }
    stmt.execute()
    stmt.close()
  }


  val ID_COLUMN = "MIMIR_ID"

  def registerMap(category: ID, schema: Metadata.MapSchema): MetadataMap =
  {
    // Assert that the backend schema lines up with the target
    // This should trigger a migration in the future, but at least 
    // inject a sanity check for now.
    protocol match {
      case "sqlite" => {
        // SQLite doesn't recognize anything more than the simplest possible types.
        // Type information is persisted but not interpreted, so conn.getMetaData() 
        // is useless for getting schema information.  Instead, we need to use a
        // SQLite-specific PRAGMA operation.
        SQLiteCompat.getTableSchema(conn, category) match {
          case Some(existing) => {
            assert(existing.length == schema.length)
            for(((_, e), f) <- existing.zip(schema)) {
              assert(Type.rootType(e) == f) 
            }
          }
          case None => {
            val create = s"CREATE TABLE ${category.quoted}("+
              (  
                Seq(s"${ID_COLUMN} string PRIMARY KEY NOT NULL")++
                schema.map { case (name, t) => s"${name.quoted} ${Type.rootType(t)}"}
              ).mkString(",")+
            ")"
            val stmt = conn.createStatement()
            stmt.executeUpdate(create)
            stmt.close()
          }
        }

      }
    }
    tableSchemas.put(category, schema)
    return new MetadataMap(this, category)
  }

  def keysForMap(category: ID): Seq[ID] = 
  {
    query(
      s"SELECT ${ID_COLUMN} FROM ${category.quoted}", 
      Seq(TString())
    ) .map { _(0).asString }
      .map { ID(_) }
  }
  def allForMap(category: ID): Seq[(ID, Seq[PrimitiveValue])] = 
  {
    val fields = tableSchemas.get(category).get
    query(
      "SELECT "+
        (Seq(ID_COLUMN)++fields.map { _._1.quoted }).mkString(",")+
        " FROM "+category.quoted,
      TString() +: fields.map { _._2 }
    ) .map { row => (ID(row.head.asString), row.tail) }
  }
  def getFromMap(category: ID, resource: ID): Option[Metadata.MapResource] =
  {
    val fields = tableSchemas.get(category).get
    query(
      "SELECT "+
        fields.map { _._1.quoted }.mkString(",")+
        " FROM "+category.quoted +
        " WHERE "+ID_COLUMN+" = ?",
      fields.map { _._2 },
      Seq(StringPrimitive(resource.id))
    ) .headOption
      .map { (resource, _) }
  }
  def putToMap(category: ID, resource: Metadata.MapResource)
  {
    val fields = tableSchemas.get(category).get
    update(
      s"INSERT OR REPLACE INTO ${category.quoted}("+
          ( 
            Seq(ID_COLUMN) ++ fields.map { _._1.quoted }
          ).mkString(",")+
        ") VALUES ("+( 0 until (fields.length+1) ).map { _ => "?" }.mkString(",")+")",
      StringPrimitive(resource._1.id) +: resource._2
    )
  }
  def rmFromMap(category: ID, resource: ID)
  {
    update(
      s"DELETE FROM ${category.quoted} WHERE ${ID_COLUMN} = ?",
      Seq(StringPrimitive(resource.id))
    )
  }
  def updateMap(category: ID, resource: ID, fields: Map[ID, PrimitiveValue])
  {
    val fieldSeq:Seq[(ID, PrimitiveValue)] = fields.toSeq
    update(
      s"UPDATE ${category.quoted} SET ${fieldSeq.map { _._1.quoted + " = ?" }.mkString(", ")} WHERE ${ID_COLUMN} = ?",
      fieldSeq.map { _._2 } :+ StringPrimitive(resource.id)
    )
  }

  def registerManyMany(category: ID): MetadataManyMany = 
  {
    // Assert that the backend schema lines up with the target
    // This should trigger a migration in the future, but at least 
    // inject a sanity check for now.
    protocol match {
      case "sqlite" => {
        // SQLite doesn't recognize anything more than the simplest possible types.
        // Type information is persisted but not interpreted, so conn.getMetaData() 
        // is useless for getting schema information.  Instead, we need to use a
        // SQLite-specific PRAGMA operation.
        SQLiteCompat.getTableSchema(conn, category) match {
          case Some(existing) => {
            assert(existing.length == 2)
            assert(existing(0)._1.equals(ID("LHS")))
            assert(existing(0)._2.equals(TString()))
            assert(existing(1)._1.equals(ID("RHS")))
            assert(existing(1)._2.equals(TString()))
          }
          case None => {
            val create = s"CREATE TABLE ${category.quoted}(LHS string, RHS string, PRIMARY KEY (LHS, RHS));"
            val stmt = conn.createStatement()
            stmt.executeUpdate(create)
            stmt.close()
          }
        }

      }
    }
    manyManys.add(category)
    return new MetadataManyMany(this, category)

  }
  def addToManyMany(category: ID,lhs: ID,rhs: ID)
  {
    update(
      s"INSERT INTO ${category.quoted}(LHS, RHS) VALUES (?, ?)",
      Seq(StringPrimitive(lhs.id), StringPrimitive(rhs.id))
    )
  }
  def getManyManyByLHS(category: ID,lhs: ID): Seq[ID] =
  {    
    query(
      s"SELECT RHS FROM ${category} WHERE LHS = ?",
      Seq(TString()),
      Seq(StringPrimitive(lhs.id))
    ) .map { _(0).asString }
      .map { ID(_) }
  }
  def getManyManyByRHS(category: ID,rhs: ID): Seq[ID] = 
  {
    query(
      s"SELECT LHS FROM ${category} WHERE RHS = ?",
      Seq(TString()),
      Seq(StringPrimitive(rhs.id))
    ) .map { _(0).asString }
      .map { ID(_) }
  }
  def rmByLHSFromManyMany(category: ID,lhs: ID)
  {
    update(
      s"DELETE FROM ${category.quoted} WHERE LHS = ?",
      Seq(StringPrimitive(lhs.id))
    )
  }
  def rmByRHSFromManyMany(category: ID,rhs: ID)
  {
    update(
      s"DELETE FROM ${category.quoted} WHERE RHS = ?",
      Seq(StringPrimitive(rhs.id))
    )
  }
  def rmFromManyMany(category: ID,lhs: ID,rhs: ID): Unit = 
  {
    update(
      s"DELETE FROM ${category.quoted} WHERE LHS = ? AND RHS = ?",
      Seq(StringPrimitive(lhs.id), StringPrimitive(rhs.id))
    )
  }
}