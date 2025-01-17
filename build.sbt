import scala.sys.process._

name := "Mimir-Core"
version := "0.3.2"
organization := "info.mimirdb"
scalaVersion := "2.12.10"

dependencyOverrides += "org.scala-lang" % "scala-library" % scalaVersion.value

// Needed to avoid cryptic EOFException crashes in forked tests
// in Travis with `sudo: false`.
// See https://github.com/sbt/sbt/issues/653
// and https://github.com/travis-ci/travis-ci/issues/3775
javaOptions ++= Seq("-Xmx8G" )


scalacOptions ++= Seq(
  "-feature"
)

unmanagedResourceDirectories in Compile += baseDirectory.value / "lib_extra"
includeFilter in (Compile, unmanagedResourceDirectories):= ".dylib,.dll,.so"
unmanagedClasspath in Runtime += baseDirectory.value / "conf"
unmanagedResourceDirectories in Compile += baseDirectory.value / "conf"

fork := true
outputStrategy in run := Some(StdoutOutput)
connectInput in run := true
cancelable in Global := true
javaOptions ++= Seq(
  "-Dcom.github.fommil.netlib.BLAS=com.github.fommil.netlib.F2jBLAS", 
  "-Dcom.github.fommil.netlib.LAPACK=com.github.fommil.netlib.F2jLAPACK", 
  "-Dcom.github.fommil.netlib.ARPACK=com.github.fommil.netlib.F2jARPACK"
  // ,"-agentlib:jdwp=transport=dt_shmem,address=jdbconn,server=y,suspend=n"
)
scalacOptions in Test ++= Seq("-Yrangepos")
parallelExecution in Test := false
testOptions in Test ++= Seq( Tests.Argument("junitxml"), Tests.Argument("console") )
mainClass in Compile := Some("mimir.Mimir")

//if you want to debug tests uncomment this
//javaOptions += "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"

lazy val runMimirVizier = inputKey[Unit]("run MimirVizier")
runMimirVizier := {
  val args = sbt.complete.Parsers.spaceDelimited("[main args]").parsed
  val classpath = (fullClasspath in Compile).value
  val classpathString = Path.makeString(classpath map { _.data })
  val debugTestJVMArgs = Seq()//Seq("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
  val jvmArgs = debugTestJVMArgs ++ Seq("-Xmx4g", "-Dcom.github.fommil.netlib.BLAS=com.github.fommil.netlib.F2jBLAS", "-Dcom.github.fommil.netlib.LAPACK=com.github.fommil.netlib.F2jLAPACK", "-Dcom.github.fommil.netlib.ARPACK=com.github.fommil.netlib.F2jARPACK")
  val (jh, os, bj, bd, jo, ci, ev) = (javaHome.value, outputStrategy.value, Vector[java.io.File](), 
		Some(baseDirectory.value), (jvmArgs ++ Seq("-classpath", classpathString)).toVector, connectInput.value, sys.props.get("os.name") match {
	  	//case Some(osname) if osname.startsWith("Mac OS X") =>  Map(("DYLD_INSERT_LIBRARIES",System.getProperty("java.home")+"/lib/libjsig.dylib"))
	  	case Some(osname) if osname.startsWith("Mac OS X") => sys.props.get("os.arch") match {
	  		case Some(osarch) if osarch.endsWith("64") => Map(("LD_PRELOAD_64",System.getProperty("java.home")+"/lib/libjsig.dylib"))
	  		case Some(osarch) => Map(("LD_PRELOAD",System.getProperty("java.home")+"/lib/libjsig.dylib"))
	  		case None => envVars.value
	  	}
	  	case Some(otherosname) => sys.props.get("os.arch") match {
	  		case Some(osarch) if osarch.endsWith("64") => Map(("LD_PRELOAD_64",System.getProperty("java.home")+"/lib/"+System.getProperty("os.arch")+"/libjsig.so"))
	  		case Some(osarch) => Map(("LD_PRELOAD",System.getProperty("java.home")+"/lib/"+System.getProperty("os.arch")+"/libjsig.so")) 
	  		case None => envVars.value
	  	}
	  	case None => envVars.value
	  })
  Fork.java(
    ForkOptions(jh, os, bj, bd, jo, ci, ev),
    "mimir.MimirVizier" +: args
  )
}

lazy val runTestResults = inputKey[Unit]("run runTestResults")
runTestResults := {
  val args = sbt.complete.Parsers.spaceDelimited("[main args]").parsed
  val classpath = (fullClasspath in Compile).value
  val classpathString = Path.makeString(classpath map { _.data })
  val debugTestJVMArgs = Seq()//Seq("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
  val jvmArgs = debugTestJVMArgs ++ Seq("-Xmx4g", "-Dcom.github.fommil.netlib.BLAS=com.github.fommil.netlib.F2jBLAS", "-Dcom.github.fommil.netlib.LAPACK=com.github.fommil.netlib.F2jLAPACK", "-Dcom.github.fommil.netlib.ARPACK=com.github.fommil.netlib.F2jARPACK")
  val (jh, os, bj, bd, jo, ci, ev) = (javaHome.value, outputStrategy.value, Vector[java.io.File](), 
		Some(baseDirectory.value), (jvmArgs ++ Seq("-classpath", classpathString)).toVector, 
		connectInput.value, envVars.value)
  Fork.java(
    ForkOptions(jh, os, bj, bd, jo, ci, ev),
    "mimir.util.TestResults" +: args
  )
}

lazy val runBackup = inputKey[Unit]("run runBackup")
runBackup := {
  val args = sbt.complete.Parsers.spaceDelimited("[main args]").parsed
  val classpath = (fullClasspath in Compile).value
  val classpathString = Path.makeString(classpath map { _.data })
  val debugTestJVMArgs = Seq()//Seq("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
  val jvmArgs = debugTestJVMArgs ++ Seq("-Xmx4g", "-Dcom.github.fommil.netlib.BLAS=com.github.fommil.netlib.F2jBLAS", "-Dcom.github.fommil.netlib.LAPACK=com.github.fommil.netlib.F2jLAPACK", "-Dcom.github.fommil.netlib.ARPACK=com.github.fommil.netlib.F2jARPACK")
  val (jh, os, bj, bd, jo, ci, ev) = (javaHome.value, outputStrategy.value, Vector[java.io.File](), 
		Some(baseDirectory.value), (jvmArgs ++ Seq("-classpath", classpathString)).toVector, 
		connectInput.value, envVars.value)
  Fork.java(
    ForkOptions(jh, os, bj, bd, jo, ci, ev),
    "mimir.util.BackupUtils" +: args
  )
}

//for tests that need to run in their own jvm because they need specific envArgs or otherwise
testGrouping in Test := {
	val (jh, os, bj, bd, jo, ci, ev) = (javaHome.value, outputStrategy.value, Vector[java.io.File](), 
		baseDirectory.value, javaOptions.value.toVector, connectInput.value, envVars.value)
	val testsToForkSeperately = Seq("mimir.algebra.gprom.OperatorTranslationSpec","mimir.demo.MimirGProMDemo")
	val gpromTestsForkEnvArgs = sys.props.get("os.name") match {
	  	case Some(osname) if osname.startsWith("Mac OS X") => Map(("DYLD_INSERT_LIBRARIES",System.getProperty("java.home")+"/lib/libjsig.dylib"))
	  	case Some(otherosname) => Map(("LD_PRELOAD",System.getProperty("java.home")+"/lib/"+System.getProperty("os.arch")+"/libjsig.so"))
	  	case None => envVars.value
	  }
	val seperateForkedEnvArgs = Map(("mimir.algebra.gprom.OperatorTranslationSpec", gpromTestsForkEnvArgs), ("mimir.demo.MimirGProMDemo", gpromTestsForkEnvArgs))
	val (forkedTests, otherTests) = (definedTests in Test).value.partition { test => testsToForkSeperately.contains(test.name) }
    Seq(Tests.Group(name = "Single JVM tests", tests = otherTests, runPolicy = Tests.SubProcess(
	    ForkOptions( jh, os, bj, Some(bd), jo, ci, ev)
	    ))) ++ forkedTests.map { test =>
	  Tests.Group(name = test.name, tests = Seq(test), runPolicy = Tests.SubProcess(
	    ForkOptions( jh, os, bj, Some(bd), jo, ci, seperateForkedEnvArgs.getOrElse(test.name, ev))
	    ))
	}
}


resolvers += "MimirDB" at "https://maven.mimirdb.info/"
resolvers += "MVNRepository" at "https://mvnrepository.com/artifact/"
resolvers ++= Seq("snapshots", "releases").map(Resolver.sonatypeRepo)
resolvers += Resolver.mavenLocal

updateOptions := updateOptions.value.withGigahorse(false)

dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core" % "2.10.0"
dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.10.0"
dependencyOverrides += "com.fasterxml.jackson.module" % "jackson-module-scala_2.12" % "2.10.0"

libraryDependencies ++= Seq(
  ////////////////////// Command-Line Interface Utilities //////////////////////
  "org.rogach"                    %%  "scallop"                  % "3.1.3",
  "org.jline"                     %   "jline"                    % "3.2.0",
  "info.mimirdb"                  %%  "sparsity"                 % "1.7.0",
  "com.lihaoyi"                   %%  "fastparse"                % "2.1.0",
  "org.apache.commons"            %   "commons-text"             % "1.8",

  ////////////////////// Dev Tools -- Logging, Testing, etc... //////////////////////
  "com.typesafe.scala-logging"    %%  "scala-logging"            % "3.9.2",
  "ch.qos.logback"                %   "logback-classic"          % "1.2.3",
  "org.specs2"                    %%  "specs2-core"              % "4.6.0" % "test",
  "org.specs2"                    %%  "specs2-matcher-extra"     % "4.6.0" % "test",
  "org.specs2"                    %%  "specs2-junit"             % "4.6.0" % "test",
  "org.clapper"                   %%  "classutil" 				 % "1.1.2",
  "com.amazonaws"                 %   "aws-java-sdk-core"        % "1.11.234",
  "com.amazonaws" 				  %   "aws-java-sdk-s3" 		 % "1.11.234",
  //"ch.cern.sparkmeasure" 		  %%  "spark-measure" 			 % "0.13",
  "org.scala-lang" 				  %   "scala-compiler" 		 	 % "2.12.7",
  "org.ddahl" 					  %%  "rscala" 					 % "3.2.15",
  
  //////////////////////// Data Munging Tools //////////////////////
  "com.github.nscala-time"        %%  "nscala-time"              % "2.20.0",
  "org.apache.lucene"             %   "lucene-spellchecker"      % "3.6.2",
  "org.apache.servicemix.bundles" %   "org.apache.servicemix.bundles.collections-generic" 
                                                                 % "4.01_1",
  "org.scala-lang.modules"        %%  "scala-parser-combinators" % "1.0.6",
  "org.apache.commons"            %   "commons-csv"              % "1.4",
  "commons-io"                    %   "commons-io"               % "2.5",
  "com.github.wnameless"          %   "json-flattener"           % "0.2.2",
  "com.typesafe.play"             %%  "play-json"                % "2.7.0-M1"  excludeAll( ExclusionRule("com.fasterxml.jackson.core")),
  "technology.tabula" 			  %	  "tabula" 					 % "1.0.3",
  "org.apache.commons" 			  %   "commons-compress" 	 	 % "1.19",
  
  //////////////////////// Lens Libraries //////////////////////
  // WEKA - General-purpose Classifier Training/Deployment Library
  // Used by the imputation lens
   ("nz.ac.waikato.cms.weka"       %   "weka-stable"              % "3.8.1").
     exclude("nz.ac.waikato.cms.weka",  "weka-dev").
     exclude("nz.ac.waikato.cms.weka.thirdparty", "java-cup-11b-runtime"),
   ("nz.ac.waikato.cms.moa"        %   "moa"                      % "2014.11").
     exclude("nz.ac.waikato.cms.weka",  "weka-dev").
     exclude("nz.ac.waikato.cms.weka.thirdparty", "java-cup-11b-runtime"),
    
  //spark ml
  "org.apache.spark"         %   "spark-sql_2.12"          % "2.4.4",// excludeAll(ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12"), ExclusionRule("org.apache.hadoop")),
  "org.apache.spark"         %   "spark-mllib_2.12"         % "2.4.4",// excludeAll(ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12"), ExclusionRule("org.apache.hadoop")),
  "org.apache.spark"         %   "spark-hive_2.12"        % "2.4.4",// excludeAll(ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12"), ExclusionRule("org.apache.hadoop")),
  "com.databricks"           %   "spark-xml_2.12"            % "0.9.0",// excludeAll(ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12"), ExclusionRule("org.apache.hadoop")),
  "com.sun.xml.txw2"         %   "txw2"                      % "20110809",
  "com.crealytics"           %%  "spark-excel"                    % "0.12.0",// excludeAll(ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12"), ExclusionRule("org.apache.hadoop")),
  //"com.google.api.client"    %   "google-api-client-json"         % "1.2.2-alpha",
  "org.apache.hadoop"        %   "hadoop-client"          % "2.8.2" exclude("org.slf4j", "slf4j-log4j12"),
  "org.apache.hadoop"        %   "hadoop-aws"             % "2.8.2" exclude("org.slf4j", "slf4j-log4j12"),
  "net.java.dev.jets3t"      %   "jets3t"               % "0.9.4",
  ("com.google.api-client"   %   "google-api-client"             % "1.30.9").exclude("com.google.guava", "guava-jdk5"),
  "com.google.oauth-client"  %   "google-oauth-client-jetty"     % "1.30.6",
  "com.google.apis"          %   "google-api-services-sheets"    % "v4-rev610-1.25.0",
  "org.datasyslab"           %   "geospark"                      % "1.2.0",
  "org.datasyslab"           %   "geospark-sql_2.3"                  % "1.2.0",
  "com.fasterxml.jackson.core" % "jackson-core"                  % "2.10.0",
  
  
  //////////////////////// Jung ////////////////////////
  // General purpose graph manipulation library
  // Used to detect and analyze Functional Dependencies
  "net.sf.jung"              %   "jung-graph-impl"          % "2.0.1",
  "net.sf.jung"              %   "jung-algorithms"          % "2.0.1",
  "net.sf.jung"              %   "jung-visualization"       % "2.0.1",
  "jgraph"                   %   "jgraph"                   % "5.13.0.0",
  "javax.measure"            %   "jsr-275"          % "0.9.1",


  //////////////////////// JDBC Backends //////////////////////
  "org.xerial"               %   "sqlite-jdbc"              % "3.16.1",
  "org.postgresql"           %   "postgresql" 				 % "9.4-1201-jdbc41",
  /// Explicitly not including MySQL, since it's GPL-licensed.  If you want 
  /// to use MySQL, you're free to compile your own version of Mimir.

  ///////////////////// Viztrails Integration ///////////////////
  
  "org.eclipse.jetty"			  %    "jetty-http" 		  % "9.4.10.v20180503",
  "org.eclipse.jetty" 			  %    "jetty-io" 			  % "9.4.10.v20180503",
  "org.eclipse.jetty" 			  %    "jetty-security" 	  % "9.4.10.v20180503",
  "org.eclipse.jetty" 			  %    "jetty-server" 		  % "9.4.10.v20180503",
  "org.eclipse.jetty" 			  %    "jetty-servlet" 		  % "9.4.10.v20180503" ,
  "org.eclipse.jetty" 			  %    "jetty-servlets" 	  % "9.4.10.v20180503" ,
  "org.eclipse.jetty" 			  %    "jetty-util" 		  % "9.4.10.v20180503" ,
  "org.eclipse.jetty"        	  %    "jetty-webapp"         % "9.4.10.v20180503" ,
			
  //////////////////////// Visualization //////////////////////
  // For now, all of this happens in python with matplotlib
  // and so we don't need any external dependencies.
  //"org.vegas-viz"                 %%  "vegas"                 % "0.3.9",
  //"org.sameersingh.scalaplot"     % "scalaplot"               % "0.0.4",

  //////////////////////// Linear Solver /////////////////////////
  "com.github.vagmcs"       %% "optimus"                % "3.1.0",
  "com.github.vagmcs"       %% "optimus-solver-oj"      % "3.1.0"
)


////// Generate a Coursier Bootstrap Jar
// See https://get-coursier.io/docs
// 
lazy val bootstrap = taskKey[Unit]("Generate Bootstrap Jar")
bootstrap := {
  val logger = ProcessLogger(println(_), println(_))
  val coursier_bin = "bin/coursier"
  val coursier_url = "https://git.io/coursier-cli"
  val mimir_bin = "bin/mimir"
  if(!java.nio.file.Files.exists(java.nio.file.Paths.get("bin/coursier"))){

    println("Downloading Coursier...")
    Process(List(
      "curl", "-L",
      "-o", coursier_bin,
      coursier_url
    )) ! logger match {
      case 0 => 
      case n => sys.error(s"Could not download Coursier")
    }
    Process(List(
      "chmod", "+x", coursier_bin
    )) ! logger
    println("... done")
  }

  println("Coursier available.  Generating Repository List")

  val resolverArgs = resolvers.value.map { 
    case r: MavenRepository => Seq("-r", r.root)
  }.flatten

  val (art, file) = packagedArtifact.in(Compile, packageBin).value
  val qualified_artifact_name = file.name.replace(".jar", "").replaceFirst("-([0-9.]+)$", "")
  val full_artifact_name = s"${organization.value}:${qualified_artifact_name}:${version.value}"
  println("Rendering bootstraps for "+full_artifact_name)
  for(resolver <- resolverArgs){
    println("  "+resolver)
  }
  println
  println("Generating Mimir binary")

  Process(List(
    coursier_bin,
    "bootstrap",
    full_artifact_name,
    "-f",
    "-o", "bin/mimir",
    "-r", "central"
  )++resolverArgs) ! logger match {
      case 0 => 
      case n => sys.error(s"Bootstrap failed")
  }
  

  println("Generating Mimir-API Server binary")
  Process(List(
    coursier_bin,
    "bootstrap",
    full_artifact_name,
    "-f",
    "-o", "bin/mimir-api",
    "-r", "central",
    "-M", "mimir.MimirVizier"
  )++resolverArgs) ! logger match {
      case 0 => 
      case n => sys.error(s"Bootstrap failed")
  }
}

////// Assembly Plugin //////
// We use the assembly plugin to create self-contained jar files
// https://github.com/sbt/sbt-assembly

test in assembly := {}
assemblyJarName in assembly := "Mimir.jar"
mainClass in assembly := Some("mimir.Mimir")
val nettyMeta = ".*META-INF\\/io\\.netty.*".r
assemblyMergeStrategy in assembly := {
  case PathList("org","aopalliance", xs @ _*) => MergeStrategy.last
  case PathList("javax", "inject", xs @ _*) => MergeStrategy.last
  case PathList("javax", "servlet", xs @ _*) => MergeStrategy.last
  case PathList("javax", "activation", xs @ _*) => MergeStrategy.last
  case PathList("org", "apache", xs @ _*) => MergeStrategy.last
  case PathList("io", "netty", xs @ _*) => MergeStrategy.last
  case PathList("com", "google", xs @ _*) => MergeStrategy.last
  case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last
  case PathList("com", "codahale", xs @ _*) => MergeStrategy.last
  case PathList("com", "yammer", xs @ _*) => MergeStrategy.last
  case PathList("ch", "qos", xs @ _*) => MergeStrategy.first
  case PathList("org", "slf4j", xs @ _*) => MergeStrategy.first
  case PathList("org", "codehaus", xs @ _*) => MergeStrategy.last
  case PathList("com", "googlecode", xs @ _*) => MergeStrategy.last
  case "overview.html" => MergeStrategy.rename
  case "about.html" => MergeStrategy.rename
  case "META-INF/ECLIPSEF.RSA" => MergeStrategy.last
  case "META-INF/mailcap" => MergeStrategy.last
  case "META-INF/mimetypes.default" => MergeStrategy.last
  case nettyMeta() => MergeStrategy.last
  case "plugin.properties" => MergeStrategy.last
  case "log4j.properties" => MergeStrategy.last
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case x => MergeStrategy.first
}


////// Publishing Metadata //////
// use `sbt publish make-pom` to generate 
// a publishable jar artifact and its POM metadata

publishMavenStyle := true

pomExtra := <url>http://mimirdb.info</url>
  <licenses>
    <license>
      <name>Apache License 2.0</name>
      <url>http://www.apache.org/licenses/</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:ubodin/mimir.git</url>
    <connection>scm:git:git@github.com:ubodin/mimir.git</connection>
  </scm>

/////// Publishing Options ////////
// use `sbt publish` to update the package in 
// your own local ivy cache
publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))

/*/////// Docker Image Creation Options ////////
// use `sbt docker` to generate docker image
import sbtdocker._
enablePlugins(DockerPlugin)
dockerfile in docker := {
    val userDataVolMountPoint = "/usr/local/source/"
    val sbtPath = s"${userDataVolMountPoint}sbt/bin/sbt"
	val initContainer = Seq(
		s"if [ -e ${userDataVolMountPoint}initComplete ]",
		"then", 
		    "echo 'already initialized...'",
			s"(cd ${userDataVolMountPoint}mimir; git pull; ${sbtPath} run)",
		"else",
			s"""curl -sL "https://github.com/sbt/sbt/releases/download/v0.13.15/sbt-0.13.15.tgz" | gunzip | tar -x -C ${userDataVolMountPoint}""",
			s"chmod 0755 ${sbtPath}",
			s"git clone https://github.com/UBOdin/mimir.git ${userDataVolMountPoint}mimir",
			s"(cd ${userDataVolMountPoint}mimir; ${sbtPath} compile; ${sbtPath} compile)",
			s"touch ${userDataVolMountPoint}initComplete",
			"echo 'initialization complete...'",
			s"(cd ${userDataVolMountPoint}mimir; git pull; ${sbtPath} run)",
		"fi"
		)
		
	val instructions = Seq(
	  sbtdocker.Instructions.From("frolvlad/alpine-oraclejdk8"),
	  sbtdocker.Instructions.Volume(Seq(s"type=volume,source=mimir-vol,target=${userDataVolMountPoint}")),
	  sbtdocker.Instructions.Run.exec(Seq("apk", "add", "--no-cache", "bash")),
	  sbtdocker.Instructions.Run.exec(Seq("apk", "add", "--no-cache", "curl")),
	  sbtdocker.Instructions.Run.exec(Seq("apk", "add", "--no-cache", "git")),
	  sbtdocker.Instructions.Run.exec(Seq("mkdir", s"${userDataVolMountPoint}")),
	  sbtdocker.Instructions.Run(s"""( ${initContainer.map(el => s"""echo "$el" >> ${userDataVolMountPoint}initContainer.sh; """).mkString("") } chmod 0755 ${userDataVolMountPoint}initContainer.sh)"""),
	  sbtdocker.Instructions.EntryPoint.exec(Seq("/bin/bash", "-c", s"${userDataVolMountPoint}initContainer.sh"))
	)
	Dockerfile(instructions)
}*/
