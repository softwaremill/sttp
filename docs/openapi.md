# OpenApi

Sttp request definitions can be automatically generated from server's openapi description 
using scala-sttp-openapi generator.

For sttp-openapi generator's configuration options refer to: https://openapi-generator.tech/docs/generators/scala-sttp

## Standalone setup

This is the simplest setup which relay on calling openapi-generator manually and generating complete sbt project from it.

First, you will need to install/download openapi-generator. Follow openapi's [official documentation](https://github.com/OpenAPITools/openapi-generator#1---installation) on how to do this.
Keep in mind that scala-sttp generator is available only since v5.0.0-beta. 

Next, call the generator with the following options:
```bash
openapi-generator-cli generate \
  -i petstore.yaml \
  --generator-name scala-sttp \
  -o samples/client/petstore/
```

## Sbt managed

In this setup openapi-generator is plugged into sbt project through [sbt-openapi-generator](https://github.com/OpenAPITools/sbt-openapi-generator/) plugin.
Sttp requests and models are automatically generated upon compilation.

To have your openapi descriptions automatically turned into classes first define a new module in your project:
```scala
lazy val petstoreApi: Project = project
  .in(file("petstore-api"))
  .settings(
    openApiInputSpec := s"${baseDirectory.value.getPath}/petstore.yaml",
    openApiGeneratorName := "scala-sttp",
    openApiOutputDir := baseDirectory.value.name,
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client" %% "core" % "2.2.0",
      "com.softwaremill.sttp.client" %% "json4s" % "2.2.0",
      "org.json4s" %% "json4s-jackson" % "3.6.8"
    )
  )
```
As this will generate code into `petstore-api/src` you might want to add this folder to the `.gitignore`. 

Since this plugin is still in a very early stage it requires some additional configuration.

First we need to connect generation with compilation. 
Add following line into petstore module settings:
```scala
    (compile in Compile) := ((compile in Compile) dependsOn openApiGenerate).value,
```

Now we have to attach our generated source code directory into cleaning process.
Add following line into petstore module settings:
```scala
    cleanFiles += baseDirectory.value / "src"
```

Last but not least we need to tell openapi-generator not to generate whole project but only the source files:
Add following line into petstore module settings:
```scala
    openApiIgnoreFileOverride := s"${baseDirectory.in(ThisBuild).value.getPath}/openapi-ignore-file",
```
and create `openapi-ignore-file` file in project's root directory with following content:
```
*
**/*
!**/src/main/scala/**/*
```

Final petstore module configuration:
```scala
lazy val petstoreApi: Project = project
  .in(file("petstore-api"))
  .settings(
    openApiInputSpec := s"${baseDirectory.value.getPath}/petstore.yaml",
    openApiGeneratorName := "scala-sttp",
    openApiOutputDir := baseDirectory.value.name,
    openApiIgnoreFileOverride := s"${baseDirectory.in(ThisBuild).value.getPath}/openapi-ignore-file",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client" %% "core" % "2.2.0",
      "com.softwaremill.sttp.client" %% "json4s" % "2.2.0",
      "org.json4s" %% "json4s-jackson" % "3.6.8"
    ),
    (compile in Compile) := ((compile in Compile) dependsOn openApiGenerate).value,
    cleanFiles += baseDirectory.value / "src"
  )
```

Full demo project is available on [github](https://github.com/softwaremill/sttp-openapi-example)

### Additional notes

Although recent versions of IntellijIdea IDE come with "OpenApi Specification" plugin bundled into it, this plugin doesn't seem to support 
latest versions of generator and so, it is impossible to generate sttp bindings from it. 
