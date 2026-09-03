# Scoped Values 與 Virtual Thread 的 context 傳遞 — 2026-08-17（解答）

> 版本前提：Java 25（Scoped Values 已定案）｜⚠️ 此答案對應 Java 25，未來版本變動需重新確認

## L1
1. `ScopedValue.where(TRACE_ID, id).run(() -> ...)`，值只在 lambda 的動態範圍內可見；宣告用 `static final ScopedValue<String>`。關鍵詞：不可變綁定、動態範圍、無 set/remove。

## L2
2. 綁定在「執行範圍」而非執行緒物件上，離開 run() 自動解除，不需 remove()。ThreadLocal 的 map 掛在 Thread 物件上，百萬條 virtual thread 就是百萬份 map 且易漏 remove 造成洩漏。關鍵詞：不可變、範圍結束即回收、無洩漏風險。

## L3
3. ThreadLocal 的三個前提在 Loom 下全破：執行緒昂貴稀少、可變、生命週期長。Virtual Thread 是廉價海量短命的，繼承 ThreadLocal 的成本被放大成記憶體災難。Scoped Values 用不可變 + 明確範圍換掉可變 + 隱式生命週期。trade-off：失去中途改值的彈性。

## 延伸題
4. [反面] 需要在執行過程中改值（如快取累加）、或值的生命週期不對齊呼叫堆疊時，Scoped Values 的不可變與範圍限制反而卡手。
5. [情境] 在 gateway 入口一次 `ScopedValue.where(TENANT, t).run(...)` 包住整個處理鏈，下游直接讀，不必逐層當參數傳。
6. [規模] 層級變深不影響（動態範圍自然穿透）；fork 子任務要搭配 StructuredTaskScope，子任務才會繼承綁定，這正是兩者被設計成一起用的原因。
7. [跨主題] 相似：都是隱式傳遞的 ambient context。不同：Scoped Values 是行程內、由語言執行期保證範圍；trace context 要跨行程，得靠 W3C traceparent header 序列化，且沒有自動解綁機制。

---

## 📐 本輪維度表現（弱點地圖）

| 維度 | 表現 | 缺口 |
|---|---|---|
| 溝通 | ●●●○ | 有講清楚，偶爾跳步 |
| 問題拆解 | ●●●○ | — |
| 技術實作 | ●●○○ | L2 停在「比較省記憶體」，沒講到範圍結束即回收 |
| 測試與邊界 | ○○○○ | ← 這輪掛零，完全沒提怎麼驗證沒有洩漏 |

**累計觀察**：連續 2 輪在「測試與邊界」掛零。
