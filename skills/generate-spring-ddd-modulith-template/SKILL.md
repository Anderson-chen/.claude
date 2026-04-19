---
name: generate-spring-ddd-modulith-template
description: >
  Scaffold a production-ready Spring Boot 4.x multi-module DDD project using Gradle (Groovy DSL).
  Generates the full directory structure, all Gradle build files, and template source code following
  Hexagonal Architecture + Domain-Driven Design + Spring Modulith patterns.
  Use this skill whenever the user asks to: scaffold a Spring Boot DDD project, generate a DDD template,
  create a multi-module Spring Boot project, set up a Spring Modulith project, create a Spring Boot
  hexagonal architecture project, or initialize a Gradle multi-module Spring project.
  Also trigger when the user says things like "幫我建立 Spring DDD 專案", "產生 Spring Boot 多模組架構",
  or any variation involving Spring Boot + DDD + Gradle scaffold/template.
---

# Spring Boot DDD Multi-Module Scaffold Generator

Scaffold a complete Gradle multi-module project following 2026 industry best practices:
**Hexagonal Architecture + DDD + Spring Modulith** on Spring Boot 4.x / JDK 25 / PostgreSQL.

## Step 1 — Collect Parameters

If the user hasn't provided them, ask for:

| Parameter | Default | Example |
|---|---|---|
| `groupId` | (required) | `com.example` |
| `artifactId` | (required) | `shop` |
| `boundedContexts` | (required) | `orders,catalog,inventory` |
| `basePackage` | `{groupId}.{artifactId}` | `com.example.shop` |
| `description` | `""` | `"E-Commerce Platform"` |

With these in hand, proceed to generation immediately — do not ask more questions.

## Step 2 — Generate the Project

Generate **every file listed below** with full content. Use the templates in `references/` as the
starting point and substitute placeholders throughout:

| Placeholder | Meaning | Example |
|---|---|---|
| `{GROUP}` | groupId | `com.example` |
| `{ARTIFACT}` | artifactId | `shop` |
| `{BASE_PKG}` | base package | `com.example.shop` |
| `{BASE_PATH}` | package as filesystem path | `com/example/shop` |
| `{CONTEXT}` | bounded context name, **lowercase** | `orders` |
| `{CONTEXT_CAP}` | bounded context, PascalCase | `Orders` |
| `{CONTEXT_LOWER}` | same as `{CONTEXT}` — explicit alias used in URL paths | `orders` |
| `{ENTITY}` | primary aggregate name, PascalCase — derive singular from context | `Order` |
| `{ENTITY_LOWER}` | camelCase of entity | `order` |

Generate contexts **in parallel** — produce all files for each context simultaneously.

## Step 3 — File Tree to Generate

> **Architecture note**: This structure uses `adapter/inbound` and `adapter/outbound` naming (Hexagonal Architecture)
> instead of a flat `infrastructure/` bucket. This makes the direction of dependency explicit:
> inbound adapters DRIVE the application; outbound adapters are DRIVEN BY it.
> The `shared-kernel/` module (not `commons/`) is intentionally minimal — only cross-cutting DDD
> base types go here, not utilities or DTOs.

```
{ARTIFACT}/
├── gradlew                                      ← [gradle-templates.md § gradlew]
├── gradlew.bat                                  ← [gradle-templates.md § gradlew-bat]
├── settings.gradle                              ← [gradle-templates.md § settings]
├── build.gradle                                 ← [gradle-templates.md § root-build]
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.properties            ← [gradle-templates.md § wrapper-properties]
│   │   └── gradle-wrapper.jar                   ← binary; run `gradle wrapper` or `./gradlew wrapper` to generate
│   └── libs.versions.toml                       ← [gradle-templates.md § version-catalog]
├── build-logic/                                 ← Convention plugins (included build)
│   ├── settings.gradle                          ← [gradle-templates.md § build-logic-settings]
│   ├── build.gradle                             ← [gradle-templates.md § build-logic-build]
│   └── src/main/groovy/
│       ├── java-common-conventions.gradle       ← [gradle-templates.md § convention-java]
│       ├── spring-module-conventions.gradle     ← [gradle-templates.md § convention-spring]
│       └── library-conventions.gradle          ← [gradle-templates.md § convention-library]
├── shared-kernel/                               ← ★ Shared Kernel — AggregateRoot, DomainEvent, ValueObject ONLY
│   ├── build.gradle                             ← [gradle-templates.md § shared-kernel-build]
│   └── src/main/java/{BASE_PATH}/shared/
│       ├── domain/
│       │   ├── AggregateRoot.java               ← [domain-templates.md § aggregate-root-base]
│       │   ├── DomainEvent.java                 ← [domain-templates.md § domain-event-base]
│       │   └── ValueObject.java                 ← [domain-templates.md § value-object-base]
│       └── infrastructure/
│           └── jpa/
│               └── AuditableEntity.java         ← [infrastructure-templates.md § auditable]
├── {CONTEXT}/                                   ← One per bounded context
│   ├── build.gradle                             ← [gradle-templates.md § context-build]
│   └── src/
│       ├── main/
│       │   ├── java/{BASE_PATH}/{CONTEXT}/
│       │   │   ├── package-info.java            ← [domain-templates.md § package-info]  ★ Spring Modulith API declaration
│       │   │   │
│       │   │   ├── domain/                      ← Pure Java — zero Spring/JPA annotations
│       │   │   │   ├── model/
│       │   │   │   │   ├── {ENTITY}.java        ← [domain-templates.md § aggregate-root]
│       │   │   │   │   ├── {ENTITY}Id.java      ← [domain-templates.md § entity-id]
│       │   │   │   │   └── Money.java           ← [domain-templates.md § value-object] (only in first context)
│       │   │   │   ├── event/
│       │   │   │   │   └── {ENTITY}CreatedEvent.java ← [domain-templates.md § domain-event]
│       │   │   │   └── repository/
│       │   │   │       └── {ENTITY}Repository.java   ← [domain-templates.md § repository-port]
│       │   │   │
│       │   │   ├── application/                 ← Use cases; minimal Spring (@Service, @Transactional)
│       │   │   │   ├── port/
│       │   │   │   │   ├── in/
│       │   │   │   │   │   ├── Create{ENTITY}Command.java  ← [application-templates.md § command]
│       │   │   │   │   │   └── Create{ENTITY}UseCase.java  ← [application-templates.md § use-case]
│       │   │   │   │   └── out/
│       │   │   │   │       ├── Save{ENTITY}Port.java       ← [application-templates.md § save-port]
│       │   │   │   │       └── Find{ENTITY}Port.java       ← [application-templates.md § find-port]
│       │   │   │   └── service/
│       │   │   │       ├── {ENTITY}ApplicationService.java ← [application-templates.md § app-service]
│       │   │   │       └── {ENTITY}NotFoundException.java  ← [application-templates.md § not-found-exception]
│       │   │   │
│       │   │   ├── adapter/                     ← ★ Adapters: inbound DRIVES app, outbound is DRIVEN BY app
│       │   │   │   ├── inbound/
│       │   │   │   │   └── web/                 ← REST adapter (previously infrastructure/web)
│       │   │   │   │       ├── {ENTITY}Controller.java         ← [infrastructure-templates.md § controller]
│       │   │   │   │       ├── GlobalExceptionHandler.java     ← [infrastructure-templates.md § global-exception-handler]  ★ (first context only)
│       │   │   │   │       ├── request/
│       │   │   │   │       │   └── Create{ENTITY}Request.java  ← [infrastructure-templates.md § request]
│       │   │   │   │       └── response/
│       │   │   │   │           └── {ENTITY}Response.java       ← [infrastructure-templates.md § response]
│       │   │   │   └── outbound/
│       │   │   │       └── persistence/         ← JPA adapter (previously infrastructure/persistence)
│       │   │   │           ├── {ENTITY}JpaEntity.java          ← [infrastructure-templates.md § jpa-entity]
│       │   │   │           ├── {ENTITY}JpaRepository.java      ← [infrastructure-templates.md § jpa-repo]
│       │   │   │           ├── {ENTITY}PersistenceAdapter.java ← [infrastructure-templates.md § persistence-adapter]
│       │   │   │           └── {ENTITY}Mapper.java             ← [infrastructure-templates.md § mapper]
│       │   │   │
│       │   │   └── internal/                    ← ★ Module-private; other contexts must NOT import from here
│       │   │       └── (complex internals when they emerge — start empty)
│       │   │
│       │   └── resources/
│       │       └── (no db/migration here — migrations belong in app module)
│       └── test/
│           └── java/{BASE_PATH}/{CONTEXT}/
│               ├── domain/
│               │   └── {ENTITY}Test.java                   ← [infrastructure-templates.md § domain-test]
│               ├── adapter/outbound/
│               │   └── {ENTITY}PersistenceAdapterTest.java  ← [infrastructure-templates.md § persistence-test]
│               └── arch/
│                   └── HexagonalArchitectureTest.java       ← [infrastructure-templates.md § arch-test]  ★
└── app/                                         ← Bootstrap / Spring Boot runner
    ├── build.gradle                             ← [gradle-templates.md § app-build]
    └── src/
        ├── main/
        │   ├── java/{BASE_PATH}/
        │   │   └── Application.java             ← [app-module.md § application-class]
        │   └── resources/
        │       ├── application.yml              ← [app-module.md § application-yml]
        │       ├── application-local.yml        ← [app-module.md § application-local-yml]
        │       ├── application-test.yml         ← [app-module.md § application-test-yml]
        │       └── db/migration/
        │           └── V1__init.sql             ← [app-module.md § flyway-init-sql]  ★ 所有 context 的初始 schema 集中在此
        └── test/
            └── java/{BASE_PATH}/
                └── ApplicationModulesTest.java  ← [app-module.md § modules-test]  ★
```

Also generate at project root:
- `docker-compose.yml` — [app-module.md § docker-compose]
- `.gitignore` — [app-module.md § gitignore]

## Step 4 — Key Architecture Principles to Apply

### Spring Boot 4 Breaking Changes (apply when generating)

- **Flyway requires `spring-boot-starter-flyway`**: Spring Boot 4 拆分了 AutoConfiguration 模組，
  Flyway 不再自動配置。必須使用 `spring-boot-starter-flyway` starter（而非 `flyway-core`），
  才能觸發 `FlywayAutoConfiguration` 並確保 Flyway 在 Hibernate 驗證前執行。
  見 [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)

- **Flyway 版本**: 使用 Spring Boot 4 BOM 管理版本 `10.20.1`，避免版本不相容問題。

- **每個 Bounded Context 的 Flyway migration 版本號必須唯一**（跨所有 context），
  否則打包後 classpath 合併會造成版本衝突。建議依 context 順序排列：V1、V2、V3…

### Layer Rules (enforce via package boundaries)
- **`domain/`** — zero Spring dependencies; uses only JDK 25 (records, sealed classes)
- **`application/`** — minimal Spring (`@Service`, `@Transactional`); orchestrates domain
- **`infrastructure/`** — Spring, JPA, Web adapters; implements ports from application layer

### DDD Patterns Used
- **Aggregate Root**: extends `AggregateRoot<ID>`, registers domain events via `registerEvent()`
- **Value Objects**: Java `record` types implementing `ValueObject`
- **Entity IDs**: `record {ENTITY}Id(UUID value)` — always typed, never raw `UUID`
- **Domain Events**: `record` implementing `DomainEvent`, published via Spring's `ApplicationEventPublisher`
- **Repository**: interface in `domain/repository/`, implemented in `infrastructure/persistence/`
- **Use Case**: interface in `application/port/in/` — one interface per use case
- **Command**: `record` — immutable input to a use case

### Application Service Dependency Rule
The `{ENTITY}ApplicationService` must depend **only on output ports** declared in `application/port/out/`:
- `Save{ENTITY}Port` — write operations
- `Find{ENTITY}Port` — read operations

It must NOT inject the domain `{ENTITY}Repository` interface directly; that interface is for the infrastructure adapter to implement, not for the service to use. This keeps the application layer free from knowing which adapter provides which capability.

### Inter-Context Communication
- Use `@ApplicationModuleListener` (Spring Modulith) for async cross-context events
- Never inject another context's service directly — always use events or declared APIs
- `package-info.java` at each context root declares `@ApplicationModule(allowedDependencies = "commons")`

### Error Handling
- Generate `GlobalExceptionHandler` (first context only, it's app-wide) using Spring's `ProblemDetail` (RFC 7807) via `@RestControllerAdvice`
- Return `application/problem+json` for validation errors and not-found cases

### JDK 25 Idioms to Use
- `record` for Value Objects, Commands, Responses, Domain Events
- `sealed interface` for domain event hierarchies where appropriate
- Virtual threads: already enabled in `application.yml` via `spring.threads.virtual.enabled: true`

### Comment Policy (apply to every generated file)

All generated `.java` files **must** include comments that explain **why** the DDD/Hexagonal design decision was made — not what the code does mechanically, and **never** what the user asked for.

Rules:
- **語言：所有註解一律使用繁體中文。**
- Every class Javadoc: explain its DDD role (e.g., "Aggregate Root = 一致性邊界", "Output Port = 依賴反轉").
- Every non-trivial field: one-line comment stating its DDD significance in Traditional Chinese.
- Every non-trivial method: explain the architectural reason for the design choice in Traditional Chinese (factory method vs constructor, why events are pulled not pushed, etc.).
- Every `build.gradle` dependency or `libs.versions.toml` entry: one-line `#` comment in Traditional Chinese stating why this lib is needed in this architecture.
- YAML config keys with non-obvious values: inline comment in Traditional Chinese explaining the architectural impact.
- **Prohibited**: do NOT mention the user's business domain, entity names used as examples, or any requirement the user described. Comments must be universally applicable DDD rationale.

### Architecture Enforcement (ArchUnit)
Generate `HexagonalArchitectureTest` in each context to automatically verify that:
- `domain/` has zero Spring imports
- `application/` does not import from `infrastructure/`
- `infrastructure/` may import all layers

## Step 5 — Output Format

Present the output as:
1. A brief summary table of what will be generated
2. Each file as a **fenced code block** preceded by its path as a heading

Example:
```
### `{ARTIFACT}/settings.gradle`
​```groovy
// file content here
​```
```

Generate ALL files — do not truncate or say "and so on". The user needs a complete, copy-paste-ready scaffold.

> **Note on `gradle-wrapper.jar`**: This is a binary file and cannot be generated as a code block.
> After copying all files, instruct the user to run once:
> ```bash
> gradle wrapper --gradle-version 8.13
> ```
> This will generate `gradle/wrapper/gradle-wrapper.jar` and update `gradle-wrapper.properties`.

## Reference Files

Read these before generating — they contain the actual template code:

- `references/gradle-templates.md` — All Gradle/version catalog files
- `references/domain-templates.md` — Domain layer Java templates
- `references/application-templates.md` — Application layer Java templates
- `references/infrastructure-templates.md` — Infrastructure layer Java templates
- `references/app-module.md` — App bootstrap, YAML configs, Docker Compose
