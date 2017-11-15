name := "meetup-santiagoscala-reactivestreams-live"

version := "1.0"

scalaVersion := "2.12.4"

resolvers += "Local Maven Repository" at Path.userHome.asFile.toURI.toURL + ".m2/repository"

val akkaActorV = "2.5.6"
val akkaHttpV = "10.0.10"

// reactivestreams
libraryDependencies += "org.reactivestreams" % "reactive-streams" % "1.0.1"
libraryDependencies += "org.reactivestreams" % "reactive-streams-tck" % "1.0.1" % Test

// base de datos embedida
libraryDependencies += "org.hsqldb" % "hsqldb" % "2.4.0" % Test

// jdbc
libraryDependencies += "com.zaxxer" % "HikariCP" % "2.7.3"
libraryDependencies += "org.flywaydb" % "flyway-core" % "4.2.0"

// config
libraryDependencies += "com.typesafe" % "config" % "1.3.2"

// log
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2"
libraryDependencies += "org.slf4j" % "slf4j-log4j12" % "1.7.25"

// akka streams
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaActorV
libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % akkaActorV

// test
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % Test
