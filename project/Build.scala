import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "modulesManager"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      // Add your project dependencies here,
      "postgresql" % "postgresql" % "9.1-901.jdbc4",
      // https://github.com/typesafehub/play-plugins/tree/master/mailer
      "com.typesafe" %% "play-plugins-mailer" % "2.0.2",
      // https://github.com/mumoshu/play2-memcached
      "com.github.mumoshu" %% "play2-memcached" % "0.2-SNAPSHOT"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      // Add your own project settings here
      lessEntryPoints <<= baseDirectory(_ / "app" / "assets" / "stylesheets" ** "style.less"),
      routesImport += "models.QueryBinders._",
      // required to resolve `spymemcached` for Memcached
      resolvers += "Sonatype OSS Snapshots Repository" at "http://oss.sonatype.org/content/groups/public",
      resolvers += "Spy Repository" at "http://files.couchbase.com/maven2"
    )

}
