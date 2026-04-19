# App Bootstrap Templates

## § application-class

```java
// app/src/main/java/{BASE_PATH}/Application.java
package {BASE_PKG};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.modulith.Modulithic;

/**
 * Spring Boot 應用程式進入點。
 *
 * @Modulithic：向 Spring Modulith 登錄模組結構，
 *   強制各 Bounded Context 只能透過宣告的公開 API 與事件互動。
 *   可在測試中呼叫 ApplicationModules.of(Application.class).verify() 驗證違規。
 *
 * @EnableJpaAuditing：啟用 Spring Data JPA 的審計功能，
 *   讓繼承 AuditableEntity 的 JPA Entity 能自動填入 createdAt / updatedAt。
 */
@SpringBootApplication
@EnableJpaAuditing
@Modulithic(
    systemName = "{ARTIFACT}",
    sharedModules = "commons"   // shared-kernel 對所有 Bounded Context 可見
)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## § application-yml

```yaml
# app/src/main/resources/application.yml
spring:
  application:
    name: {ARTIFACT}

  threads:
    virtual:
      enabled: true   # 啟用 Project Loom 虛擬執行緒（JDK 25），提升 I/O 密集型吞吐量

  datasource:
    url:      ${DB_URL:jdbc:postgresql://localhost:5432/{ARTIFACT}}
    username: ${DB_USER:{ARTIFACT}_user}
    password: ${DB_PASS:secret}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20     # 配合虛擬執行緒：連線池不需要過大，瓶頸在 DB 而非執行緒
      minimum-idle: 5
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: validate        # Flyway 負責 Schema；Hibernate 僅驗證，不自動建立或修改表格
    open-in-view: false         # 關閉 OSIV：避免在 View 層觸發懶載入，強制事務邊界清晰
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: false
        jdbc:
          batch_size: 50        # 批次寫入：減少 INSERT/UPDATE 的資料庫往返次數
          order_inserts: true   # 搭配 batch_size：讓 Hibernate 對同類型 INSERT 排序後批次執行
          order_updates: true

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true   # 首次部署時允許對已有資料的資料庫設定基準版本

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, modulith  # modulith：暴露模組結構端點，便於觀測架構
  endpoint:
    health:
      show-details: when-authorized

logging:
  level:
    root: INFO
    {BASE_PKG}: DEBUG
    org.springframework.modulith: DEBUG  # 開發期間顯示 Modulith 的模組邊界驗證訊息
```

## § application-local-yml

```yaml
# app/src/main/resources/application-local.yml
# 本機開發覆蓋設定——以 --spring.profiles.active=local 啟用
spring:
  datasource:
    url:      jdbc:postgresql://localhost:5432/{ARTIFACT}
    username: {ARTIFACT}_user
    password: secret

  jpa:
    properties:
      hibernate:
        format_sql: true  # 本機開發：格式化 SQL 輸出，方便閱讀 Hibernate 產生的查詢

logging:
  level:
    org.hibernate.SQL: DEBUG          # 顯示完整 SQL 語句
    org.hibernate.orm.jdbc.bind: TRACE # 顯示綁定參數值，協助除錯 N+1 問題
```

## § application-test-yml

```yaml
# app/src/main/resources/application-test.yml
# @SpringBootTest 使用此設定；Testcontainers 會透過 @DynamicPropertySource 覆蓋 datasource
spring:
  flyway:
    enabled: true  # 測試時也執行 Flyway，確保測試 Schema 與正式環境一致

  jpa:
    hibernate:
      ddl-auto: create-drop   # 測試環境：每次測試後重建 Schema，確保測試間互不影響
```

## § flyway-init-sql

```sql
-- app/src/main/resources/db/migration/V1__{CONTEXT1}_init.sql
-- {CONTEXT1_CAP} Bounded Context 初始 Schema。
-- 設計原則：
--   Spring Modulith 共用一個 DB 與一個 Flyway instance，migration 統一放在 app 模組。
--   版本號為全域序列（V1、V2、V3…），context 名稱放在 description 部分作為識別。
--   一個 context 一個檔案：未來若拆出為獨立微服務，可直接帶走對應的 migration 檔案。
--   未來此 context 的 schema 異動，新增 V{N}__{CONTEXT1}_{add|alter|delete}_{對象}.sql，不修改此檔。

CREATE TABLE IF NOT EXISTS {CONTEXT1}_{ENTITY1_LOWER}s (
    id         UUID         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_{CONTEXT1}_{ENTITY1_LOWER}s PRIMARY KEY (id)
);

COMMENT ON TABLE {CONTEXT1}_{ENTITY1_LOWER}s IS '{ENTITY1} aggregate root for the {CONTEXT1_CAP} bounded context';
```

> 每個 Bounded Context 對應一個 migration 檔案，版本號依 context 順序排列：
> `V1__{CONTEXT1}_init.sql`, `V2__{CONTEXT2}_init.sql`, `V3__{CONTEXT3}_init.sql`…
>
> 最後一個版本號保留給 Spring Modulith 基礎設施表（見下方 § spring-modulith-event-publication）。

## § spring-modulith-event-publication

```sql
-- app/src/main/resources/db/migration/V{N}__spring_modulith_event_publication.sql
-- Spring Modulith Transactional Outbox 所需的 event_publication 表。
-- spring-modulith-starter-jpa 使用此表實現 Transactional Outbox Pattern：
-- 跨 Bounded Context 的 Domain Event 先寫入此表（與業務資料同一個 transaction），
-- 再由 Modulith 非同步派送給各 @ApplicationModuleListener，確保事件不會因系統崩潰而遺失。
-- completion_date 為 NULL 表示事件尚未被所有 listener 消費完畢。

CREATE TABLE IF NOT EXISTS event_publication (
    id               UUID         NOT NULL,
    listener_id      TEXT         NOT NULL,
    event_type       TEXT         NOT NULL,
    serialized_event TEXT         NOT NULL,
    publication_date TIMESTAMPTZ  NOT NULL,
    completion_date  TIMESTAMPTZ,
    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_event_publication_completion_date
    ON event_publication (completion_date);
```

## § docker-compose

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:17-alpine
    container_name: {ARTIFACT}-postgres
    environment:
      POSTGRES_DB:       {ARTIFACT}
      POSTGRES_USER:     {ARTIFACT}_user
      POSTGRES_PASSWORD: secret
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U {ARTIFACT}_user -d {ARTIFACT}"]
      interval: 10s
      timeout: 5s
      retries: 5


volumes:
  postgres_data:
```

## § modules-test

```java
// app/src/test/java/{BASE_PATH}/ApplicationModulesTest.java
package {BASE_PKG};

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * 驗證 Spring Modulith 模組結構的完整性測試。
 *
 * 此測試做兩件事：
 *   1. verify()：斷言沒有模組違反其宣告的依賴關係
 *      （定義於各 Context 的 package-info.java @ApplicationModule 注解）。
 *      循環依賴與未授權的跨模組 import 都會導致此測試失敗。
 *   2. Documenter：將模組文件產生至 target/spring-modulith-docs/，
 *      讓團隊隨時可查閱最新的架構示意圖，無需手動維護文件。
 *
 * 執行指令：./gradlew :app:test
 */
class ApplicationModulesTest {

    // 靜態初始化：模組掃描只執行一次，所有測試方法共用同一個掃描結果
    private static final ApplicationModules modules =
            ApplicationModules.of(Application.class);

    @Test
    void modules_are_structurally_valid() {
        // 驗證所有模組的依賴邊界，任何違規都會產生清楚的錯誤訊息
        modules.verify();
    }

    @Test
    void generate_module_documentation() {
        // 產生 PlantUML 架構圖與模組畫布（Module Canvas），
        // 建議將此文件納入 CI 產出物（Artifacts）以保持文件同步
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
```

## § gitignore

```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar
!**/src/main/**/build/
!**/src/test/**/build/

# IntelliJ IDEA
.idea/
*.iws
*.iml
*.ipr

# VS Code
.vscode/
.classpath
.project
.settings/
bin/

# macOS
.DS_Store

# Logs
*.log
logs/

# Environment
.env
.env.local

# Spring Boot
HELP.md
```
