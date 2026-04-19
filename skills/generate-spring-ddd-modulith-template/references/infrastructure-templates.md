# Infrastructure Layer Templates

Spring、JPA 與 Web 都在此層。這些是實作應用層 Port 的 Adapter，
是外界「驅動」應用（Inbound）或應用「驅動」外界（Outbound）的橋接點。

## § auditable

```java
// commons/src/main/java/{BASE_PATH}/commons/infrastructure/jpa/AuditableEntity.java
package {BASE_PKG}.shared.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * JPA 超類別，自動填入審計時間戳。
 * 每個需要 created_at / updated_at 追蹤的 @Entity 都應繼承此類。
 * 需要在 Application 主類上加上 @EnableJpaAuditing 才能啟用自動填入。
 */
@MappedSuperclass
// AuditingEntityListener：Spring Data JPA 提供的監聽器，
// 負責在 @PrePersist / @PreUpdate 時自動填入 @CreatedDate 與 @LastModifiedDate。
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    // updatable = false：建立時間是歷史事實，JPA 層面禁止後續更新
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

## § jpa-entity

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/adapter/outbound/persistence/{ENTITY}JpaEntity.java
package {BASE_PKG}.{CONTEXT}.adapter.outbound.persistence;

import {BASE_PKG}.shared.infrastructure.jpa.AuditableEntity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * {ENTITY} 的 JPA 持久化實體。
 *
 * 刻意與領域模型分離：這讓你能在不修改領域邏輯的情況下調整資料庫 Schema，
 * 反之亦然。兩者之間的轉換由 Mapper 負責。
 * 此類不應暴露到 domain 或 application 層。
 */
@Entity
// 表名加上 Bounded Context 前綴，避免跨模組的命名衝突，
// 同時讓 DBA 一眼看出此表屬於哪個模組。
@Table(name = "{CONTEXT}_{ENTITY_LOWER}s")
public class {ENTITY}JpaEntity extends AuditableEntity {

    // updatable = false：主鍵在建立後不得修改，由 ORM 層強制保障
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // JPA 規範要求無參數建構子；設為 protected 以阻止此 package 外的程式碼直接實例化，
    // 唯一合法的建立路徑是透過 Mapper。
    protected {ENTITY}JpaEntity() {}

    public {ENTITY}JpaEntity(UUID id, String name) {
        this.id   = id;
        this.name = name;
    }

    public UUID   getId()   { return id; }
    public String getName() { return name; }
    // Setter 僅供 Mapper 的更新路徑使用；領域的修改應通過聚合方法，而非直接操作此類
    public void   setName(String name) { this.name = name; }
}
```

## § jpa-repo

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/adapter/outbound/persistence/{ENTITY}JpaRepository.java
package {BASE_PKG}.{CONTEXT}.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA 的 {ENTITY}JpaEntity 資料存取介面。
 * 這不是領域的 Repository，而是基礎設施的實作細節。
 * 設為 package-private（不加 public），確保只有同一個 package 內的
 * PersistenceAdapter 能使用，不洩漏到其他層。
 */
interface {ENTITY}JpaRepository extends JpaRepository<{ENTITY}JpaEntity, UUID> {
    // 設為 package-private：只有 PersistenceAdapter 能使用此介面
}
```

## § persistence-adapter

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/adapter/outbound/persistence/{ENTITY}PersistenceAdapter.java
package {BASE_PKG}.{CONTEXT}.adapter.outbound.persistence;

import {BASE_PKG}.{CONTEXT}.application.port.out.Save{ENTITY}Port;
import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY};
import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY}Id;
import {BASE_PKG}.{CONTEXT}.domain.repository.{ENTITY}Repository;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Outbound Adapter：同時實作領域的 Repository 介面與 Application 的 Save Output Port。
 *
 * 單一 Adapter 實作兩個介面是因為它們共用同一個持久化關切點。
 * 若日後兩者需求分歧（例如讀取模型需要不同的查詢），再拆分為兩個 Adapter。
 * 設為 package-private，確保此類不被應用層或領域層直接依賴。
 */
@Component
class {ENTITY}PersistenceAdapter implements {ENTITY}Repository, Save{ENTITY}Port {

    private final {ENTITY}JpaRepository jpaRepository;
    // Mapper 負責領域模型與 JPA Entity 之間的轉換，
    // 將兩個模型的生命週期完全解耦。
    private final {ENTITY}Mapper mapper;

    {ENTITY}PersistenceAdapter({ENTITY}JpaRepository jpaRepository, {ENTITY}Mapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper        = mapper;
    }

    @Override
    public void save({ENTITY} {ENTITY_LOWER}) {
        // 先將領域模型轉為 JPA Entity，再委託 Spring Data 持久化
        jpaRepository.save(mapper.toJpaEntity({ENTITY_LOWER}));
    }

    @Override
    public Optional<{ENTITY}> findById({ENTITY}Id id) {
        // id.value() 取出原始 UUID，因為 JpaRepository 的鍵值型別是 UUID
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<{ENTITY}> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsById({ENTITY}Id id) {
        return jpaRepository.existsById(id.value());
    }

    @Override
    public void deleteById({ENTITY}Id id) {
        jpaRepository.deleteById(id.value());
    }
}
```

## § mapper

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/adapter/outbound/persistence/{ENTITY}Mapper.java
package {BASE_PKG}.{CONTEXT}.adapter.outbound.persistence;

import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY};
import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY}Id;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct Mapper：領域模型 ↔ JPA Entity 的轉換。
 * MapStruct 在編譯期產生實作程式碼，無反射開銷，效能等同手寫程式碼。
 * componentModel = "spring"：讓 Spring 能將產生的實作注入為 Bean。
 */
@Mapper(componentModel = "spring")
interface {ENTITY}Mapper {

    // expression 用於將強型別 ID 解包為原始 UUID，供 JPA Entity 使用
    @Mapping(target = "id",   expression = "java({ENTITY_LOWER}.getId().value())")
    @Mapping(target = "name", source = "name")
    {ENTITY}JpaEntity toJpaEntity({ENTITY} {ENTITY_LOWER});

    // 使用 reconstitute() 而非 create()，避免重建時重複觸發領域事件
    default {ENTITY} toDomain({ENTITY}JpaEntity jpa) {
        return {ENTITY}.reconstitute(
                {ENTITY}Id.of(jpa.getId()),
                jpa.getName(),
                jpa.getCreatedAt()
        );
    }
}
```

## § controller

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/adapter/inbound/web/{ENTITY}Controller.java
package {BASE_PKG}.{CONTEXT}.adapter.inbound.web;

import {BASE_PKG}.{CONTEXT}.application.port.in.Create{ENTITY}Command;
import {BASE_PKG}.{CONTEXT}.application.port.in.Create{ENTITY}UseCase;
import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY}Id;
import {BASE_PKG}.{CONTEXT}.adapter.inbound.web.request.Create{ENTITY}Request;
import {BASE_PKG}.{CONTEXT}.adapter.inbound.web.response.{ENTITY}Response;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Inbound REST Adapter，驅動 Create{ENTITY} Use Case。
 *
 * Controller 的唯一職責是處理 HTTP 關切點：解析請求、呼叫 Use Case、組裝 HTTP 回應。
 * 任何業務邏輯都不應出現在此類中。
 * 依賴 Use Case 介面而非具體的 Application Service，符合依賴反轉原則。
 */
@RestController
@RequestMapping("/api/v1/{CONTEXT_LOWER}s")
public class {ENTITY}Controller {

    private final Create{ENTITY}UseCase create{ENTITY}UseCase;

    public {ENTITY}Controller(Create{ENTITY}UseCase create{ENTITY}UseCase) {
        this.create{ENTITY}UseCase = create{ENTITY}UseCase;
    }

    @PostMapping
    public ResponseEntity<{ENTITY}Response> create(
            // @Validated：觸發 Request record 上的 Jakarta Bean Validation，
            // 驗證失敗時由 GlobalExceptionHandler 映射為 HTTP 400 + ProblemDetail。
            @Validated @RequestBody Create{ENTITY}Request request,
            // UriComponentsBuilder：建立符合 REST 慣例的 Location Header，
            // 指向新建立資源的 URI，讓客戶端無需硬編碼路徑。
            UriComponentsBuilder uriBuilder) {

        {ENTITY}Id id = create{ENTITY}UseCase.create(
                new Create{ENTITY}Command(request.name())
        );

        URI location = uriBuilder
                .path("/api/v1/{CONTEXT_LOWER}s/{id}")
                .buildAndExpand(id.toString())
                .toUri();

        // HTTP 201 Created + Location Header：符合 REST 資源建立的語意規範
        return ResponseEntity.created(location).body(new {ENTITY}Response(id.toString(), request.name()));
    }
}
```

## § request

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/adapter/inbound/web/request/Create{ENTITY}Request.java
package {BASE_PKG}.{CONTEXT}.adapter.inbound.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 建立 {ENTITY} 的 HTTP 請求體（Request Body）。
 * 使用 record：自動產生 toString、equals 與 getter，同時保持不可變。
 * 此型別屬於 Web Adapter 的邊界，不應洩漏到 Application 或 Domain 層；
 * 進入 Use Case 前轉換為 Command。
 */
public record Create{ENTITY}Request(

        // @NotBlank + @Size：在 Web Adapter 邊界完成輸入驗證，
        // 確保格式錯誤的請求不會傳入應用層。
        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name

) {}
```

## § response

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/adapter/inbound/web/response/{ENTITY}Response.java
package {BASE_PKG}.{CONTEXT}.adapter.inbound.web.response;

/**
 * {ENTITY} 的 HTTP 回應體（Response Body）。
 * 使用 record：簡潔且 Jackson 可直接序列化，不需額外注解。
 * 此型別屬於 Web Adapter 的邊界，不應直接暴露領域模型，
 * 保持領域模型與 API Contract 的獨立演化能力。
 */
public record {ENTITY}Response(String id, String name) {}
```

## § flyway-sql

```sql
-- {CONTEXT}/src/main/resources/db/migration/V1__create_{CONTEXT}_tables.sql
-- Flyway 負責 Schema 版控；Hibernate 設為 validate 模式，不自動建立或修改表格。
-- 版本命名慣例：V{版本}__{說明}.sql，確保遷移按順序且僅執行一次。
CREATE TABLE IF NOT EXISTS {CONTEXT}_{ENTITY_LOWER}s (
    id         UUID         NOT NULL,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_{CONTEXT}_{ENTITY_LOWER}s PRIMARY KEY (id)
);

COMMENT ON TABLE {CONTEXT}_{ENTITY_LOWER}s IS '{ENTITY} aggregate root for the {CONTEXT_CAP} bounded context';
```

## § global-exception-handler

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/adapter/inbound/web/GlobalExceptionHandler.java
// 注意：只在第一個 Bounded Context 產生此類；它是應用程式層級的，並非各 Context 各自獨立。
package {BASE_PKG}.{CONTEXT}.adapter.inbound.web;

import {BASE_PKG}.{CONTEXT}.application.service.{ENTITY}NotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 將領域 / 應用層例外轉換為 RFC 7807 ProblemDetail 回應。
 *
 * Spring Boot 3.x+ 原生支援 ProblemDetail，無需額外依賴。
 * 客戶端收到 application/problem+json，包含結構化、機器可讀的錯誤資訊。
 * 使用 @RestControllerAdvice 集中處理所有例外，避免每個 Controller 各自重複處理。
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    // 攔截具名的領域例外，映射為精確的 HTTP 語意（404 Not Found）
    @ExceptionHandler({ENTITY}NotFoundException.class)
    ProblemDetail handle{ENTITY}NotFound({ENTITY}NotFoundException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("https://api.{ARTIFACT}.example.com/errors/not-found"));
        return problem;
    }

    // 攔截 @Validated 驗證失敗，將所有欄位錯誤收集後回傳結構化清單
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage,
                        (a, b) -> a));
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setType(URI.create("https://api.{ARTIFACT}.example.com/errors/validation"));
        problem.setProperty("violations", errors);
        return problem;
    }

    // 兜底處理：所有未預期的例外統一回傳 500，避免 Stack Trace 洩漏給客戶端
    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneric(Exception ex) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setType(URI.create("https://api.{ARTIFACT}.example.com/errors/internal"));
        return problem;
    }
}
```

## § arch-test

```java
// {CONTEXT}/src/test/java/{BASE_PATH}/{CONTEXT}/arch/HexagonalArchitectureTest.java
package {BASE_PKG}.{CONTEXT}.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * ArchUnit 架構測試：自動驗證 Hexagonal Architecture 的層次規則。
 *
 * 這些測試不啟動 Spring Context，執行速度極快，
 * 能在 CI 階段防止架構腐化（Architectural Drift）。
 *
 * 為什麼需要這些測試？
 * 一旦有開發者在 domain 類別中加入 @Autowired，架構就開始崩壞。
 * ArchUnit 讓這種違規成為測試失敗，而不是 Code Review 的遺漏。
 */
class HexagonalArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        // 僅掃描此 Bounded Context 的 package，確保驗證範圍精確
        classes = new ClassFileImporter()
                .importPackages("{BASE_PKG}.{CONTEXT}");
    }

    @Test
    void domain_must_not_depend_on_spring() {
        // 領域層規則：零 Spring / JPA 依賴，確保領域模型可獨立於框架測試與部署
        ArchRule rule = noClasses()
                .that().resideInAPackage("..{CONTEXT}.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence.."
                );
        rule.check(classes);
    }

    @Test
    void application_must_not_depend_on_adapters() {
        // 應用層規則：不允許依賴 Adapter，確保應用層只透過 Port 介面與外界互動
        ArchRule rule = noClasses()
                .that().resideInAPackage("..{CONTEXT}.application..")
                .should().dependOnClassesThat()
                .resideInAPackage("..{CONTEXT}.adapter..");
        rule.check(classes);
    }

    @Test
    void layered_architecture_dependency_direction() {
        // 層次依賴方向：Domain ← Application ← Adapter
        // Domain 不依賴任何層；Application 只依賴 Domain；Adapter 可依賴兩者
        layeredArchitecture()
                .consideringAllDependencies()
                .layer("Domain").definedBy("..{CONTEXT}.domain..")
                .layer("Application").definedBy("..{CONTEXT}.application..")
                .layer("Adapter").definedBy("..{CONTEXT}.adapter..")
                .whereLayer("Domain").mayNotAccessAnyLayer()
                .whereLayer("Application").mayOnlyAccessLayers("Domain")
                .whereLayer("Adapter").mayOnlyAccessLayers("Application", "Domain")
                .check(classes);
    }
}
```

## § domain-test

```java
// {CONTEXT}/src/test/java/{BASE_PATH}/{CONTEXT}/domain/{ENTITY}Test.java
package {BASE_PKG}.{CONTEXT}.domain;

import {BASE_PKG}.{CONTEXT}.domain.event.{ENTITY}CreatedEvent;
import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY};
import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY}Id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * 純單元測試：驗證 {ENTITY} 聚合的領域邏輯——無 Spring Context、無資料庫。
 * 快速回饋是領域層測試的核心價值；領域規則應能在毫秒內完成測試。
 */
class {ENTITY}Test {

    @Test
    void create_registers_domain_event() {
        var id     = {ENTITY}Id.generate();
        var {ENTITY_LOWER} = {ENTITY}.create(id, "Test {ENTITY}");

        assertThat({ENTITY_LOWER}.getId()).isEqualTo(id);
        assertThat({ENTITY_LOWER}.getName()).isEqualTo("Test {ENTITY}");

        // 驗證 create() 確實登錄了建立事件（領域事件是聚合行為的外部可見證據）
        var events = {ENTITY_LOWER}.pullDomainEvents();
        assertThat(events).hasSize(1)
                .first().isInstanceOf({ENTITY}CreatedEvent.class);
    }

    @Test
    void create_with_blank_name_throws() {
        // 驗證聚合在建立時確實執行不變式（Invariant）
        var id = {ENTITY}Id.generate();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> {ENTITY}.create(id, "  "));
    }

    @Test
    void pulling_events_clears_the_list() {
        // 驗證 pullDomainEvents() 的冪等性：同一批事件只能被取出一次
        var {ENTITY_LOWER} = {ENTITY}.create({ENTITY}Id.generate(), "Test");
        {ENTITY_LOWER}.pullDomainEvents(); // 第一次取出
        assertThat({ENTITY_LOWER}.pullDomainEvents()).isEmpty();
    }
}
```

## § persistence-test

```java
// {CONTEXT}/src/test/java/{BASE_PATH}/{CONTEXT}/adapter/outbound/{ENTITY}PersistenceAdapterTest.java
package {BASE_PKG}.{CONTEXT}.adapter.outbound;

import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY};
import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY}Id;
import {BASE_PKG}.{CONTEXT}.adapter.outbound.persistence.{ENTITY}PersistenceAdapter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence Adapter 的整合測試：使用真實 PostgreSQL（透過 Testcontainers）。
 * 驗證領域模型 → JPA Entity 的映射，以及從資料庫重建後結果正確。
 * 不使用 Mock 資料庫，因為 Mock 無法驗證真實的 SQL 行為與映射正確性。
 */
@DataJpaTest        // 只載入 JPA 相關的 Spring Context Slice，啟動速度快於 @SpringBootTest
@Testcontainers     // 啟動 Docker 容器生命週期管理
@Import({ENTITY}PersistenceAdapter.class)
class {ENTITY}PersistenceAdapterTest {

    // @Container：Testcontainers 在測試類載入時自動啟動，測試結束後自動停止
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    // @DynamicPropertySource：將 Testcontainers 動態分配的連線資訊注入 Spring 設定，
    // 取代 application-test.yml 中的靜態 datasource 設定。
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private {ENTITY}PersistenceAdapter adapter;

    @Test
    void save_and_find_by_id() {
        var id     = {ENTITY}Id.generate();
        var {ENTITY_LOWER} = {ENTITY}.create(id, "Test");

        adapter.save({ENTITY_LOWER});

        var found = adapter.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test");
        assertThat(found.get().getId()).isEqualTo(id);
    }
}
```
