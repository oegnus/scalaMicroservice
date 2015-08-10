enablePlugins(JavaAppPackaging)

name         := """iteratorsExercise1"""
organization := "pl.bitgrind"
version      := "1.0"
scalaVersion := "2.11.6"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV       = "2.3.10"
  val akkaStreamV = "1.0-RC2"
  val scalaTestV  = "2.2.4"
  val slickV      = "2.1.0"
  val slf4jV      = "1.7.12"
  val h2V         = "1.3.170"
  val scalazV     = "7.1.3"
  Seq(
    "com.typesafe.akka" %% "akka-actor"                           % akkaV,
    "com.typesafe.akka" %% "akka-stream-experimental"             % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-core-experimental"          % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-scala-experimental"         % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental"    % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-testkit-scala-experimental" % akkaStreamV,
    "com.typesafe.slick" %% "slick"                               % slickV,
    "org.slf4j" % "slf4j-nop"                                     % slf4jV,
    "com.h2database" % "h2"                                       % h2V,
    "org.scalaz" %% "scalaz-core"                                 % scalazV,
    "org.scalatest" %% "scalatest"                                % scalaTestV % "test"
  )
}

Revolver.settings
