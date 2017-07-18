/* Generated By:JavaCC: Do not edit this line. MimirJSqlParserConstants.java */
/* ================================================================
 * MimirJSqlParser : java based sql parser 
 *
 * Forked from JSQLParser 
 *   by Leonardo Francalanci (leoonardoo@yahoo.it)
 *   info at: http://jsqlparser.sourceforge.net
 *
 * ************************ IMPORTANT *****************************
 * This file (MimirJsqlParser.java) is AUTOGENERATED from 
 * JSqlParserCC.jj
 * 
 * DO NOT EDIT MimirJsqlParser.java DIRECTLY!!!
 * 
 * Instead, edit JSqlParserCC.jj and use `sbt parser` to rebuild.
 * ================================================================
 */


package mimir.parser;


/**
 * Token literal values and constants.
 * Generated by org.javacc.parser.OtherFilesGen#start()
 */
public interface MimirJSqlParserConstants {

  /** End of File. */
  int EOF = 0;
  /** RegularExpression Id. */
  int K_AS = 5;
  /** RegularExpression Id. */
  int K_UNCERTAIN = 6;
  /** RegularExpression Id. */
  int K_ANALYZE = 7;
  /** RegularExpression Id. */
  int K_EXPLAIN = 8;
  /** RegularExpression Id. */
  int K_ASSUME = 9;
  /** RegularExpression Id. */
  int K_VIEW = 10;
  /** RegularExpression Id. */
  int K_LENS = 11;
  /** RegularExpression Id. */
  int K_ADAPTIVE = 12;
  /** RegularExpression Id. */
  int K_SCHEMA = 13;
  /** RegularExpression Id. */
  int K_LET = 14;
  /** RegularExpression Id. */
  int K_LOAD = 15;
  /** RegularExpression Id. */
  int K_PLOT = 16;
  /** RegularExpression Id. */
  int K_ALTER = 17;
  /** RegularExpression Id. */
  int K_SAVE = 18;
  /** RegularExpression Id. */
  int K_RENAME = 19;
  /** RegularExpression Id. */
  int K_PRAGMA = 20;
  /** RegularExpression Id. */
  int K_MATERIALIZE = 21;
  /** RegularExpression Id. */
  int K_BY = 22;
  /** RegularExpression Id. */
  int K_DO = 23;
  /** RegularExpression Id. */
  int K_IF = 24;
  /** RegularExpression Id. */
  int K_IS = 25;
  /** RegularExpression Id. */
  int K_IN = 26;
  /** RegularExpression Id. */
  int K_OR = 27;
  /** RegularExpression Id. */
  int K_OF = 28;
  /** RegularExpression Id. */
  int K_ON = 29;
  /** RegularExpression Id. */
  int K_ALL = 30;
  /** RegularExpression Id. */
  int K_AND = 31;
  /** RegularExpression Id. */
  int K_ANY = 32;
  /** RegularExpression Id. */
  int K_KEY = 33;
  /** RegularExpression Id. */
  int K_NOT = 34;
  /** RegularExpression Id. */
  int K_SET = 35;
  /** RegularExpression Id. */
  int K_ASC = 36;
  /** RegularExpression Id. */
  int K_TOP = 37;
  /** RegularExpression Id. */
  int K_END = 38;
  /** RegularExpression Id. */
  int K_DESC = 39;
  /** RegularExpression Id. */
  int K_INTO = 40;
  /** RegularExpression Id. */
  int K_NULL = 41;
  /** RegularExpression Id. */
  int K_LIKE = 42;
  /** RegularExpression Id. */
  int K_DROP = 43;
  /** RegularExpression Id. */
  int K_JOIN = 44;
  /** RegularExpression Id. */
  int K_LEFT = 45;
  /** RegularExpression Id. */
  int K_FROM = 46;
  /** RegularExpression Id. */
  int K_OPEN = 47;
  /** RegularExpression Id. */
  int K_CASE = 48;
  /** RegularExpression Id. */
  int K_WHEN = 49;
  /** RegularExpression Id. */
  int K_THEN = 50;
  /** RegularExpression Id. */
  int K_ELSE = 51;
  /** RegularExpression Id. */
  int K_SOME = 52;
  /** RegularExpression Id. */
  int K_FULL = 53;
  /** RegularExpression Id. */
  int K_WITH = 54;
  /** RegularExpression Id. */
  int K_TABLE = 55;
  /** RegularExpression Id. */
  int K_WHERE = 56;
  /** RegularExpression Id. */
  int K_USING = 57;
  /** RegularExpression Id. */
  int K_UNION = 58;
  /** RegularExpression Id. */
  int K_GROUP = 59;
  /** RegularExpression Id. */
  int K_BEGIN = 60;
  /** RegularExpression Id. */
  int K_INDEX = 61;
  /** RegularExpression Id. */
  int K_INNER = 62;
  /** RegularExpression Id. */
  int K_LIMIT = 63;
  /** RegularExpression Id. */
  int K_OUTER = 64;
  /** RegularExpression Id. */
  int K_ORDER = 65;
  /** RegularExpression Id. */
  int K_RIGHT = 66;
  /** RegularExpression Id. */
  int K_DELETE = 67;
  /** RegularExpression Id. */
  int K_CREATE = 68;
  /** RegularExpression Id. */
  int K_SELECT = 69;
  /** RegularExpression Id. */
  int K_CAST = 70;
  /** RegularExpression Id. */
  int K_PROVENANCE = 71;
  /** RegularExpression Id. */
  int K_OFFSET = 72;
  /** RegularExpression Id. */
  int K_EXISTS = 73;
  /** RegularExpression Id. */
  int K_HAVING = 74;
  /** RegularExpression Id. */
  int K_INSERT = 75;
  /** RegularExpression Id. */
  int K_UPDATE = 76;
  /** RegularExpression Id. */
  int K_VALUES = 77;
  /** RegularExpression Id. */
  int K_ESCAPE = 78;
  /** RegularExpression Id. */
  int K_PRIMARY = 79;
  /** RegularExpression Id. */
  int K_NATURAL = 80;
  /** RegularExpression Id. */
  int K_REPLACE = 81;
  /** RegularExpression Id. */
  int K_BETWEEN = 82;
  /** RegularExpression Id. */
  int K_TRUNCATE = 83;
  /** RegularExpression Id. */
  int K_DISTINCT = 84;
  /** RegularExpression Id. */
  int K_INTERSECT = 85;
  /** RegularExpression Id. */
  int K_FEEDBACK = 86;
  /** RegularExpression Id. */
  int K_EXTRACT = 87;
  /** RegularExpression Id. */
  int K_ASSIGNMENTS = 88;
  /** RegularExpression Id. */
  int S_DOUBLE = 89;
  /** RegularExpression Id. */
  int S_INTEGER = 90;
  /** RegularExpression Id. */
  int DIGIT = 91;
  /** RegularExpression Id. */
  int LINE_COMMENT = 92;
  /** RegularExpression Id. */
  int MULTI_LINE_COMMENT = 93;
  /** RegularExpression Id. */
  int S_IDENTIFIER = 94;
  /** RegularExpression Id. */
  int LETTER = 95;
  /** RegularExpression Id. */
  int SPECIAL_CHARS = 96;
  /** RegularExpression Id. */
  int S_CHAR_LITERAL = 97;
  /** RegularExpression Id. */
  int S_QUOTED_IDENTIFIER = 98;

  /** Lexical state. */
  int DEFAULT = 0;

  /** Literal token values. */
  String[] tokenImage = {
    "<EOF>",
    "\" \"",
    "\"\\t\"",
    "\"\\r\"",
    "\"\\n\"",
    "\"AS\"",
    "\"UNCERTAIN\"",
    "\"ANALYZE\"",
    "\"EXPLAIN\"",
    "\"ASSUME\"",
    "\"VIEW\"",
    "\"LENS\"",
    "\"ADAPTIVE\"",
    "\"SCHEMA\"",
    "\"LET\"",
    "\"LOAD\"",
    "\"PLOT\"",
    "\"ALTER\"",
    "\"SAVE\"",
    "\"RENAME\"",
    "\"PRAGMA\"",
    "\"MATERIALIZE\"",
    "\"BY\"",
    "\"DO\"",
    "\"IF\"",
    "\"IS\"",
    "\"IN\"",
    "\"OR\"",
    "\"OF\"",
    "\"ON\"",
    "\"ALL\"",
    "\"AND\"",
    "\"ANY\"",
    "\"KEY\"",
    "\"NOT\"",
    "\"SET\"",
    "\"ASC\"",
    "\"TOP\"",
    "\"END\"",
    "\"DESC\"",
    "\"INTO\"",
    "\"NULL\"",
    "\"LIKE\"",
    "\"DROP\"",
    "\"JOIN\"",
    "\"LEFT\"",
    "\"FROM\"",
    "\"OPEN\"",
    "\"CASE\"",
    "\"WHEN\"",
    "\"THEN\"",
    "\"ELSE\"",
    "\"SOME\"",
    "\"FULL\"",
    "\"WITH\"",
    "\"TABLE\"",
    "\"WHERE\"",
    "\"USING\"",
    "\"UNION\"",
    "\"GROUP\"",
    "\"BEGIN\"",
    "\"INDEX\"",
    "\"INNER\"",
    "\"LIMIT\"",
    "\"OUTER\"",
    "\"ORDER\"",
    "\"RIGHT\"",
    "\"DELETE\"",
    "\"CREATE\"",
    "\"SELECT\"",
    "\"CAST\"",
    "\"PROVENANCE\"",
    "\"OFFSET\"",
    "\"EXISTS\"",
    "\"HAVING\"",
    "\"INSERT\"",
    "\"UPDATE\"",
    "\"VALUES\"",
    "\"ESCAPE\"",
    "\"PRIMARY\"",
    "\"NATURAL\"",
    "\"REPLACE\"",
    "\"BETWEEN\"",
    "\"TRUNCATE\"",
    "\"DISTINCT\"",
    "\"INTERSECT\"",
    "\"FEEDBACK\"",
    "\"EXTRACT\"",
    "\"ASSIGNMENTS\"",
    "<S_DOUBLE>",
    "<S_INTEGER>",
    "<DIGIT>",
    "<LINE_COMMENT>",
    "<MULTI_LINE_COMMENT>",
    "<S_IDENTIFIER>",
    "<LETTER>",
    "<SPECIAL_CHARS>",
    "<S_CHAR_LITERAL>",
    "<S_QUOTED_IDENTIFIER>",
    "\";\"",
    "\":\"",
    "\"(\"",
    "\",\"",
    "\")\"",
    "\"=\"",
    "\".\"",
    "\"*\"",
    "\"?\"",
    "\">\"",
    "\"<\"",
    "\">=\"",
    "\"<=\"",
    "\"<>\"",
    "\"!=\"",
    "\"@@\"",
    "\"||\"",
    "\"|\"",
    "\"&\"",
    "\"+\"",
    "\"-\"",
    "\"/\"",
    "\"^\"",
    "\"{d\"",
    "\"}\"",
    "\"{t\"",
    "\"{ts\"",
    "\"{fn\"",
  };

}
