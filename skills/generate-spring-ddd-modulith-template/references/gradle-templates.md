# Gradle Templates

## § wrapper-properties

```properties
# gradle/wrapper/gradle-wrapper.properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.14-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

## § gradlew

```bash
#!/bin/sh
# gradlew — Gradle start-up script for UN*X

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Resolve links: $0 may be a symlink
PRG="$0"
while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then PRG="$link"
  else PRG=$(dirname "$PRG")/"$link"
  fi
done
SAVED=$(pwd)
cd "$(dirname "$PRG")" > /dev/null
APP_HOME=$(pwd -P)
cd "$SAVED" > /dev/null

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Determine Java executable
if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

if ! command -v "$JAVACMD" > /dev/null 2>&1; then
  echo "ERROR: JAVA_HOME is not set and 'java' is not on PATH." >&2
  exit 1
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
  "-Dorg.gradle.appname=$APP_BASE_NAME" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"
```

## § gradlew-bat

```bat
@rem gradlew.bat — Gradle startup script for Windows

@if "%DEBUG%"=="" @echo off
@rem Set local scope for the variables with windows NT shell
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any relative classpath entries
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
goto execute

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe
if exist "%JAVA_EXE%" goto execute
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
exit /b 1

:execute
"%JAVA_EXE%" -Xmx64m -Xms64m ^
  "-Dorg.gradle.appname=%APP_BASE_NAME%" ^
  -classpath "%CLASSPATH%" ^
  org.gradle.wrapper.GradleWrapperMain %*

:end
endlocal
```

## § settings

```groovy
// settings.gradle
//
// WHY build-logic instead of buildSrc?
// buildSrc is auto-included but ANY file change invalidates the entire build cache
// for ALL subprojects. build-logic is an included build — it has its own Gradle
// cache, so editing a convention plugin only re-compiles that plugin, not everything.
// This is the Gradle team's current recommendation (see gradle.org/docs/current/userguide/structuring_software_products.html).

pluginManagement {
    includeBuild 'build-logic'   // convention plugins live here
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
    // Gradle 8.1+ 會自動偵測 gradle/libs.versions.toml 並建立 libs catalog，
    // 無需在此手動宣告；手動宣告會導致 from() 被呼叫兩次而報錯。
}

rootProject.name = '{ARTIFACT}'

include(
    'shared-kernel',   // ★ renamed from 'commons' — keeps shared kernel intentionally minimal
    'app',
    // one entry per bounded context:
    '{CONTEXT1}',
    '{CONTEXT2}'
    // ... repeat for each context
)
```

## § build-logic-settings

```groovy
// build-logic/settings.gradle
// Minimal settings file required for the included build.
rootProject.name = 'build-logic'

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        // Re-use the same version catalog from the root project
        libs { from(files('../gradle/libs.versions.toml')) }
    }
}
```

## § root-build

```groovy
// build.gradle (root)
plugins {
    // plugins {} 是靜態 DSL，不允許動態表達式（如 libs.versions.xxx.get()）。
    // alias() 是在 plugins 區塊中引用 version catalog 的唯一合法方式。
    alias(libs.plugins.spring.boot)     apply false
    alias(libs.plugins.spring.dep.mgmt) apply false
    alias(libs.plugins.flyway.plugin)   apply false
}

allprojects {
    group   = '{GROUP}'
    version = '0.0.1-SNAPSHOT'
}
// repositories 統一在 settings.gradle 的 dependencyResolutionManagement 宣告，
// 此處不重複宣告，否則與 FAIL_ON_PROJECT_REPOS 模式衝突。
```

## § version-catalog

```toml
# gradle/libs.versions.toml
[versions]
springBoot            = "4.0.1"
springModulith        = "2.0.0"
dependencyManagement  = "1.1.7"
flyway                = "10.20.1"  # 對齊 Spring Boot 4 BOM 管理版本
postgresql            = "42.7.5"
mapstruct             = "1.6.3"
testcontainers        = "1.20.6"
archunit              = "1.3.0"
springdocOpenapi      = "2.8.5"

[libraries]
# Spring Boot starters
spring-boot-starter-web        = { module = "org.springframework.boot:spring-boot-starter-web" }
# spring-boot-starter-data-jpa：提供 JPA + Hibernate + DataSource 自動設定，Outbound Persistence Adapter 依賴此模組
spring-boot-starter-data-jpa   = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
# spring-boot-starter-validation：在 Inbound Adapter 邊界啟用 Jakarta Bean Validation，驗證 Command record 的輸入
spring-boot-starter-validation = { module = "org.springframework.boot:spring-boot-starter-validation" }
# spring-boot-starter-actuator：暴露 /health 與 /modulith 端點，Spring Modulith 可觀測性的必要依賴
spring-boot-starter-actuator   = { module = "org.springframework.boot:spring-boot-starter-actuator" }
# spring-boot-starter-test：JUnit 5 + AssertJ + Mockito 的標準測試基礎，供所有層次的測試使用
spring-boot-starter-test       = { module = "org.springframework.boot:spring-boot-starter-test" }

# Spring Modulith
# spring-modulith-starter-core：在 package 層級強制執行 Bounded Context 邊界，提供 @ApplicationModule 注解
spring-modulith-starter-core   = { module = "org.springframework.modulith:spring-modulith-starter-core",       version.ref = "springModulith" }
# spring-modulith-starter-jdbc：用 JdbcTemplate 儲存 event_publication，不建立 JPA entity，
#   Hibernate 不會驗證此表，可安全搭配 ddl-auto: validate，無需額外的 Flyway migration
spring-modulith-starter-jdbc   = { module = "org.springframework.modulith:spring-modulith-starter-jdbc",       version.ref = "springModulith" }
# spring-modulith-events-api：提供 @ApplicationModuleListener，讓跨 Context 的非同步事件處理不需直接依賴
spring-modulith-events-api     = { module = "org.springframework.modulith:spring-modulith-events-api",         version.ref = "springModulith" }
# spring-modulith-test：提供 ApplicationModules.verify()，在 CI 中自動驗證模組邊界是否被違反
spring-modulith-test           = { module = "org.springframework.modulith:spring-modulith-test",               version.ref = "springModulith" }

# Database
# postgresql：PostgreSQL JDBC 驅動，設為 runtimeOnly，確保應用層在編譯期不依賴特定資料庫廠商
postgresql                     = { module = "org.postgresql:postgresql",                                       version.ref = "postgresql" }
# spring-boot-starter-flyway：Spring Boot 4 起 Flyway 必須透過此 starter 引入，內含 FlywayAutoConfiguration
# 直接使用 flyway-core 不會觸發 AutoConfiguration，導致 Flyway 不執行（Spring Boot 4 Migration Guide 要求）
spring-boot-starter-flyway     = { module = "org.springframework.boot:spring-boot-starter-flyway" }
# flyway-database-postgresql：Flyway 10+ 必須加入的 PostgreSQL 方言支援模組
flyway-database-postgresql     = { module = "org.flywaydb:flyway-database-postgresql",                         version.ref = "flyway" }

# Mapping
# mapstruct：編譯期程式碼產生器，負責領域模型 ↔ JPA Entity 的轉換，無反射開銷，讓領域物件不依賴任何框架
mapstruct                      = { module = "org.mapstruct:mapstruct",                                         version.ref = "mapstruct" }
# mapstruct-processor：編譯時處理 @Mapper 注解並產生實作程式碼的 Annotation Processor
mapstruct-processor            = { module = "org.mapstruct:mapstruct-processor",                               version.ref = "mapstruct" }

# API Documentation
# springdoc-openapi-webmvc：從 @RestController 自動產生 OpenAPI 3 規格與 Swagger UI，不影響業務邏輯
springdoc-openapi-webmvc       = { module = "org.springdoc:springdoc-openapi-starter-webmvc-ui",               version.ref = "springdocOpenapi" }

# Testing
# testcontainers-bom：統一管理所有 Testcontainers 模組版本，防止整合測試中的版本衝突
testcontainers-bom             = { module = "org.testcontainers:testcontainers-bom",                           version.ref = "testcontainers" }
# testcontainers-junit：提供 @Container + @Testcontainers 的 JUnit 5 擴充，負責容器生命週期管理
testcontainers-junit           = { module = "org.testcontainers:junit-jupiter" }
# testcontainers-postgresql：在 Docker 中啟動真實 PostgreSQL，用於 Persistence Adapter 的整合測試
testcontainers-postgresql      = { module = "org.testcontainers:postgresql" }
# archunit-junit5：將 Hexagonal Architecture 層次規則實作為自動化測試，在 CI 中偵測架構腐化
archunit-junit5                = { module = "com.tngtech.archunit:archunit-junit5",                            version.ref = "archunit" }

[bundles]
# spring-web-jpa：每個 Bounded Context 的業務基礎設施能力——接收 HTTP 請求、透過 JPA 讀寫資料庫、
#   在 inbound adapter 邊界驗證輸入。與 modulith bundle 分開宣告，
#   因為「能做 Web + Persistence」與「參與模組邊界系統」是兩個獨立的關切點。
spring-web-jpa   = ["spring-boot-starter-web", "spring-boot-starter-data-jpa",
                     "spring-boot-starter-validation", "spring-boot-starter-actuator"]

# modulith：讓模組參與 Spring Modulith 的邊界驗證與跨 Context 事件機制。
#   starter-core 負責 package 層級的邊界強制執行（verify()）；
#   starter-jdbc 用 JdbcTemplate 儲存 event_publication，不建立 JPA entity，
#     Hibernate 不會驗證此表，可安全搭配 ddl-auto: validate；
#   events-api 提供 @ApplicationModuleListener，讓各 Context 宣告對事件的訂閱，
#     不需直接依賴發布方的類別。
modulith         = ["spring-modulith-starter-core", "spring-modulith-starter-jdbc",
                     "spring-modulith-events-api"]

flyway           = ["spring-boot-starter-flyway", "flyway-database-postgresql"]
testing          = ["spring-boot-starter-test", "testcontainers-junit",
                     "testcontainers-postgresql", "archunit-junit5"]

[plugins]
spring-boot           = { id = "org.springframework.boot",            version.ref = "springBoot" }
spring-dep-mgmt       = { id = "io.spring.dependency-management",     version.ref = "dependencyManagement" }
flyway-plugin         = { id = "org.flywaydb.flyway",                 version.ref = "flyway" }
```

## § build-logic-build

```groovy
// build-logic/build.gradle
plugins {
    id 'groovy-gradle-plugin'   // enables writing .gradle convention plugins in Groovy
}

dependencies {
    // Give convention plugins access to the Spring Boot plugin classpath
    implementation libs.plugins.spring.boot.map { "org.springframework.boot:spring-boot-gradle-plugin:${it.version}" }.get()
    implementation libs.plugins.spring.dep.mgmt.map { "io.spring.gradle:dependency-management-plugin:${it.version}" }.get()
}
```

## § convention-java

```groovy
// build-logic/src/main/groovy/java-common-conventions.gradle
// Applied to every subproject — sets Java toolchain, compiler flags, test runner.
plugins {
    id 'java'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += ['-parameters']   // Spring & MapStruct need parameter names
    options.encoding = 'UTF-8'
}

tasks.withType(Test).configureEach {
    useJUnitPlatform()
    jvmArgs '-XX:+EnableDynamicAgentLoading'  // suppress JDK 25 agent warnings
    maxParallelForks = Runtime.runtime.availableProcessors().intdiv(2) ?: 1
}
```

## § convention-spring

```groovy
// build-logic/src/main/groovy/spring-module-conventions.gradle
// For bounded-context modules: Spring beans but NOT the runnable boot app.
plugins {
    id 'java-common-conventions'
    id 'org.springframework.boot'        // 套用才會產生 bootJar 任務，才能在下方關閉它
    id 'io.spring.dependency-management'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}"
        mavenBom "org.testcontainers:testcontainers-bom:${libs.versions.testcontainers.get()}"
    }
}

// Disable the executable jar task — only the 'app' module should produce one
tasks.named('bootJar') { enabled = false }
tasks.named('jar')     { enabled = true  }

dependencies {
    annotationProcessor libs.mapstruct.processor
    implementation      libs.mapstruct
    testImplementation  libs.bundles.testing
}
```

## § convention-library

```groovy
// build-logic/src/main/groovy/library-conventions.gradle
// For pure-Java modules (commons / shared-kernel) — no Spring Boot, no web.
plugins {
    id 'java-common-conventions'
    id 'io.spring.dependency-management'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}"
    }
}
```

## § shared-kernel-build

```groovy
// shared-kernel/build.gradle
//
// WHY 'shared-kernel' not 'commons'?
// 'commons' tends to become a dumping ground for utilities, DTOs, helpers.
// 'shared-kernel' signals the DDD concept: ONLY cross-cutting domain abstractions
// (AggregateRoot, DomainEvent, ValueObject, AuditableEntity) belong here.
// If something isn't genuinely shared across bounded contexts, it goes in the context module.
plugins {
    id 'library-conventions'
}

dependencies {
    // Jakarta Persistence API：@Entity、@MappedSuperclass 等 JPA 注解
    compileOnly 'jakarta.persistence:jakarta.persistence-api'
    // spring-boot-starter-data-jpa：AuditableEntity 需要 @CreatedDate、@LastModifiedDate
    // 與 AuditingEntityListener，這些來自 spring-data-jpa，無法只靠 jakarta.persistence-api
    compileOnly libs.spring.boot.starter.data.jpa
    testImplementation libs.spring.boot.starter.test
}
```

## § context-build

```groovy
// {CONTEXT}/build.gradle
plugins {
    id 'spring-module-conventions'
}

dependencies {
    implementation project(':shared-kernel')

    implementation libs.bundles.spring.web.jpa
    implementation libs.bundles.modulith
    implementation libs.bundles.flyway
    implementation libs.springdoc.openapi.webmvc   // Swagger UI / OpenAPI 3
    runtimeOnly    libs.postgresql

    testImplementation libs.spring.modulith.test
    testImplementation libs.archunit.junit5
}
```

## § app-build

```groovy
// app/build.gradle
plugins {
    id 'spring-module-conventions'
    id 'org.springframework.boot'
    id 'io.spring.dependency-management'
}

// Only the app module is executable — disable bootJar on other subprojects via:
//   tasks.named('bootJar') { enabled = false } in their build files
bootJar { enabled = true }
jar     { enabled = false }

dependencies {
    implementation project(':shared-kernel')

    // Include every bounded-context module
    // Add one line per context:
    implementation project(':{CONTEXT1}')
    implementation project(':{CONTEXT2}')

    implementation libs.bundles.spring.web.jpa
    implementation libs.bundles.modulith
    implementation libs.bundles.flyway
    implementation libs.spring.boot.starter.actuator
    runtimeOnly    libs.postgresql

    testImplementation libs.spring.modulith.test
    testImplementation libs.bundles.testing
}
```
