# sttp - Scala HTTP client

## Build commands
- Compile all: `sbt compile`
- Compile specific Scala version/platform: `sbt "compileScoped 3 JVM"` (options: 2.12, 2.13, 3 / JVM, JS, Native)
- Test all: `sbt test`
- Test specific: `sbt "testScoped 3 JVM"`
- Compile docs: `sbt compileDocs`

## Project structure
- Multi-module sbt project using ProjectMatrix for cross-compilation
- Scala versions: 2.12, 2.13, 3
- Platforms: JVM, JS, Native
- Core module in `core/`, backends in dedicated directories (e.g., `okhttp-backend/`, `http4s-backend/`)
- JSON support modules in `json/`
- Effects modules in `effects/` (cats, zio, fs2, monix, ox)

## Testing
- Tests require a test server running (started automatically via `testServerSettings`)
- Use `sbt "testScoped <version> <platform>"` for targeted testing

## Code style
- Follow existing patterns in the codebase
- Use `%%%` for cross-platform dependencies, `%%` for JVM-only
