# Scoped Values 與 Virtual Thread 的 context 傳遞 — 2026-08-17

> 主題：T1 Java Core｜版本前提：Java 25

## L1 — 怎麼使用
1. [L1] 你手上這段程式用 ThreadLocal 存 request 的 traceId，如果要改成 Scoped Values，寫法上會變成什麼樣子？

## L2 — 運作原理
2. [L2] ScopedValue 的值是綁在哪裡的？為什麼它在 Virtual Thread 大量建立的情境下，記憶體行為會比 ThreadLocal 好？

## L3 — 為什麼存在
3. [L3] ThreadLocal 用了二十幾年都沒事，為什麼到了 Loom 時代非得再做一套 Scoped Values 不可？不做會怎樣？

## 延伸題
4. [反面] 什麼情境下 Scoped Values 反而不適用，你還是得留著 ThreadLocal？
5. [情境] 一個 API gateway 每秒開 50000 個 virtual thread 處理請求，每條都要帶 tenantId 往下傳三層服務，你會怎麼設計這個 context 傳遞？
6. [規模] 如果往下傳的層級從 3 層變成 20 層，中間還有非同步 fork 出去的子任務，你的方案要怎麼調整？
7. [跨主題] 這種「隱式傳遞 context」的設計，跟分散式追蹤（distributed tracing）裡的 trace context 傳播有什麼相似與不同？
