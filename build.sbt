name := "akka-cluster-netsplit-sample"

version := "0.1"

organization in ThisBuild := "sample"

scalaVersion := "2.11.7"

val akkaVersion = "2.4.2"
val log4j2Version = "2.5"

libraryDependencies ++= Seq(
    // Akka
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,

    // Reactive Stream Dependencies
    "com.typesafe.akka" %% "akka-http-core" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,

    // JSON
    "org.json4s" %% "json4s-jackson" % "3.3.0",

    // Logging
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j2Version,
    "org.apache.logging.log4j" % "log4j-core" % log4j2Version,
    "org.apache.logging.log4j" % "log4j-api" % log4j2Version,
    "com.lmax" % "disruptor" % "3.3.2",

    // Test Dependencies
    "org.scalatest" %% "scalatest" % "2.2.5" % "test",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion % "test",
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test",
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
    "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % "test"
  )

enablePlugins(JavaAppPackaging)

// Bash Script config
bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../conf/app.conf""""
bashScriptExtraDefines += """addJava "-Dlog4j.configurationFile=${app_home}/../conf/log4j2.xml""""

bashScriptExtraDefines += """addJava "-Dcom.sun.management.jmxremote.port=1099""""
bashScriptExtraDefines += """addJava "-Dcom.sun.management.jmxremote.authenticate=false""""
bashScriptExtraDefines += """addJava "-Dcom.sun.management.jmxremote.ssl=false""""

// Bat Script config
batScriptExtraDefines += """set _JAVA_OPTS=%_JAVA_OPTS% -Dconfig.file=%AKKA_CLUSTER_NETSPLIT_SAMPLE_HOME%\\conf\\app.conf"""
batScriptExtraDefines += """set _JAVA_OPTS=%_JAVA_OPTS% -Dlog4j.configurationFile=%AKKA_CLUSTER_NETSPLIT_SAMPLE_HOME%\\conf\\log4j2.xml"""

batScriptExtraDefines += """set _JAVA_OPTS=%_JAVA_OPTS% -Dcom.sun.management.jmxremote.port=1099"""
batScriptExtraDefines += """set _JAVA_OPTS=%_JAVA_OPTS% -Dcom.sun.management.jmxremote.authenticate=false"""
batScriptExtraDefines += """set _JAVA_OPTS=%_JAVA_OPTS% -Dcom.sun.management.jmxremote.ssl=false"""

// Testing Data
fork in Test := true

SbtMultiJvm.multiJvmSettings

parallelExecution in Test := false
compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test)

executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
    case (testResults, multiNodeResults) =>
        val overall = if (testResults.overall.id < multiNodeResults.overall.id) {
            multiNodeResults.overall
        } else {
            testResults.overall
        }
        Tests.Output(overall,
            testResults.events ++ multiNodeResults.events,
            testResults.summaries ++ multiNodeResults.summaries)
}

configs(MultiJvm)