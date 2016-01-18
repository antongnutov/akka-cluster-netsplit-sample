import sbt._

object SampleBuild extends Build {
  lazy val sample = Project("sample", file("."))
}