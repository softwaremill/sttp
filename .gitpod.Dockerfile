FROM gitpod/workspace-full
USER gitpod
RUN brew install coursier/formulas/coursier sbt scalaenv
RUN sudo env "PATH=$PATH" coursier bootstrap org.scalameta:scalafmt-cli_2.12:2.7.5 \
  -r sonatype:snapshots \
  -o /usr/local/bin/scalafmt --standalone --main org.scalafmt.cli.Cli
RUN scalaenv install scala-2.12.12 && scalaenv global scala-2.12.12
RUN bash -cl "set -eux \
    version=0.9.7 \
    coursier fetch \
        org.scalameta:metals_2.12:$version \
        org.scalameta:mtags_2.13.4:$version \
        org.scalameta:mtags_2.12.12:$version \
        org.scalameta:mtags_2.11.12:$version"
