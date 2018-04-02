scalaVersion := "2.12.2"

//cancelable in Global := true

// https://mvnrepository.com/artifact/org.jitsi/ice4j
libraryDependencies += "org.jitsi" % "ice4j" % "1.0"

val akkaVersion = "2.4.17"

val akkaLibraries = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "com.google.protobuf" % "protobuf-java" % "2.4.1" % Runtime
)

libraryDependencies ++= akkaLibraries

val kamonVersion = "0.6.5"
val kamonOldVersion = "0.6.3"

val kamonLibraries = Seq(
  "io.kamon" %% "kamon-core" % kamonVersion,
  "io.kamon" %% "kamon-scala" % kamonVersion,
  "io.kamon" %% "kamon-log-reporter" % kamonVersion,
  "org.aspectj" %  "aspectjweaver" % "1.8.10"
)

libraryDependencies ++= kamonLibraries

enablePlugins(JavaServerAppPackaging,RpmPlugin,UniversalPlugin,LinuxPlugin)

name := "turn-tester"

version := "0.1"

bashScriptExtraDefines += """addJava "-javaagent:${lib_dir}/org.aspectj.aspectjweaver-1.8.10.jar""""

publishArtifact in (Compile, packageDoc) := false

publishArtifact in packageDoc := false

publishArtifact in packageSrc := false

sources in (Compile,doc) := Seq.empty
