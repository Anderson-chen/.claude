# OAuth 常見漏洞與攻擊手法

## 目錄

1. [Authorization Code Injection](#1-authorization-code-injection)
2. [CSRF on Authorization Endpoint](#2-csrf-on-authorization-endpoint)
3. [Redirect URI Manipulation](#3-redirect-uri-manipulation)
4. [Token Leakage via Referrer/History](#4-token-leakage-via-referrerhistory)
5. [Refresh Token Theft & Replay](#5-refresh-token-theft--replay)
6. [JWT 相關攻擊](#6-jwt-相關攻擊)
7. [Client Secret 洩漏](#7-client-secret-洩漏)
8. [Token Scope Escalation](#8-token-scope-escalation)
9. [Open Redirector Attack](#9-open-redirector-attack)
10. [Mix-Up Attack](#10-mix-up-attack)

---

## 1. Authorization Code Injection

**嚴重度**：🔴 CRITICAL

**攻擊原理**：
攻擊者取得合法的 `authorization_code`（可能是他自己帳號的，或截取到的），
然後注入到受害者的 OAuth 流程中，讓受害者的 Session 與攻擊者的帳號綁定。

**攻擊場景**：
```
1. 攻擊者開始自己的授權流程，取得 code=ATTACKER_CODE
2. 攻擊者中斷流程，取得 code 後不繼續
3. 攻擊者誘騙受害者點擊帶有 code=ATTACKER_CODE 的連結
4. 受害者的應用用此 code 換取 Token → 受害者登入了攻擊者帳號
```

**防禦方式**：
- ✅ **PKCE**：Token endpoint 要求 `code_verifier`，攻擊者無法提供正確的 verifier
- ✅ **state 與 Session 綁定**：`state` 必須與用戶 Session 綁定，並在 callback 時驗證

**偵測跡象**：
- 用戶報告「我登入後看到別人的帳號資料」
- Log 中出現同一個 code 被多次使用

---

## 2. CSRF on Authorization Endpoint

**嚴重度**：🟠 HIGH

**攻擊原理**：
攻擊者製作惡意連結，讓受害者在不知情的情況下啟動攻擊者選擇的 OAuth 流程，
可能導致：帳號綁定攻擊（受害者帳號綁定到攻擊者的第三方帳號）。

**攻擊場景**：
```
1. 攻擊者製作：https://your-app.com/oauth/start?provider=github
2. 攻擊者已登入 GitHub，誘騙已登入你的應用的受害者點擊此連結
3. GitHub 授權頁自動批准（因攻擊者已登入）
4. 回調帶著攻擊者的 GitHub code 到受害者的 Session
5. 受害者的帳號被綁定到攻擊者的 GitHub 帳號
```

**防禦方式**：
- ✅ **state 參數**：必須是加密強度的隨機值，且與 Session 綁定
- ✅ **驗證 state**：在 callback 時驗證 state 是否與 Session 一致

```javascript
// ✅ 正確實作
const state = crypto.randomBytes(32).toString('hex');
session.oauthState = state;
// 在 authorization URL 加入 state
// callback 時：
if (req.query.state !== session.oauthState) {
  throw new Error('CSRF detected');
}
```

---

## 3. Redirect URI Manipulation

**嚴重度**：🔴 CRITICAL

**攻擊原理**：
如果 AS 對 `redirect_uri` 的驗證不夠嚴格，攻擊者可以讓 AS 把 `code` 送到
攻擊者控制的 URI，繞過授權。

**常見的不安全比對方式**：
```
已註冊：https://app.com/callback
攻擊者送出：

1. 前綴比對漏洞：https://app.com/callback.evil.com/steal
2. 子路徑比對：https://app.com/callback/../../steal
3. 帶參數：https://app.com/callback?redirect=https://evil.com
4. 大小寫差異：https://APP.COM/callback（取決於 AS）
5. Fragment 差異：https://app.com/callback#whatever（若 AS 忽略 fragment）
```

**防禦方式**：
- ✅ **精確的字串比對**：完全匹配，包含大小寫、路徑、查詢字串
- ✅ **提前在 AS 完整登記所有合法 URI**
- ✅ **不允許 wildcard 或正則比對**（OAuth 2.1 強制要求）

---

## 4. Token Leakage via Referrer/History

**嚴重度**：🔴 CRITICAL（若使用 Implicit Flow）

**攻擊原理**：
Implicit Flow 將 Token 放在 URL Fragment，
現代瀏覽器的某些行為可能導致 Token 洩漏。

**洩漏路徑**：
1. `Referer` Header（載入第三方資源時）
2. 瀏覽器歷史記錄
3. 伺服器 Access Log（若 Fragment 被傳到伺服器）
4. 頁面內的 JavaScript 可讀取 `location.hash`

**防禦方式**：
- ✅ **完全停用 Implicit Flow**
- ✅ 改用 Authorization Code + PKCE
- ✅ 即使使用 Code Flow，也應避免在 URL 中傳遞敏感資訊

---

## 5. Refresh Token Theft & Replay

**嚴重度**：🟠 HIGH

**攻擊原理**：
Refresh Token 通常有效期很長，如果被盜取，
攻擊者可以長期取得新的 Access Token。

**盜取方式**：
- 惡意軟體讀取儲存在 localStorage/disk 的 RT
- XSS 攻擊（若 RT 存在 JS 可讀取的位置）
- 中間人攻擊（若沒有 HTTPS）
- 內部員工洩漏

**防禦方式**：
- ✅ **Refresh Token Rotation**：每次使用 RT 後立即廢棄，發新的
- ✅ **Token Family 偵測**：舊的 RT 被重複使用 → 立即廢棄整個 Token Family
- ✅ **Sender-Constrained RT**：mTLS 或 DPoP 綁定 RT 到特定 Client
- ✅ **RT 存放安全**：HttpOnly Cookie（防 XSS 讀取）
- ✅ **RT 有效期合理**：不要設定無限期的 RT

**Rotation 後的偵測邏輯**：
```
假設 Token Family:
  RT_1 → (用過) → RT_2 → (用過) → RT_3 (當前有效)

攻擊者偷到 RT_1，嘗試使用：
  AS 發現 RT_1 已廢棄
  → 判斷 Token Family 已被洩漏
  → 廢棄 RT_2, RT_3 整個 Family
  → 強制用戶重新登入
  → 觸發安全警報
```

---

## 6. JWT 相關攻擊

### 6a. Algorithm Confusion（alg 混淆）

**嚴重度**：🔴 CRITICAL

**攻擊原理**：
某些舊版 JWT library 在驗證時使用 Token header 中的 `alg` 欄位，
攻擊者可以把 `alg` 改成 `HS256`，然後用 RS256 的 **Public Key** 作為 HMAC Secret 簽名。

```
原始 Token（RS256）：
  header: {"alg": "RS256"}
  → 驗證：用 Public Key 驗 RSA 簽名

攻擊者偽造 Token：
  header: {"alg": "HS256"}
  → 用 Public Key（攻擊者知道）作為 HMAC-SHA256 的 Secret 簽名
  → 若 library 不驗證 alg，就會接受此 Token ❌
```

**防禦**：
```javascript
// ✅ 永遠指定允許的 algorithm
jwt.verify(token, publicKey, { algorithms: ['RS256', 'ES256'] });
```

### 6b. "alg: none" Attack

**嚴重度**：🔴 CRITICAL

某些舊版 library 接受 `alg: none`，代表「無簽名」，
攻擊者可以偽造任意 payload。

**防禦**：
- 白名單允許的 algorithm，`none` 永遠不在白名單內

### 6c. JWT Expiry 不驗證

**嚴重度**：🟠 HIGH

若 Resource Server 不驗證 `exp`，過期的 Token 仍可使用。

### 6d. JWT `aud` 不驗證

**嚴重度**：🟠 HIGH

若 Resource Server A 不驗證 `aud`，發給 Resource Server B 的 Token 也能使用。
攻擊者可以拿一個低權限 RS 的 Token 來攻擊高權限 RS。

---

## 7. Client Secret 洩漏

**嚴重度**：🟠 HIGH

**常見洩漏位置**：
- Git Commit（硬編碼在程式碼中）
- 環境變數被 log 出來
- 前端程式碼中（SPA 打包時）
- Docker Image 中
- CI/CD Pipeline log

**前端不能有 client_secret**：
SPA（React/Vue/Angular）是 Public Client，根本不應該有 client_secret，
因為任何用戶都可以查看前端程式碼。

**防禦**：
- ✅ 使用環境變數或 Secret Vault（HashiCorp Vault、AWS Secrets Manager）
- ✅ 定期 Rotation client_secret
- ✅ 使用 Private Key JWT 替代 client_secret
- ✅ `.gitignore` 所有包含 secret 的設定檔
- ✅ 啟用 Git Secrets 掃描工具

---

## 8. Token Scope Escalation

**嚴重度**：🟡 MEDIUM

**攻擊原理**：
若 Scope 設計過於寬鬆，或 AS 在 Token 中給予超過用戶請求的 Scope，
攻擊者可以利用 Token 存取原本不應存取的資源。

**防禦**：
- ✅ 遵循最小權限原則（Least Privilege）
- ✅ AS 絕對不授予超過 Client 已請求的 Scope
- ✅ Resource Server 驗證 Token 中的 Scope 是否足夠（不多也不少）
- ✅ 細粒度 Scope 設計（如 `read:users` 而非只有 `admin`）

---

## 9. Open Redirector Attack

**嚴重度**：🟠 HIGH（搭配其他漏洞時升為 CRITICAL）

**攻擊原理**：
若你的應用本身有 Open Redirector 漏洞（如 `/redirect?url=xxx`），
攻擊者可能構造：
```
https://trusted-app.com/redirect?url=https://evil.com/steal
```
然後把這個 URL 當作 `redirect_uri` 發給 AS（若 AS 驗證不夠嚴格）。

**防禦**：
- ✅ 不要在你的應用中建立 Open Redirector
- ✅ redirect_uri 精確比對（AS 端）
- ✅ 若有 Redirect 功能，使用白名單

---

## 10. Mix-Up Attack

**嚴重度**：🔴 CRITICAL（多 IdP 環境）

**攻擊原理**：
在用戶可以選擇多個 IdP 的情況下，攻擊者可能讓受害者使用 IdP-A 的 code，
但發到 IdP-B 的 Token Endpoint，從而竊取 code。

**防禦**：
- ✅ **Issuer Identification**：AS 的 issuer 要包含在授權回應中
- ✅ **PAR（Pushed Authorization Requests，RFC 9126）**：防止 Mix-Up
- ✅ 在 `state` 或 `iss` 中記錄預期的 IdP，並在 callback 時驗證
