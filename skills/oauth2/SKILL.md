---
name: oauth2-oidc-security-auditor
description: >
  使用此 Skill 來審查、設計或評估任何涉及 OAuth 2.0 / OAuth 2.1 / OpenID Connect（OIDC）
  的認證授權流程。當用戶提到：OAuth、OIDC、OpenID Connect、id_token、UserInfo、
  access token、refresh token、PKCE、client credentials、authorization code flow、
  redirect URI、JWT、SSO、單一登入、第三方登入、Google Login、社群登入、
  API 認證安全、token 洩漏、implicit flow、session 管理、登出流程、
  nonce、claims、scope openid、或任何身份驗證架構設計時，**務必觸發此 Skill**。
  支援情境：審查現有專案是否符合 OAuth 2.1 / OIDC 規範、設計新專案的認證授權流程、
  找出安全漏洞、生成合規報告、提供修補建議。即使用戶只說「幫我看看這個登入流程安不安全」
  也應觸發此 Skill。
---

# OAuth 2.1 + OpenID Connect 安全審查與設計 Skill

## 概述

本 Skill 引導你透過**逐步情境訪談**，對 OAuth 2.0/2.1 與 OpenID Connect（OIDC）
實作進行完整的安全審查，或協助設計符合最新規範的全新流程。

**OAuth 2.1**（RFC 9700）處理「授權」（Authorization）：你的應用能做什麼。
**OpenID Connect 1.0**（OIDC）建立在 OAuth 2.1 之上，處理「身份驗證」（Authentication）：用戶是誰。
兩者常同時使用，但有不同的安全考量，本 Skill 均完整涵蓋。

---

## 快速導覽

| 我想要… | 請跳至… |
|---------|---------|
| 審查現有專案是否符合規範 | [模式 A：現有專案審查](#模式-a現有專案審查) |
| 設計全新的 OAuth / OIDC 流程 | [模式 B：新專案設計](#模式-b新專案設計) |
| 快速查閱 OAuth 2.1 規範重點 | [references/oauth21-spec.md](references/oauth21-spec.md) |
| 快速查閱 OIDC 規範重點 | [references/oidc-spec.md](references/oidc-spec.md) |
| 查看完整安全檢查清單 | [checklists/security-checklist.md](checklists/security-checklist.md) |
| 了解各 Grant Type 選擇指引 | [references/grant-type-guide.md](references/grant-type-guide.md) |
| 查看常見 OIDC 錯誤與漏洞 | [references/common-vulnerabilities.md](references/common-vulnerabilities.md) |

---

## 核心原則：逐步訪談

> **重要**：不論使用哪種模式，**絕對不要一次丟出所有問題**。
> 每個階段問完後，先消化用戶回答、提供初步判斷，再進入下一階段。
> 這樣才能精準聚焦真正有風險的地方。

---

## 模式 A：現有專案審查

### 階段 0 — 破冰與定錨

先了解用戶的背景與急迫性：

```
問題集 A0（一次問，選擇題優先）：
1. 你目前的主要擔憂是什麼？
   □ 懷疑有安全漏洞  □ 準備上線前檢查  □ 合規要求（ISO/SOC2 等）
   □ 被滲透測試報告點出問題  □ 純粹想了解現況

2. 你對 OAuth / OIDC 的熟悉程度？
   □ 我寫了所有相關程式碼  □ 我使用第三方 library  □ 我只負責設計/管理

3. 目前使用的是哪一個版本或規範？
   □ OAuth 2.0（標準）  □ OAuth 2.1（草案/正式）  □ 不確定  □ 混用

4. 是否有使用 OpenID Connect（OIDC）做身份驗證？
   □ 是（有 id_token、UserInfo endpoint）
   □ 否（純 OAuth，只做授權）
   □ 不確定（我只知道有「登入功能」）
```

根據 A0 回答 → 決定審查深度與優先順序。
**若用戶回答有 OIDC 或不確定** → 階段 1 起加入 OIDC 相關問題。

---

### 階段 1 — 架構全貌

```
問題集 A1：
1. 這個系統中有哪些角色？（可複選）
   □ 自建 Authorization Server / OpenID Provider（AS/OP）
   □ 使用第三方 IdP（如 Auth0、Keycloak、Azure AD、Google）
   □ Resource Server（API 伺服器）
   □ 多個 Client 應用程式（Web / Mobile / SPA / CLI / Backend）

2. 請簡述目前的授權流程（能說多少說多少，可以貼程式碼或流程圖）

3. 目前使用哪些 Grant Type？
   □ Authorization Code  □ Authorization Code + PKCE
   □ Client Credentials  □ Device Code
   □ Implicit（⚠ 高風險）  □ Resource Owner Password（⚠ 高風險）
   □ 不確定

4. 若有使用 OIDC，請回答：
   □ 有請求 `openid` scope
   □ 有使用 `id_token` 做身份驗證
   □ 有使用 UserInfo Endpoint 取得用戶資料
   □ 有 nonce 實作
   □ 以上都不確定
```

**審查邏輯**（內部判斷用）：
- 若出現 `Implicit` → 立即標記 🔴 CRITICAL，OAuth 2.1 已廢除
- 若出現 `Resource Owner Password` → 立即標記 🔴 CRITICAL，OAuth 2.1 已廢除
- 若 Authorization Code **沒有** PKCE → 標記 🟠 HIGH
- 若有 OIDC 但**沒有 nonce** → 標記 🟠 HIGH（Replay Attack 風險）
- 若把 `id_token` 當 Access Token 使用 → 標記 🔴 CRITICAL（設計錯誤）

---

### 階段 2 — Client 端安全

```
問題集 A2（依 A1 的 Client 類型調整）：

[若有 SPA / Mobile App]
- 是否實作 PKCE（Proof Key for Code Exchange）？
- code_verifier 是如何產生的（長度？亂數來源？）
- 是否使用 state 參數防 CSRF？
- Access Token 存放在哪裡？
  □ Memory（JS 變數）□ localStorage □ sessionStorage □ Cookie（httpOnly？）

[若有後端 Confidential Client]
- client_secret 如何管理（環境變數？Vault？硬編碼？）
- 是否有 client secret rotation 機制？
- mTLS 或 DPoP 是否有考慮？

[通用]
- redirect_uri 是完整比對還是前綴比對？
- 是否有 redirect_uri 白名單？
```

---

### 階段 3 — Token 管理

```
問題集 A3：
1. Access Token 類型？
   □ JWT（自包含）  □ Opaque（需 introspection）  □ 不確定

2. Access Token 有效期？_____ 分鐘
   Refresh Token 有效期？_____ 天

3. 是否實作 Refresh Token Rotation？
   □ 是  □ 否  □ 不知道什麼是 Rotation

4. Token 吊銷（Revocation）機制？
   □ 有（RFC 7009）  □ 沒有  □ 只靠短效期

5. 若是 JWT：
   - 簽名演算法？（□ RS256  □ HS256  □ ES256  □ 其他：___）
   - 是否驗證 iss / aud / exp / nbf？
   - 是否有 alg:none 防護？
   - JWK Set 如何取得（靜態 or 動態拉取）？
```

---

### 階段 4 — 傳輸與端點安全

```
問題集 A4：
1. 所有 OAuth 端點是否強制 HTTPS？
2. Authorization endpoint 是否有 CSRF 防護（state 或 nonce）？
3. 是否實作 PKCE 的 S256 method（而非 plain）？
4. Token endpoint 是否限制 HTTP Method 為 POST？
5. CORS 設定是否限制為必要的 Origin？
6. 是否有 Rate Limiting 在 token endpoint？
7. 是否記錄授權/Token 相關的 Audit Log？
```

---

### 階段 5 — 特殊情境追問（依前面答案選擇性問）

若有 Refresh Token → 問：
```
- Refresh Token 是否與 Client 綁定（binding）？
- 是否偵測 Refresh Token Reuse（偵測竊取）？
- Refresh Token 存放位置與 Access Token 是否分開？
```

若有多個 Resource Server → 問：
```
- 是否使用 Resource Indicators（RFC 8707）隔離 Token Audience？
- Resource Server 如何驗證 Token（本地驗證 JWT / Introspection）？
```

若是 B2B / Machine-to-Machine → 問：
```
- Client Credentials flow 中，是否有 Scope 最小權限設計？
- 是否使用 DPoP 或 mTLS 綁定 Token 到 Client？
```

若有使用 OIDC（A0 回答有 OIDC）→ 進入階段 5b：

---

### 階段 5b — OIDC 專項審查

```
問題集 A5b：
1. id_token 驗證
   - 是否驗證 id_token 的簽名？
   - 是否驗證 iss（發行者）？
   - 是否驗證 aud（接收者，必須是自己的 client_id）？
   - 是否驗證 exp（過期）？
   - 是否驗證 nonce（防 Replay Attack）？
   - 是否驗證 at_hash（若有使用 Hybrid Flow）？

2. nonce 實作
   - nonce 是否是加密強度隨機值？
   - nonce 是否與用戶 Session 綁定？
   - 是否在 id_token 回來後驗證 nonce 並標記為已使用？

3. id_token 的用途
   - id_token 是否被用來呼叫 API（當成 Access Token）？
     ⚠️ 這是常見的嚴重錯誤，id_token 只能用於身份驗證，不應用於授權 API 存取
   - 是否使用 access_token 呼叫 UserInfo Endpoint？

4. SSO / Session 管理（若適用）
   - 是否實作 OIDC Session Management？
   - 是否支援 Front-Channel Logout 或 Back-Channel Logout？
   - RP-Initiated Logout 是否安全實作（post_logout_redirect_uri 驗證）？

5. Claims 使用
   - 是否只從 id_token 或 UserInfo 取得必要的 Claims？
   - 是否依賴 sub（Subject）作為用戶的唯一識別碼？
   - 是否有使用 email 作為唯一識別？
     ⚠️ email 可能被更改或重複使用，應使用 sub + iss 組合作為唯一識別
```

**OIDC 審查邏輯**（內部判斷用）：
- `id_token` 被當 Access Token 使用 → 🔴 CRITICAL
- 缺少 nonce 驗證 → 🟠 HIGH（Replay Attack）
- 未驗證 `aud` → 🔴 CRITICAL（Token 替換攻擊）
- 用 email 做唯一識別（未搭配 iss+sub）→ 🟡 MEDIUM
- 無 Logout 機制（尤其 SSO 場景）→ 🟠 HIGH

---

### 階段 6 — 輸出審查報告

收集完所有資訊後，產生以下格式的報告：

```markdown
## OAuth 2.1 + OIDC 安全審查報告

### 整體評分：[🔴 不合格 / 🟠 需改善 / 🟡 基本合格 / 🟢 符合規範]

### 適用規範範圍
- OAuth 2.1（RFC 9700）：[適用 / 不適用]
- OpenID Connect 1.0：[適用 / 不適用]

### 🔴 CRITICAL（必須立即修復）
| # | 問題 | 說明 | 修補建議 | 參考規範 |
|---|------|------|---------|---------|
| 1 | ... | ... | ... | RFC xxxx |

### 🟠 HIGH（建議盡快處理）
...

### 🟡 MEDIUM（計畫內改善）
...

### 🟢 已符合的最佳實踐
...

### 行動計畫（優先順序）
1. 立即（本週）：...
2. 短期（1 個月）：...
3. 中期（季度）：...
```

---

## 模式 B：新專案設計

### 階段 0 — 需求定錨

```
問題集 B0：
1. 這個系統的主要使用情境是？
   □ 用戶透過網頁登入你的應用  □ 用戶透過手機 App 登入
   □ 你的 API 要讓第三方呼叫  □ 服務與服務之間（無人類用戶）
   □ IoT / 限制輸入設備  □ 以上混合

2. 你需要「身份驗證」還是「授權」，還是兩者都要？
   □ 只需要知道「用戶是誰」（Authentication）→ 需要 OIDC
   □ 只需要讓應用存取資源（Authorization）→ 純 OAuth
   □ 兩者都要（登入 + API 存取）→ OAuth 2.1 + OIDC
   □ 不確定（Claude 請在此主動說明差異）

3. 你是否需要建立自己的 Authorization Server / OpenID Provider？
   □ 是，我要自建（如 Keycloak、自寫）
   □ 否，我要整合現有 IdP（如 Google、GitHub、Auth0）
   □ 還沒決定

4. 這個系統的安全等級要求？
   □ 一般消費者應用  □ 企業內部系統  □ 金融/醫療/政府（高合規要求）

5. 是否有 SSO（跨多個應用單一登入）需求？
   □ 是  □ 否  □ 未來可能需要
```

---

### 階段 1 — Grant Type 選型

根據 B0 的回答，引導選擇正確的 Grant Type：

```
決策樹（Claude 在這裡主動建議，不要只問不說）：

有人類用戶 + Web/Mobile App
  → 強制建議：Authorization Code + PKCE
  → 若需要身份驗證（知道「誰」登入）→ 加上 OIDC（scope: openid）
  → 禁止：Implicit Flow、Resource Owner Password

服務對服務（無人類用戶）
  → 建議：Client Credentials
  → 考慮：mTLS 或 DPoP 強化
  → OIDC 不適用（無用戶）

IoT / 限制輸入設備
  → 建議：Device Authorization Grant（RFC 8628）
  → 若需要身份驗證 → 可加 OIDC

如果用戶提到任何廢棄 Flow → 主動說明為何廢棄、提供替代方案
```

追問：
```
- 你的 Client 是 Public（SPA、Native App）還是 Confidential（有後端的應用）？
- 你需要 SSO 跨多個應用嗎？→ 需要 OIDC Session Management
- 你需要「以用戶身份呼叫其他 API」（Delegation）嗎？
- 你需要取得用戶的哪些資料（email、name、picture）？→ OIDC Claims 設計
```

**OIDC vs 純 OAuth 關鍵說明**（若用戶不確定，主動解釋）：
```
OAuth 2.1 → 解決「授權」問題：「這個 App 可以存取你的 Google Drive 嗎？」
            結果是 access_token，代表「被允許做某件事的憑證」

OpenID Connect → 解決「身份驗證」問題：「這個用戶到底是誰？」
                 結果是 id_token，代表「這個用戶的身份證明」

常見錯誤：把 id_token 傳給 API 當 access_token 用 ← 🔴 CRITICAL 設計錯誤
正確做法：id_token 只在 Client 端用來確認用戶身份，
          呼叫 API 永遠使用 access_token
```

---

### 階段 2 — Token 設計決策

```
問題集 B2：
1. 你傾向使用哪種 Token 格式？
   □ JWT（無需查詢，適合分散式）
   □ Opaque（需要 introspection，較好撤銷）
   □ 讓我來建議

2. 你的 Resource Server 是否在同一個信任域（Trust Domain）？

3. 對 Token 有效期的期望？
   - Access Token：越短越安全，建議 5-15 分鐘
   - Refresh Token：依業務需求，是否需要 sliding expiry？

4. 是否需要細粒度的 Scope 設計？（最小權限原則）
```

根據回答，提供具體的 Token 設計方案。

---

### 階段 3 — 安全加固選項

提供勾選式的安全增強清單，讓用戶決定實作哪些：

```
基礎（所有新專案必做）：
✅ PKCE（所有 Public Client 強制）
✅ state 參數防 CSRF
✅ redirect_uri 精確比對白名單
✅ HTTPS 強制
✅ Refresh Token Rotation

若有 OIDC（必做）：
✅ nonce 參數（防 Replay Attack）
✅ id_token 完整驗證（iss / aud / exp / nonce / 簽名）
✅ 使用 sub + iss 作為用戶唯一識別（不用 email）
✅ id_token 與 access_token 用途分開（不混用）
✅ 實作登出（RP-Initiated Logout）

進階（建議實作）：
□ DPoP（Demonstrating Proof of Possession，RFC 9449）
□ PAR（Pushed Authorization Requests，RFC 9126）
□ Token Binding
□ Resource Indicators（RFC 8707）
□ OIDC Back-Channel Logout（RFC 推薦，比 Front-Channel 更可靠）

高安全等級（金融/醫療）：
□ mTLS Client Authentication（RFC 8705）
□ JWT Authorization Requests（JAR，RFC 9101）
□ JARM（JWT Secured Authorization Response Mode）
□ ACR（Authentication Context Class Reference）強制要求 MFA
```

---

### 階段 4 — 輸出設計文件

產生包含以下內容的設計文件：

```markdown
## OAuth 2.1 流程設計文件

### 架構概覽
[流程圖描述]

### Grant Type 選擇與理由
...

### Token 設計規格
| Token 類型 | 格式 | 有效期 | 存放位置 | 簽名/加密 |
|-----------|------|--------|---------|---------|

### 端點清單
| 端點 | URL Pattern | 安全機制 |
|-----|------------|---------|

### 實作步驟（優先順序）
1. ...

### 需要避免的常見錯誤
...

### 相關 RFC 參考
...
```

---

## 內部審查邏輯參考

Claude 在審查時，需要對照以下核心規則（詳細規範見 `references/oauth21-spec.md`）：

### OAuth 2.1 廢棄項目（MUST NOT）
| 廢棄項目 | 風險 | 替代方案 |
|---------|------|---------|
| Implicit Flow | Token 暴露在 URL Fragment，可被 Referrer 洩漏 | Authorization Code + PKCE |
| Resource Owner Password Grant | 直接處理密碼，破壞 OAuth 設計初衷 | Authorization Code + PKCE 或 Device Flow |
| plain PKCE code_challenge_method | 可被降級攻擊 | 強制使用 S256 |
| Bearer Token in URI Query | Token 會進 Log/History | Header 傳遞 |

### OAuth 2.1 強制要求（MUST）
- Authorization Code Flow **必須** 使用 PKCE（Public Client 和 Confidential Client 均適用）
- redirect_uri **必須** 精確比對（不允許 wildcard 或前綴比對）
- Refresh Token **必須** 是一次性使用（Rotation）或受發送方約束（Sender-Constrained）
- 所有端點 **必須** 使用 HTTPS

---

## 常見漏洞速查

讀取 `references/common-vulnerabilities.md` 可取得完整攻擊模式說明。

| 漏洞 | 觸發條件 | 嚴重度 |
|------|---------|--------|
| Authorization Code Injection | 缺少 PKCE 或 state | 🔴 CRITICAL |
| Token Leakage via Referrer | 使用 Implicit Flow | 🔴 CRITICAL |
| Redirect URI Hijacking | URI 非精確比對 | 🔴 CRITICAL |
| id_token 當 Access Token 使用 | OIDC 設計錯誤 | 🔴 CRITICAL |
| id_token aud 未驗證 | 可接受其他 Client 的 id_token | 🔴 CRITICAL |
| Refresh Token Theft | 缺少 Rotation + 長效 RT | 🟠 HIGH |
| JWT Algorithm Confusion | 未驗證 alg、允許 none | 🟠 HIGH |
| CSRF on Authorization | 缺少 state/nonce | 🟠 HIGH |
| OIDC Replay Attack | 缺少 nonce 驗證 | 🟠 HIGH |
| SSO Logout 不完整 | 未實作 Back/Front-Channel Logout | 🟠 HIGH |
| 用 email 作唯一識別碼 | email 可被更改或重用 | 🟡 MEDIUM |
| Token Scope Creep | Scope 設計過於寬鬆 | 🟡 MEDIUM |
| Insufficient Audit Logging | 無法追蹤異常授權 | 🟡 MEDIUM |

---

## 參考文件

- `references/oauth21-spec.md` — OAuth 2.1 規範重點摘要（RFC 9700 + BCP 212）
- `references/oidc-spec.md` — OpenID Connect 1.0 規範重點摘要
- `references/grant-type-guide.md` — Grant Type 選型指引與決策樹
- `references/common-vulnerabilities.md` — 常見 OAuth / OIDC 攻擊手法與防禦
- `checklists/security-checklist.md` — 完整安全檢查清單（可直接輸出給團隊使用）
- `checklists/code-review-checklist.md` — 程式碼審查專用清單
