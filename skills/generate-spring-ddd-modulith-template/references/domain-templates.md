# Domain Layer Templates

These are pure Java — **no Spring dependencies** in this layer.

## § package-info

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/package-info.java
//
// 為什麼需要這個檔案？
// Spring Modulith 以 package 識別模組邊界。這個 package-info.java 宣告此 Context
// 允許依賴哪些外部模組。沒有它，Modulith 仍可運作，但邊界是未宣告的 —
// 加上這個檔案可讓意圖明確，並在 ApplicationModulesTest 執行時自動驗證。
//
// 直接放在此 package 下（不在子套件內）的類別，會成為此模組的公開 API。
// 放在子套件（domain/, application/, adapter/）中的一切，對其他模組是不可見的，
// 除非在此明確 expose。
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = "commons"
    // 若需要讀取另一個 Context 的公開 API（例如 catalog）：
    // allowedDependencies = {"commons", "catalog"}
)
package {BASE_PKG}.{CONTEXT};
```

## § aggregate-root-base

```java
// commons/src/main/java/{BASE_PATH}/commons/domain/AggregateRoot.java
package {BASE_PKG}.shared.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 所有 DDD Aggregate Root 的基底類別。
 * Aggregate Root 是一致性邊界（Consistency Boundary）：
 * 所有對此聚合的修改都必須通過 Root，以確保不變式（Invariant）始終成立。
 *
 * @param <ID> 強型別識別碼類型（應為 Value Object record）
 */
public abstract class AggregateRoot<ID> {

    // 在聚合內部收集領域事件，確保事件只在交易提交後才被派送，
    // 避免監聽者對從未真正持久化的資料做出反應。
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // 強制每個聚合暴露強型別識別碼，禁止使用原始型別 UUID，
    // 以防止將錯誤的 ID 傳入錯誤的方法（Primitive Obsession 反模式）。
    public abstract ID getId();

    /**
     * 將領域事件加入待派送清單，待交易提交後由 Application Service 統一派送。
     * 設為 protected，確保只有子類別（聚合）能夠觸發事件，
     * 外部程式碼無法直接向聚合注入事件。
     */
    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    /**
     * 取出所有待派送的領域事件，並清空內部清單。
     * 採用 Pull（拉取）而非 Push（推送）語意：由 Application Service 決定何時派送，
     * 讓聚合完全不依賴事件匯流排（Event Bus）。
     * 取出後立即清空，確保每個事件只被派送一次（Exactly Once）。
     */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = Collections.unmodifiableList(new ArrayList<>(domainEvents));
        domainEvents.clear();
        return events;
    }
}
```

## § domain-event-base

```java
// commons/src/main/java/{BASE_PATH}/commons/domain/DomainEvent.java
package {BASE_PKG}.shared.domain;

import java.time.Instant;

/**
 * 所有領域事件的標記介面（Marker Interface）。
 * 實作類別應使用 Java record，以確保事件不可變（Immutable），
 * 不可變事件可安全地跨執行緒傳遞，且無需防禦性複製。
 */
public interface DomainEvent {

    /**
     * 事件在領域中發生的時刻，由聚合設定，而非由基礎設施層設定。
     * 使用 Instant（UTC）讓領域模型與時區無關。
     * 監聽者可用此值進行事件排序與冪等性（Idempotency）檢查。
     */
    Instant occurredOn();
}
```

## § value-object-base

```java
// commons/src/main/java/{BASE_PATH}/commons/domain/ValueObject.java
package {BASE_PKG}.shared.domain;

/**
 * 領域值物件（Value Object）的標記介面。
 * Value Object 沒有識別碼，以結構性相等（Structural Equality）判斷是否相同。
 * 建議以 Java record 實作：record 預設提供結構相等、不可變性與簡潔語法。
 */
public interface ValueObject {}
```

## § aggregate-root

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/domain/model/{ENTITY}.java
package {BASE_PKG}.{CONTEXT}.domain.model;

import {BASE_PKG}.shared.domain.AggregateRoot;
import {BASE_PKG}.{CONTEXT}.domain.event.{ENTITY}CreatedEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * {ENTITY} 聚合的 Aggregate Root。
 *
 * 業務規則在此強制執行，這是一致性邊界（Consistency Boundary）。
 * 所有領域邏輯應放在此類別中；不應將業務規則放在 Application Service 裡。
 */
public class {ENTITY} extends AggregateRoot<{ENTITY}Id> {

    // final：識別碼不可變，聚合建立後 ID 永遠不會改變
    private final {ENTITY}Id id;
    // 可變狀態：所有修改必須通過具名方法，由方法內強制不變式，不對外暴露 setter
    private String name;
    // final：建立時間是歷史事實，不允許事後修改
    private final Instant createdAt;

    // 私有建構子：唯一合法的建立路徑是以下兩個靜態工廠方法。
    // 這確保每個實例要嘛觸發建立事件（create），要嘛靜默還原（reconstitute）。
    private {ENTITY}({ENTITY}Id id, String name, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be blank");
        this.createdAt = createdAt;
    }

    /**
     * 建立全新聚合的工廠方法。
     * 使用具名靜態工廠（而非公開建構子）能清楚傳達語意，
     * 且允許方法在回傳前先行登錄領域事件。
     * 事件將在交易提交後由 Application Service 派送。
     */
    public static {ENTITY} create({ENTITY}Id id, String name) {
        var entity = new {ENTITY}(id, name, Instant.now());
        entity.registerEvent(new {ENTITY}CreatedEvent(id, name, entity.createdAt));
        return entity;
    }

    /**
     * 從持久化狀態重建聚合（Reconstitution）。
     * 與 create() 分開，因為從儲存層重新載入不應觸發領域事件——
     * 這些事件在聚合最初建立時就已發生過了。
     */
    public static {ENTITY} reconstitute({ENTITY}Id id, String name, Instant createdAt) {
        return new {ENTITY}(id, name, createdAt);
    }

    /**
     * 領域修改方法：封裝「名稱不得為空白」的不變式。
     * 業務規則的執行屬於領域層，而非 Application Service，
     * 確保無論誰呼叫此方法，領域模型始終保持有效狀態。
     */
    public void rename(String newName) {
        Objects.requireNonNull(newName, "name must not be null");
        if (newName.isBlank()) throw new IllegalArgumentException("name must not be blank");
        this.name = newName;
    }

    @Override public {ENTITY}Id getId()  { return id; }
    public String    getName()           { return name; }
    public Instant   getCreatedAt()      { return createdAt; }
}
```

## § entity-id

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/domain/model/{ENTITY}Id.java
package {BASE_PKG}.{CONTEXT}.domain.model;

import {BASE_PKG}.shared.domain.ValueObject;

import java.util.UUID;

/**
 * {ENTITY} 的強型別識別碼（Typed Identity）。
 * 使用 record value type 可避免不同聚合的 UUID 被意外交換傳遞，
 * 編譯器會在型別不符時即時報錯（消除 Primitive Obsession）。
 */
public record {ENTITY}Id(UUID value) implements ValueObject {

    // Compact constructor：record 沒有方法體可放驗證邏輯，
    // compact constructor 是 Value Object 強制 non-null 的標準位置。
    public {ENTITY}Id {
        java.util.Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * 為全新聚合產生隨機識別碼。
     * 集中於此，若日後需要改用 UUID v7（時間排序）等策略，
     * 只需修改此一處，無須改動聚合程式碼。
     */
    public static {ENTITY}Id generate() {
        return new {ENTITY}Id(UUID.randomUUID());
    }

    /**
     * 包裝既有 UUID，用於從持久層重建聚合或接收外部系統傳入的識別碼。
     */
    public static {ENTITY}Id of(UUID value) {
        return new {ENTITY}Id(value);
    }

    /**
     * 解析字串 UUID，用於從 HTTP 路徑參數或訊息 Payload 反序列化。
     * 格式錯誤時拋出 IllegalArgumentException，由 Web Adapter 的例外處理器映射為 HTTP 400。
     */
    public static {ENTITY}Id of(String value) {
        return new {ENTITY}Id(UUID.fromString(value));
    }

    @Override public String toString() { return value.toString(); }
}
```

## § value-object

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/domain/model/Money.java
// 注意：僅在第一個 Bounded Context 或 shared-kernel 中產生，避免重複定義。
package {BASE_PKG}.{CONTEXT}.domain.model;

import {BASE_PKG}.shared.domain.ValueObject;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * 不可變的金額值物件（Value Object）。
 * record 讓值物件寫法簡潔，且預設以結構相等（Structural Equality）比較，
 * 不需要手動實作 equals/hashCode。
 */
public record Money(BigDecimal amount, Currency currency) implements ValueObject {

    // Compact constructor：在物件建立時強制驗證，確保值物件永遠處於有效狀態，
    // 不存在「金額為負」或「幣別為 null」的無效實例。
    public Money {
        Objects.requireNonNull(amount,   "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("amount must not be negative");
    }

    /**
     * 以字串幣別代碼建立金額，方便從外部 API 或設定值轉換。
     */
    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    /**
     * 回傳相加後的新 Money 實例。
     * 不可變：運算結果是新物件，原始物件不受影響，符合 Value Object 語意。
     * 幣別不同時拋出例外，因為不同幣別相加在領域上無意義。
     */
    public Money add(Money other) {
        if (!this.currency.equals(other.currency))
            throw new IllegalArgumentException("Cannot add different currencies");
        return new Money(this.amount.add(other.amount), this.currency);
    }

    @Override public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
```

## § domain-event

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/domain/event/{ENTITY}CreatedEvent.java
package {BASE_PKG}.{CONTEXT}.domain.event;

import {BASE_PKG}.shared.domain.DomainEvent;
import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY}Id;

import java.time.Instant;

/**
 * 當新的 {ENTITY} 成功建立時發佈此領域事件。
 *
 * 其他 Bounded Context 的監聽者應使用 @ApplicationModuleListener
 * 以非同步方式回應此事件，避免直接依賴（緊耦合）。
 * 使用 record 確保事件不可變，可安全地序列化與跨模組傳遞。
 */
public record {ENTITY}CreatedEvent(
        {ENTITY}Id {ENTITY_LOWER}Id,
        String name,
        Instant occurredOn
) implements DomainEvent {}
```

## § repository-port

```java
// {CONTEXT}/src/main/java/{BASE_PATH}/{CONTEXT}/domain/repository/{ENTITY}Repository.java
package {BASE_PKG}.{CONTEXT}.domain.repository;

import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY};
import {BASE_PKG}.{CONTEXT}.domain.model.{ENTITY}Id;

import java.util.List;
import java.util.Optional;

/**
 * 領域儲存庫介面（Hexagonal Architecture 中的 Output Port）。
 *
 * 此介面位於領域層，定義領域「需要什麼」儲存能力；
 * 基礎設施層的 Adapter 實作「如何」儲存。
 * 這樣的設計讓領域層完全不依賴 JPA、資料庫或任何框架。
 */
public interface {ENTITY}Repository {

    // Upsert 語意：領域層不關心這是 INSERT 還是 UPDATE，
    // 這個區別屬於持久層 Adapter 的責任，而非領域層。
    void save({ENTITY} {ENTITY_LOWER});

    // 回傳 Optional，強制呼叫端明確處理「找不到」的情境，
    // 避免 NullPointerException，使「實體不存在」成為一等概念。
    Optional<{ENTITY}> findById({ENTITY}Id id);

    // 回傳全部聚合；若資料量可能很大，應加入分頁參數。
    List<{ENTITY}> findAll();

    // 僅確認存在性，不載入完整聚合，避免不必要的資料庫往返與物件還原開銷
    // （例如：建立前的唯一性驗證）。
    boolean existsById({ENTITY}Id id);

    // 接受強型別 ID 而非原始 UUID，防止誤刪錯誤聚合類型的資料。
    void deleteById({ENTITY}Id id);
}
```
