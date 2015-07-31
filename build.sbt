enablePlugins(JavaAppPackaging)

name         := """iteratorsExercise1"""
organization := "pl.bitgrind"
version      := "1.0"
scalaVersion := "2.11.5"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV       = "2.3.10"
  val akkaStreamV = "1.0-RC2"
  val scalaTestV  = "2.2.4"
  Seq(
    "com.typesafe.akka" %% "akka-actor"                           % akkaV,
    "com.typesafe.akka" %% "akka-stream-experimental"             % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-core-experimental"          % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-scala-experimental"         % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental"    % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-testkit-scala-experimental" % akkaStreamV,
    "com.typesafe.slick" %% "slick"                               % "2.1.0",
    "org.slf4j" % "slf4j-nop"                                     % "1.7.12",
    "com.h2database" % "h2"                                       % "1.3.170",
    //"com.wix" %% "accord-core"                                    % "0.4.2",
    "org.scalaz" %% "scalaz-core"                                 % "7.1.3",
    "org.scalatest" %% "scalatest"                                % scalaTestV % "test"
  )
}

Revolver.settings
