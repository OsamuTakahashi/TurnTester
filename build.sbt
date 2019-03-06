scalaVersion := "2.12.2"

//cancelable in Global := true

// https://mvnrepository.com/artifact/org.jitsi/ice4j
libraryDependencies += "org.jitsi" % "ice4j" % "1.0"

val akkaVersion = "2.5.6"

val akkaLibraries = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "com.google.protobuf" % "protobuf-java" % "2.4.1" % Runtime
)

libraryDependencies ++= akkaLibraries

//val kamonVersion = "0.6.5"
//val kamonOldVersion = "0.6.3"
//
//val kamonLibraries = Seq(
//  "io.kamon" %% "kamon-core" % kamonVersion,
//  "io.kamon" %% "kamon-scala" % kamonVersion,
//  "io.kamon" %% "kamon-log-reporter" % kamonVersion,
//  "org.aspectj" %  "aspectjweaver" % "1.8.10"
//)
//
//libraryDependencies ++= kamonLibraries

enablePlugins(JavaAppPackaging,RpmPlugin,UniversalPlugin,LinuxPlugin)

name := "turn-tester"

organization := "com.sopranoworks"

version := "0.2-SNAPSHOT"

//bashScriptExtraDefines += """addJava "-javaagent:${lib_dir}/org.aspectj.aspectjweaver-1.8.10.jar""""

bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../conf/application.conf""""

//publishArtifact in (Compile, packageDoc) := false
//
//publishArtifact in packageDoc := false
//
//publishArtifact in packageSrc := false
//
//sources in (Compile,doc) := Seq.empty

mappings in (Compile, packageBin) ~= { (ms: Seq[(File, String)]) =>
  ms filterNot { case (file, dest) =>
    dest.contains("application.conf")
  }
}

pomExtra :=
  <url>https://github.com/OsamuTakahashi/TurnTester</url>
    <licenses>
      <license>
        <name>MIT</name>
        <url>https://opensource.org/licenses/MIT</url>
      </license>
    </licenses>
    <scm>
      <url>https://github.com/OsamuTakahashi/TurnTester</url>
      <connection>https://github.com/OsamuTakahashi/TurneTester.git</connection>
    </scm>
    <developers>
      <developer>
        <id>OsamuTakahashi</id>
        <name>Osamu Takahashi</name>
        <url>https://github.com/OsamuTakahashi/</url>
      </developer>
    </developers>
