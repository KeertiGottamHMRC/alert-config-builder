import sbt._

object LibDependencies {

  val compile = Seq(
    "org.scala-lang" % "scala-reflect" % "2.11.7",
    "org.reflections" % "reflections" % "0.9.9-RC1",
    "io.spray" %% "spray-json" % "1.3.2",
    "org.yaml" % "snakeyaml" % "1.17"
  )

  val test = Seq(
    "org.json4s" %% "json4s-jackson" % "3.6.0" % "test",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test",
    "org.pegdown" % "pegdown" % "1.5.0" % "test"
  )

}
