# Repository Guidelines

## Project Structure & Module Organization
The application is a Gradle-based Spring Boot service targeting Java 21. Production code lives under `src/main/java/com/luziatcode/demoworkflowengine`, grouped by concern:

- `controller/`: HTTP APIs for workflow definitions and executions
- `service/`: application services and workflow runtime logic
- `service/workflow/domain/`: workflow model classes
- `service/workflow/action/`: node action implementations
- `repository/`: in-memory repositories used by the demo engine

Configuration is in `src/main/resources/application.yml`. Tests mirror the main package structure under `src/test/java`.

## Build, Test, and Development Commands
- `./gradlew test`: compile and run the full JUnit 5 test suite
- `./gradlew compileJava`: compile production code only
- `./gradlew bootRun`: start the app locally with Spring Boot

Run commands from the repository root. Prefer `./gradlew test` before opening a PR.

## Coding Style & Naming Conventions
Use 4-space indentation and standard Java naming:

- classes: `WorkflowEngine`, `WorkflowExecutionService`
- methods: `run`, `markStopping`, `selectOutputs`
- tests: descriptive `camelCase`, such as `runSupportsN8nConnectionsWithParallelBranchesAndMerge`

Keep controllers thin, place workflow behavior in `service/workflow`, and keep domain models simple. Lombok is already used for boilerplate reduction. Add comments only when they explain intent, not mechanics.

## Testing Guidelines
This project uses JUnit 5 via `spring-boot-starter-webmvc-test`. Add or update tests for every engine or domain behavior change. Keep test data close to the test, often as inline JSON strings for workflow definitions. Mirror the package under test and name test classes `*Test`.

## Commit & Pull Request Guidelines
Recent commits use short, direct subjects, often in imperative form or brief Korean notes, for example:

- `Add workflow stop support`
- `todo.md 업데이트`

Prefer one-line commit subjects that describe the behavior change clearly. For pull requests, include:

- a short summary of the change
- impacted API or workflow behavior
- test results, for example `./gradlew test`
- sample request/response JSON when controller contracts change

## Workflow Engine Notes
The engine now supports n8n-style `connections` JSON in addition to legacy edge-based definitions. New work should prefer `connections` and node-driven flow control (`IF`, `MERGE`, `LOOP`) over edge conditions.
