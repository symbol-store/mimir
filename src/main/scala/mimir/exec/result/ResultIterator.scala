package mimir.exec.result

import mimir.algebra._

trait ResultIterator
  extends Iterator[Row]
{
  def tupleSchema: Seq[(ID, Type)]
  def annotationSchema: Seq[(ID, Type)]
  def schema = tupleSchema

  lazy val schemaLookup: Map[ID, (Int, Type)] = 
    tupleSchema.zipWithIndex.map { case ((name, t), idx) => (name -> (idx, t)) }.toMap
  lazy val annotationsLookup: Map[ID, (Int, Type)] = 
    annotationSchema.zipWithIndex.map { case ((name, t), idx) => (name -> (idx, t)) }.toMap

  def hasAnnotation(annotation: ID): Boolean = annotationsLookup contains annotation;

  def getTupleIdx(name: ID): Int = schemaLookup(name)._1
  def getAnnotationIdx(name: ID): Int = annotationsLookup(name)._1

  def close(): Unit

  def hasNext(): Boolean
  def next(): Row

  override def toSeq: ResultSeq = 
    new ResultSeq(super.toIndexedSeq, tupleSchema, annotationSchema)
  override def toIndexedSeq: ResultSeq = 
    new ResultSeq(super.toIndexedSeq, tupleSchema, annotationSchema)
  override def toList: List[Row] = 
    this.toIndexedSeq.toList

  def tuples = map { _.tuple }.toIndexedSeq
}