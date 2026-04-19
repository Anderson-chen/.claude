# OAuth 2.1 規範重點摘要

> 本文件摘要自 OAuth 2.1（RFC 9700 草案）、BCP 212（RFC 9700）、
> 以及相關 RFC：7636、7519、7009、8414、8628、9126、9449、8707

---

## 目錄

1. [OAuth 2.1 vs 2.0 主要差異](#1-oauth-21-vs-20-主要差異)
2. [Grant Type 規範](#2-grant-type-規範)
3. [PKCE 規範細節](#3-pkce-規範細節)
4. [Token 規範](#4-token-規範)
5. [端點安全要求](#5-端點安全要求)
6. [Refresh Token 規範](#6-refresh-token-規範)
7. [重要 RFC 清單](#7-重要-rfc-清單)

---

## 1. OAuth 2.1 vs 2.0 主要差異

### 廢棄（Removed）
| 項目 | 原 RFC | 廢棄原因 |
|------|--------|---------|
| Implicit Grant | RFC 6749 §4.2 | Token 暴露於 URL fragment，無法防 Referrer 洩漏、Open Redirector 攻擊 |
| Resource Owner Password Credentials Grant | RFC 6749 §4.3 | 直接傳輸密碼違反委託授權初衷，無法支援 MFA |
| plain code_challenge_method | RFC 7636 | 可被攔截者直接使用，毫無保護效果 |
| Bearer Token in URI Query | RFC 6750 §2.3 | Token 會進入伺服器 log、瀏覽器歷史記錄、Referrer Header |

### 新增強制要求（Newly Mandatory）
| 項目 | 適用對象 | 說明 |
|------|---------|------|
| PKCE | 所有使用 Authorization Code 的 Client | Public + Confidential Client 均強制 |
| Refresh Token Rotation 或 Sender-Constraint | 所有 Refresh Token | 防止 RT 被竊取後長期濫用 |
| redirect_uri 精確比對 | Authorization Server | 不允許任何形式的模糊比對 |

---

## 2. Grant Type 規範

### ✅ Authorization Code Grant + PKCE（主流推薦）

**適用情境**：任何有人類用戶的應用（Web App、SPA、Mobile App）

**流程**：
```
Client                    Browser               Auth Server
  |                          |                       |
  |-- 生成 code_verifier ---> |                       |
  |-- 計算 code_challenge --> |                       |
  |                          |-- GET /authorize? --> |
  |                          |   response_type=code  |
  |                          |   client_id=...       |
  |                          |   redirect_uri=...    |
  |                          |   code_challenge=...  |
  |                          |   code_challenge_     |
  |                          |   method=S256         |
  |                          |   state=...           |
  |                          |<-- 302 redirect+code--|
  |<-- code via redirect---- |                       |
  |-- POST /token ---------->|                       |
  |   code=...               |                       |
  |   code_verifier=...      |                       |
  |   client_id=...          |                       |
  |<-- access_token  --------|                       |
  |    refresh_token         |                       |
```

**安全要求清單**：
- `code_challenge_method` 必須為 `S256`
- `state` 值必須是加密強度的隨機值（min 128 bits）
- `code` 一次使用後立即作廢（Auth Server 強制）
- `code` 有效期最長 10 分鐘（建議 1-5 分鐘）
- Confidential Client 在 Token Endpoint 還需 Client Authentication

---

### ✅ Client Credentials Grant（M2M）

**適用情境**：服務對服務，**無人類用戶**

**流程**：
```
POST /token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic <base64(client_id:client_secret)>

grant_type=client_credentials&scope=read:data
```

**安全要求**：
- 必須使用 Client Authentication（Basic Auth 或 Private Key JWT）
- Scope 設計必須遵循最小權限原則
- 建議配合 mTLS 或 DPoP 進一步綁定 Token
- Access Token **不應**有太長的有效期（建議 15-60 分鐘）
- 不得發放 Refresh Token（M2M 可直接重新申請）

---

### ✅ Device Authorization Grant（RFC 8628）

**適用情境**：IoT 設備、智慧電視、CLI 工具等輸入受限設備

**流程重點**：
- Device 先呼叫 `/device_authorization` 取得 `device_code` 和 `user_code`
- 用戶在其他設備上輸入 `user_code` 完成授權
- Device 輪詢 `/token` 直到授權完成
- `interval` 欄位控制輪詢間隔（通常 5 秒）

---

### ❌ Implicit Grant（已廢棄，禁用）

**為何廢棄**：
1. `access_token` 直接附在 redirect URI 的 fragment（`#`）中
2. Fragment 可能洩漏到 Referrer Header（第三方資源載入時）
3. 無法驗證 Token 是否真的發給對的 Client
4. Browser History 中可能保留 Token
5. 無法支援 Refresh Token

**遷移方案**：改用 Authorization Code + PKCE

---

### ❌ Resource Owner Password Credentials（已廢棄，禁用）

**為何廢棄**：
1. 應用直接接觸用戶密碼，破壞委託授權的核心設計
2. 無法支援 MFA
3. 無法支援 Federated Identity
4. 鼓勵「密碼共享反模式」

**遷移方案**：改用 Authorization Code + PKCE

---

## 3. PKCE 規範細節

### code_verifier 生成規範

```
- 字元集：[A-Z] / [a-z] / [0-9] / "-" / "." / "_" / "~"
- 長度：43 到 128 個字元（建議 64）
- 亂數來源：必須使用 CSPRNG（加密強度亂數）
```

範例（JavaScript）：
```javascript
// ✅ 正確做法
const array = new Uint8Array(32);
crypto.getRandomValues(array);
const codeVerifier = base64url(array); // 產生 43 字元

// ❌ 錯誤做法
const codeVerifier = Math.random().toString(36); // 不夠隨機
```

### code_challenge 計算

```
// S256 method（強制使用）
code_challenge = BASE64URL(SHA256(ASCII(code_verifier)))

// plain method（已廢棄，OAuth 2.1 禁用）
code_challenge = code_verifier  // 完全無效的保護
```

### Auth Server 驗證邏輯

```
1. 儲存 code_challenge 和 code_challenge_method 在 authorization code 中
2. 收到 token request 時：
   - 取出 code_verifier
   - 計算 BASE64URL(SHA256(code_verifier))
   - 比對是否等於儲存的 code_challenge
   - 如果不一致 → 拒絕，返回 invalid_grant
```

---

## 4. Token 規範

### Access Token 安全要求

**JWT 格式 Access Token**：
```json
{
  "iss": "https://auth.example.com",      // 必須驗證
  "sub": "user_id",                        // 主體
  "aud": "https://api.example.com",        // 必須驗證（目標 Resource Server）
  "exp": 1700000000,                       // 必須驗證（過期時間）
  "nbf": 1699996400,                       // 建議驗證（不早於）
  "iat": 1699996400,                       // 簽發時間
  "jti": "unique_token_id",               // 建議包含（防重放）
  "scope": "read:profile write:data"      // 已授權的 Scope
}
```

**JWT 驗證清單（Resource Server 必做）**：
- [ ] 驗證 `iss`（發行者）
- [ ] 驗證 `aud`（接收者，必須包含自己）
- [ ] 驗證 `exp`（未過期）
- [ ] 驗證 `nbf`（若存在）
- [ ] 驗證簽名（用 AS 的 Public Key）
- [ ] 確認 `alg` 不是 `none`
- [ ] 確認 `alg` 在白名單中（建議：RS256、ES256、PS256）

**有效期建議**：
| 場景 | 建議有效期 |
|------|-----------|
| Web SPA | 5-15 分鐘 |
| Mobile App | 15-30 分鐘 |
| M2M / Backend | 15-60 分鐘 |
| 高安全場景 | 1-5 分鐘 |

### JWT 常見攻擊防禦

**Algorithm Confusion Attack**：
```javascript
// ❌ 危險做法
jwt.verify(token, publicKey); // 若 alg 欄位被改成 HS256，publicKey 會被當 HMAC secret

// ✅ 安全做法
jwt.verify(token, publicKey, { algorithms: ['RS256', 'ES256'] });
```

**"alg: none" Attack**：
```javascript
// Auth Server 必須拒絕 alg: none
// Resource Server 驗證時必須指定允許的 algorithm 清單
```

---

## 5. 端點安全要求

### Authorization Endpoint

| 要求 | 規範來源 |
|------|---------|
| HTTPS 強制 | RFC 9700 |
| state 參數（CSRF 防護） | RFC 9700 |
| redirect_uri 精確比對 | RFC 9700 |
| PKCE（code_challenge 必填） | RFC 9700 |
| 不在 URL 中返回 access_token | RFC 9700（廢除 Implicit） |

### Token Endpoint

| 要求 | 規範來源 |
|------|---------|
| HTTPS 強制 | RFC 6749 |
| HTTP Method：只允許 POST | RFC 6749 |
| Content-Type：application/x-www-form-urlencoded | RFC 6749 |
| Client Authentication（Confidential Client） | RFC 6749 |
| 回應包含 Cache-Control: no-store | RFC 6749 |

### 推薦的 Client Authentication 方式（優先順序）

1. **Private Key JWT**（`private_key_jwt`）— 最安全，非對稱密鑰
2. **mTLS**（`tls_client_auth`）— 憑證綁定，RFC 8705
3. **Client Secret Basic**（`client_secret_basic`）— HTTP Basic Auth
4. **Client Secret Post**（`client_secret_post`）— ⚠️ 不建議，Secret 出現在 POST body

---

## 6. Refresh Token 規範

### Refresh Token Rotation（強制）

**原理**：每次使用 Refresh Token 換取新 Access Token 時，同時發放新的 Refresh Token，舊的立即作廢。

**竊取偵測**：
- 若舊的 Refresh Token 被重複使用 → 說明 Token 可能被竊取
- Auth Server 應立即吊銷整個 Token 家族（Token Family）
- 並通知用戶

```
Session [Family: abc]
  RT_1 (initial)
    |-- used --> AT_2 + RT_2 issued, RT_1 revoked
                  |-- used --> AT_3 + RT_3 issued, RT_2 revoked
                                |
                          [RT_1 被盜用，嘗試使用]
                                |-- DETECT: RT_1 already revoked
                                    --> Revoke entire family abc
                                    --> User forced to re-login
```

### Sender-Constrained Refresh Token

替代 Rotation 的進階方案，將 RT 綁定到特定 Client（透過 mTLS 或 DPoP）。

---

## 7. 重要 RFC 清單

| RFC | 名稱 | 重要性 |
|-----|------|--------|
| RFC 9700 | OAuth 2.1 | ⭐ 核心 |
| RFC 6749 | OAuth 2.0（基礎） | ⭐ 核心 |
| RFC 7636 | PKCE | ⭐ 必讀 |
| RFC 6750 | Bearer Token Usage | ⭐ 必讀 |
| RFC 7519 | JWT | ⭐ 必讀 |
| RFC 7009 | Token Revocation | 🔶 重要 |
| RFC 8414 | Authorization Server Metadata | 🔶 重要 |
| RFC 8628 | Device Authorization Grant | 🔶 IoT 必讀 |
| RFC 9126 | Pushed Authorization Requests (PAR) | 🔷 進階 |
| RFC 9449 | DPoP | 🔷 進階 |
| RFC 8705 | mTLS Client Auth | 🔷 進階 |
| RFC 8707 | Resource Indicators | 🔷 多 RS 場景 |
| RFC 9101 | JWT Authorization Requests (JAR) | 🔷 高安全 |
| BCP 212 | OAuth Security Best Current Practice | ⭐ 必讀 |
