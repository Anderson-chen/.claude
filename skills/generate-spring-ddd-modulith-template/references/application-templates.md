# Application Layer Templates

此層只允許少量 Spring 注解：`@Service`、`@Transactional`、以及事件發布。
不應出現 JPA Entity、HTTP 相關型別，或任何基礎設施細節。

## § command

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/application/port/in/Create{ENTITY}Command.java
package {BASE_PKG}.{CONTEXT}.application.port.in;

/**
 * Create{ENTITY} Use Case 的不可變命令物件（Command Object）。
 *
 * Command 使用 record：攜帶已驗證的輸入資料至 Application Service。
 * 在此加上 Jakarta Bean Validation 注解，使 Use Case 執行前即完成輸入驗證，
 * 確保 Application Service 只接收到有效資料。
 */
public record Create{ENTITY}Command(

        @jakarta.validation.constraints.NotBlank
        String name

) {
    // Compact constructor：施加與領域無關的輸入防衛，確保命令物件建立後即為有效狀態。
    // 此驗證是應用層的邊界防衛，不是領域規則。
    public Create{ENTITY}Command {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("name must not be blank");
    }
}
```

## § use-case

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/application/port/in/Create{ENTITY}UseCase.java
package {BASE_PKG}.{CONTEXT}.application.port.in;

import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY}Id;

/**
 * Input Port：定義 Web Adapter（或其他驅動端）觸發 Create{ENTITY} 工作流程的契約。
 *
 * 一個介面對應一個 Use Case，讓 Application Service 保持專注（單一職責），
 * 同時使測試時的 Mock 替換更加簡單。
 * Web Adapter 依賴此介面，而非具體的 Application Service，符合依賴反轉原則。
 */
public interface Create{ENTITY}UseCase {

    /**
     * @param command 已驗證的輸入資料
     * @return 新建立的 {ENTITY} 識別碼
     */
    {ENTITY}Id create(Create{ENTITY}Command command);
}
```

## § save-port

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/application/port/out/Save{ENTITY}Port.java
package {BASE_PKG}.{CONTEXT}.application.port.out;

import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY};

/**
 * Output Port：Application Service 呼叫此介面以持久化 {ENTITY}。
 * JPA Persistence Adapter 實作此介面——依賴反轉讓應用層與基礎設施層解耦，
 * 日後替換為 MongoDB Adapter 時無須修改 Application Service。
 */
public interface Save{ENTITY}Port {
    void save({ENTITY} {ENTITY_LOWER});
}
```

## § find-port

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/application/port/out/Find{ENTITY}Port.java
package {BASE_PKG}.{CONTEXT}.application.port.out;

import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY};
import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY}Id;

import java.util.List;
import java.util.Optional;

/**
 * Output Port：{ENTITY} 的讀取能力。
 *
 * 將讀取（Find）與寫入（Save）拆成兩個獨立介面，符合介面隔離原則（ISP）。
 * 這讓每個 Use Case 只依賴其真正需要的能力，
 * 日後也能將讀取路徑換成讀取最佳化的實作（例如 read model / CQRS）。
 */
public interface Find{ENTITY}Port {

    // 回傳 Optional，強制呼叫端明確處理「不存在」的情境，而非依賴 null 檢查。
    Optional<{ENTITY}> findById({ENTITY}Id id);

    // 回傳全部聚合；若資料量可能很大，應加入 Pageable 參數。
    List<{ENTITY}> findAll();

    // 僅確認存在性而不載入完整聚合，避免不必要的資料庫往返
    // （例如：建立前的唯一性驗證）。
    boolean existsById({ENTITY}Id id);
}
```

## § app-service

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/application/service/{ENTITY}ApplicationService.java
package {BASE_PKG}.{CONTEXT}.application.service;

import {BASE_PKG}.{CONTEXT}.application.port.in.Create{ENTITY}Command;
import {BASE_PKG}.{CONTEXT}.application.port.in.Create{ENTITY}UseCase;
import {BASE_PKG}.{CONTEXT}.application.port.out.Find{ENTITY}Port;
import {BASE_PKG}.{CONTEXT}.application.port.out.Save{ENTITY}Port;
import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY};
import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY}Id;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 協調 {ENTITY} 相關 Use Case 的 Application Service。
 *
 * 僅依賴 application/port/out/ 中宣告的 Output Port，
 * 不直接依賴領域層的 Repository 介面或任何基礎設施型別。
 * 這樣可以在不修改此類別的情況下替換 Persistence Adapter（例如從 JPA 換成 MongoDB）。
 *
 * 聚合收集的領域事件在成功持久化後派送，
 * 讓其他 Bounded Context 能以非同步方式回應。
 */
@Service
@Transactional  // 類別層級的預設值：所有寫入方法都在交易中執行
public class {ENTITY}ApplicationService implements Create{ENTITY}UseCase {

    private final Save{ENTITY}Port save{ENTITY}Port;
    private final Find{ENTITY}Port find{ENTITY}Port;
    // ApplicationEventPublisher 由 Spring 提供；聚合的領域事件透過此介面派送，
    // 讓監聽者（@ApplicationModuleListener）在交易提交後非同步接收。
    private final ApplicationEventPublisher eventPublisher;

    public {ENTITY}ApplicationService(
            Save{ENTITY}Port save{ENTITY}Port,
            Find{ENTITY}Port find{ENTITY}Port,
            ApplicationEventPublisher eventPublisher) {
        this.save{ENTITY}Port  = save{ENTITY}Port;
        this.find{ENTITY}Port  = find{ENTITY}Port;
        this.eventPublisher    = eventPublisher;
    }

    @Override
    public {ENTITY}Id create(Create{ENTITY}Command command) {
        var id         = {ENTITY}Id.generate();
        var {ENTITY_LOWER} = {ENTITY}.create(id, command.name());

        save{ENTITY}Port.save({ENTITY_LOWER});

        // 派送聚合登錄的領域事件——其他 Context 的監聽者透過
        // @ApplicationModuleListener 接收（非同步、具有交易語意）。
        {ENTITY_LOWER}.pullDomainEvents().forEach(eventPublisher::publishEvent);

        return id;
    }

    // readOnly = true：通知 ORM 跳過 Dirty Checking 並減少鎖定開銷，
    // 讓純查詢的交易更輕量快速。
    @Transactional(readOnly = true)
    public List<{ENTITY}> findAll() {
        return find{ENTITY}Port.findAll();
    }

    // orElseThrow：將 Optional 的缺失轉換為具型別的領域例外，
    // 由 GlobalExceptionHandler 映射為 HTTP 404 + ProblemDetail。
    @Transactional(readOnly = true)
    public {ENTITY} findById({ENTITY}Id id) {
        return find{ENTITY}Port.findById(id)
                .orElseThrow(() -> new {ENTITY}NotFoundException(id));
    }
}
```

## § not-found-exception

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/application/service/{ENTITY}NotFoundException.java
package {BASE_PKG}.{CONTEXT}.application.service;

import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY}Id;

/**
 * 當請求的 {ENTITY} 不存在時，由 Application Service 拋出此例外。
 * GlobalExceptionHandler 會將此例外映射為 HTTP 404 + ProblemDetail（RFC 7807）。
 * 使用具名例外（而非通用例外）讓例外處理器能精確攔截，不干擾其他錯誤。
 */
public class {ENTITY}NotFoundException extends RuntimeException {

    public {ENTITY}NotFoundException({ENTITY}Id id) {
        super("{ENTITY} not found: " + id);
    }
}
```
