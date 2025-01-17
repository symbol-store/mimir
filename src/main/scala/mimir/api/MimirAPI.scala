package mimir.api

import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.SecureRequestCustomizer
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.http.HttpVersion
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.server.handler.ResourceHandler
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.server.handler.DefaultHandler
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.webapp.WebAppContext
import java.sql.SQLException

import org.apache.commons.text.WordUtils

import com.typesafe.scalalogging.LazyLogging

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import play.api.libs.json._
import mimir.algebra.RAException
import java.io.FileNotFoundException
import java.io.EOFException

object MimirAPI extends LazyLogging {
  
  var isRunning = true
  val DEFAULT_API_PORT = 8089
  
  def runAPIServerForViztrails(port: Int = DEFAULT_API_PORT) : Unit = {
    val server = new Server(port)
    val http_config = new HttpConfiguration();
    server.addConnector(new ServerConnector( server,  new HttpConnectionFactory(http_config)) );
    
    val contextHandler = buildSwaggerUI()
    
    val resource_handler2 = new ResourceHandler()
		resource_handler2.setDirectoriesListed(true)
	  //println(s"${new java.io.File("./client/target/scala-2.12/scalajs-bundler").getAbsolutePath()}")
		resource_handler2.setResourceBase("./src")
		val contextHandler2 = new ContextHandler("/src");
    contextHandler2.setResourceBase("./src");
    contextHandler2.setHandler(resource_handler2);
     
    val servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
    servletContextHandler.setContextPath("/");
    val holder = new ServletHolder(new MimirVizierServlet());
    servletContextHandler.addServlet(holder, "/*");
	  
    val handlerList = new HandlerCollection();
    handlerList.setHandlers( Array[Handler](contextHandler, contextHandler2, servletContextHandler, new DefaultHandler()));
    
    server.setHandler(handlerList);
    server.start()
     
    println(s"Mimir API Server Started on http://localhost:$port/...")
     while(isRunning){
       Thread.sleep(90000)
       
     }
     Thread.sleep(1000)
     server.stop();
    
  }
  
  def buildSwaggerUI(): ContextHandler = {
    val rh = new ResourceHandler()
		rh.setDirectoriesListed(true)
		rh.setResourceBase("./src/main/resources/api-docs")
    val context = new ContextHandler("/api-docs");
    context.setResourceBase("./src/main/resources/api-docs");
    context.setHandler(rh);
    context
  }
}

class MimirVizierServlet() extends HttpServlet with LazyLogging {
    override def doPost(req : HttpServletRequest, resp : HttpServletResponse) = {
        val text = scala.io.Source.fromInputStream(req.getInputStream).mkString 
        println(s"MimirAPI POST ${req.getPathInfo}\n$text")
        val routePattern = "\\/api\\/v2(\\/[a-zA-Z\\/]+)".r
        val os = resp.getOutputStream()
        resp.setHeader("Content-type", "text/json");
        req.getPathInfo match {
          case routePattern(route) => {
            try{
              route match {
                case "/eval/scala" => {
                  Json.parse(text).as[CodeEvalRequest].handle(os)
                }
                case "/eval/R" => {
                  Json.parse(text).as[CodeEvalRequest].handle(os)
                }
                case "/dataSource/load" => {
                  Json.parse(text).as[LoadRequest].handle(os)
                }
                case "/dataSource/unload" => {
                  Json.parse(text).as[UnloadRequest].handle(os)
                }
                case "/lens/create" => {
                  Json.parse(text).as[CreateLensRequest].handle(os)
                }
                case "/view/create" => {
                  val cvJson = Json.parse(text)
                  if(cvJson.\("input").get.isInstanceOf[JsString])
                    cvJson.as[CreateViewSRequest].handle(os)
                  else
                    cvJson.as[CreateViewRequest].handle(os)
                }
                case "/view/sample" => {
                  Json.parse(text).as[CreateSampleRequest].handle(os)
                }
                case "/adaptive/create" => {
                  Json.parse(text).as[CreateAdaptiveSchemaRequest].handle(os)
                }
                case "/annotations/noschema" => {
                  Json.parse(text).as[ExplainSubsetWithoutSchemaRequest].handle(os)
                }
                case "/annotations/schema" => {
                  Json.parse(text).as[ExplainSchemaRequest].handle(os)
                }
                case "/annotations/cell" => {
                  Json.parse(text).as[ExplainCellSchemaRequest].handle(os)
                }
                case "/annotations/subset" => {
                  Json.parse(text).as[ExplainSubsetRequest].handle(os)
                }
                case "/annotations/all" => {
                  Json.parse(text).as[ExplainEverythingAllRequest].handle(os)
                }
                case "/annotations/summary" => {
                  Json.parse(text).as[ExplainEverythingRequest].handle(os)
                }
                case "/annotations/feedback" => {
                  Json.parse(text).as[FeedbackForReasonRequest].handle(os)
                }
                case "/query/data" => {
                  Json.parse(text).as[QueryMimirRequest].handle(os)
                }
                case "/schema" => {
                  Json.parse(text).as[SchemaForQueryRequest].handle(os)
                }
              }
            } catch {
              case e: EOFException => 
                val message = 
                os.write(Json.stringify(Json.toJson(ErrorResponse(
                      e.getClass.getCanonicalName(),
                      "Expected a longer file: "+e.getMessage(), 
                      e.getStackTrace.map(_.toString).mkString("\n")
                    ))).getBytes)
      
              case e: FileNotFoundException =>
                os.write(Json.stringify(Json.toJson(ErrorResponse(
                      e.getClass.getCanonicalName(),
                      "File or URL not found: "+e.getMessage(), 
                      e.getStackTrace.map(_.toString).mkString("\n")
                    ))).getBytes)
      
              case e: SQLException =>
                os.write(Json.stringify(Json.toJson(ErrorResponse(
                      e.getClass.getCanonicalName(),
                      WordUtils.wrap(e.getMessage(), 100), 
                      e.getStackTrace.map(_.toString).mkString("\n")
                    ))).getBytes)
                logger.debug(e.getMessage + "\n" + e.getStackTrace.map(_.toString).mkString("\n"))
      
              case e: RAException =>
                os.write(Json.stringify(Json.toJson(ErrorResponse(
                      e.getClass.getCanonicalName(),
                      WordUtils.wrap(e.getMessage(), 100), 
                      e.getStackTrace.map(_.toString).mkString("\n")
                    ))).getBytes)
                logger.debug(e.getMessage + "\n" + e.getStackTrace.map(_.toString).mkString("\n"))
      
              case e: Throwable => {
                os.write(Json.stringify(Json.toJson(ErrorResponse(
                      e.getClass.getCanonicalName(),
                      WordUtils.wrap(e.getMessage(), 100),//"An unknown error occurred...", 
                      e.getStackTrace.map(_.toString).mkString("\n")
                    ))).getBytes)
                logger.error("MimirAPI POST ERROR: ", e)
              }
            }  
          }
          case _ => {
            os.write(Json.stringify(Json.toJson(
                    ErrorResponse("MimirAPI POST Not Handled","Unknown Request:"+ req.getPathInfo, 
                        Thread.currentThread().getStackTrace.map(_.toString).mkString("\n") ))).getBytes)
            logger.error(s"MimirAPI POST Not Handled: ${req.getPathInfo}")
          }
        } 
        os.flush()
        os.close() 
    }
    override def doGet(req : HttpServletRequest, resp : HttpServletResponse) = {
      println(s"MimirAPI GET ${req.getPathInfo}")
        
      val routePattern = "\\/api\\/v2(\\/[a-zA-Z\\/]+)".r
        req.getPathInfo match {
          case routePattern(route) => {
            try{
              val os = resp.getOutputStream()
              resp.setHeader("Content-type", "text/json");
              route match {
                case "/lens" => {
                  os.write(Json.stringify(Json.toJson(LensList(mimir.MimirVizier.getAvailableLenses()))).getBytes )
                }
                case "/adaptive" => {
                  os.write(Json.stringify(Json.toJson(AdaptiveSchemaList(mimir.MimirVizier.getAvailableAdaptiveSchemas()))).getBytes )
                }
              }
              os.flush()
              os.close() 
            } catch {
              case t: Throwable => {
                logger.error("MimirAPI GET ERROR: ", t)
                throw t
              }
            }
          }
          case _ => {
            logger.error(s"MimirAPI GET Not Handled: ${req.getPathInfo}")
            throw new Exception("request Not handled: " + req.getPathInfo)
          }
        }  
    }
  }
