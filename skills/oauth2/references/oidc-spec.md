# OpenID Connect 1.0 規範重點摘要

> 本文件摘要自 OpenID Connect Core 1.0、OpenID Connect Discovery 1.0、
> OpenID Connect Session Management 1.0、
> OpenID Connect Front-Channel Logout 1.0、Back-Channel Logout 1.0

---

## 目錄

1. [OAuth 2.1 vs OIDC — 職責分工](#1-oauth-21-vs-oidc--職責分工)
2. [OIDC 核心流程](#2-oidc-核心流程)
3. [id_token 規範與驗證](#3-id_token-規範與驗證)
4. [nonce 規範](#4-nonce-規範)
5. [UserInfo Endpoint](#5-userinfo-endpoint)
6. [Claims 與 Scope](#6-claims-與-scope)
7. [SSO 與 Logout](#7-sso-與-logout)
8. [OIDC Discovery](#8-oidc-discovery)
9. [常見錯誤模式](#9-常見錯誤模式)
10. [重要規範清單](#10-重要規範清單)

---

## 1. OAuth 2.1 vs OIDC — 職責分工

```
┌─────────────────────────────────────────────────────────┐
│                   OpenID Connect 1.0                    │
│         （身份驗證層 Authentication Layer）              │
│                                                         │
│   id_token（用戶是誰）                                  │
│   UserInfo Endpoint（取得用戶屬性）                     │
│   nonce（防 Replay Attack）                             │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │               OAuth 2.1                          │  │
│  │         （授權層 Authorization Layer）            │  │
│  │                                                   │  │
│  │   access_token（應用能做什麼）                    │  │
│  │   refresh_token（長期存取）                       │  │
│  │   PKCE、state（安全機制）                         │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

**核心區分原則**：
- `id_token` → 給 Client 用來知道「用戶是誰」，**不應**傳給 API
- `access_token` → 給 API 用來知道「允許做什麼」，用來呼叫 Resource Server
- 兩者**不可互換**，混用是最常見的嚴重設計錯誤

---

## 2. OIDC 核心流程

### Authorization Code Flow + PKCE（推薦）

```
Client                          Browser              OpenID Provider（OP）
  |                                |                         |
  |-- 生成 code_verifier ---------->|                         |
  |-- 生成 nonce ----------------->|                         |
  |                                |                         |
  |                                |-- GET /authorize? ----->|
  |                                |   response_type=code    |
  |                                |   scope=openid profile  |
  |                                |   client_id=...         |
  |                                |   redirect_uri=...      |
  |                                |   code_challenge=...    |
  |                                |   code_challenge_method=S256
  |                                |   state=...             |
  |                                |   nonce=...  ← OIDC 新增|
  |                                |                         |
  |                                |<-- 用戶登入 + 同意 ------|
  |                                |<-- 302 redirect+code ---|
  |<-- code via redirect ----------|                         |
  |                                |                         |
  |-- POST /token ---------------->|                         |
  |   code=...                     |                         |
  |   code_verifier=...            |                         |
  |                                |                         |
  |<-- access_token (OAuth) -------|                         |
  |    id_token     (OIDC)  ← 新增 |                         |
  |    refresh_token               |                         |
  |                                |                         |
  |-- 驗證 id_token -------------->|（在 Client 端驗證）      |
  |   [iss, aud, exp, nonce, sig]  |                         |
  |                                |                         |
  |-- GET /userinfo （可選）------->|                         |
  |   Authorization: Bearer <AT>   |                         |
  |<-- { sub, email, name, ... }---|                         |
```

---

## 3. id_token 規範與驗證

### id_token 結構（JWT）

```json
// Header
{
  "alg": "RS256",
  "kid": "key-id-for-rotation"
}

// Payload（必要 Claims）
{
  "iss": "https://accounts.google.com",  // 發行者，必須驗證
  "sub": "110169484474386276334",         // 用戶唯一識別碼（不會改變）
  "aud": "client_id_of_your_app",        // 接收者，必須是你的 client_id
  "exp": 1700000000,                     // 過期時間，必須驗證
  "iat": 1699996400,                     // 簽發時間
  "nonce": "n-0S6_WzA2Mj",              // 防 Replay，必須驗證（若有帶）

  // 可選但常見
  "email": "user@example.com",
  "email_verified": true,
  "name": "John Doe",
  "picture": "https://...",
  "at_hash": "....",                     // Hybrid Flow 時驗證 access_token
  "c_hash": "....",                      // Hybrid Flow 時驗證 code
  "auth_time": 1699996000,               // 用戶實際驗證時間
  "acr": "urn:mace:incommon:iap:silver"  // 驗證等級（如是否用 MFA）
}
```

### Client 端必須執行的驗證步驟（順序）

```
1. 解碼 id_token（不驗證）取得 header.kid
2. 從 jwks_uri 取得對應 kid 的 Public Key
3. 驗證簽名（使用 Public Key + header.alg）
4. 驗證 iss = 預期的 OP issuer
5. 驗證 aud 包含自己的 client_id
6. 驗證 exp > 現在時間
7. 驗證 iat 不是太舊（可選，建議容忍 ±5 分鐘 clock skew）
8. 驗證 nonce = 之前送出的 nonce（必須）
9. 若有 azp：驗證 azp = client_id
10. 若有 at_hash（Hybrid Flow）：驗證 access_token 的 hash
```

---

## 4. nonce 規範

### 用途

防止 **Replay Attack**：攻擊者截取舊的 id_token 後重新使用。

### 生成規範

```javascript
// ✅ 正確：加密強度隨機值
const nonce = crypto.randomBytes(32).toString('base64url');

// 存到 Session（在送出授權請求前）
session.oidcNonce = nonce;
session.oidcNonceExpiry = Date.now() + 10 * 60 * 1000; // 10 分鐘
```

### 授權請求中加入 nonce

```
GET /authorize?
  ...
  nonce=<random_value>   ← 加入授權請求
```

### OP 如何處理 nonce

OP 必須將 nonce 原封不動地放入 id_token 的 `nonce` claim。

### Client 驗證 nonce

```javascript
function verifyIdToken(idToken, session) {
  const payload = jwt.verify(idToken, publicKey, {
    algorithms: ['RS256'],
    issuer: 'https://accounts.google.com',
    audience: CLIENT_ID,
  });

  // 驗證 nonce
  if (!payload.nonce || payload.nonce !== session.oidcNonce) {
    throw new Error('Invalid nonce - possible replay attack');
  }

  // 驗證 nonce 未過期
  if (Date.now() > session.oidcNonceExpiry) {
    throw new Error('Nonce expired');
  }

  // 使用後清除（防重複使用）
  delete session.oidcNonce;
  delete session.oidcNonceExpiry;

  return payload;
}
```

---

## 5. UserInfo Endpoint

### 用途

取得比 id_token 更詳細的用戶屬性（避免 id_token 過大）。

### 呼叫方式

```http
GET /userinfo
Authorization: Bearer <access_token>   ← 使用 access_token，不是 id_token
```

### 回應

```json
{
  "sub": "110169484474386276334",
  "name": "John Doe",
  "given_name": "John",
  "family_name": "Doe",
  "email": "john@example.com",
  "email_verified": true,
  "picture": "https://example.com/photo.jpg"
}
```

### 安全注意事項

- 必須驗證 UserInfo 回應中的 `sub` 與 id_token 中的 `sub` **一致**
  （防止 Token 替換攻擊：用別人的 access_token 來取得不同用戶的資料）
- UserInfo Endpoint 必須需要有效的 access_token

---

## 6. Claims 與 Scope

### 標準 Scope 對應的 Claims

| Scope | 取得的 Claims |
|-------|-------------|
| `openid` | sub（必須）|
| `profile` | name, given_name, family_name, middle_name, nickname, preferred_username, profile, picture, website, gender, birthdate, zoneinfo, locale, updated_at |
| `email` | email, email_verified |
| `address` | address |
| `phone` | phone_number, phone_number_verified |
| `offline_access` | 允許發放 refresh_token（OIDC 場景） |

### 最小權限原則

只請求必要的 scope，不要一次請求所有 scope。

### sub 的重要性

`sub`（Subject Identifier）是 OP 為每個用戶指派的**不可變**唯一識別碼。
同一個用戶在同一個 OP 的 `sub` 永遠不變。

**正確的用戶唯一識別方式**：
```javascript
// ✅ 正確：使用 iss + sub 組合
const userId = `${payload.iss}|${payload.sub}`;

// ❌ 危險：只用 email（email 可以被更改，也可能被重複分配）
const userId = payload.email;

// ❌ 危險：只用 sub（不同 OP 可能有相同的 sub）
const userId = payload.sub;
```

---

## 7. SSO 與 Logout

### RP-Initiated Logout

用戶主動登出，Client（RP）向 OP 發起登出請求。

```
GET https://op.example.com/logout?
  id_token_hint=<id_token>             ← 告訴 OP 是哪個用戶
  post_logout_redirect_uri=https://app.example.com/logged-out
  state=<random_value>                 ← 防 CSRF
```

**安全要求**：
- `post_logout_redirect_uri` 必須預先在 OP 登記
- OP 必須精確比對 `post_logout_redirect_uri`（防 Open Redirector）
- Client 收到 logout callback 時也要驗證 `state`

### Front-Channel Logout

OP 透過 iframe 通知各個 RP 登出。

```
OP 在自己的登出頁面嵌入：
<iframe src="https://app1.com/logout?sid=...&iss=..."></iframe>
<iframe src="https://app2.com/logout?sid=...&iss=..."></iframe>
```

**缺點**：依賴瀏覽器載入 iframe，不可靠（網路問題、iframe 被封鎖）。

### Back-Channel Logout（推薦）

OP 直接對每個 RP 的後端發送 HTTP POST，更可靠。

```
POST https://app.example.com/backchannel-logout
Content-Type: application/x-www-form-urlencoded

logout_token=<signed_JWT>
```

**logout_token 驗證**：
```javascript
// logout_token 是一個特殊的 JWT，需驗證：
// - iss, aud（同 id_token）
// - iat（簽發時間，不太舊）
// - jti（唯一，防重放）
// - events: { "http://schemas.openid.net/event/backchannel-logout": {} }
// - sub 或 sid（至少其一）
// 不應包含 nonce
```

---

## 8. OIDC Discovery

OP 發布 Discovery Document，讓 Client 自動發現端點。

**URL**：`https://op.example.com/.well-known/openid-configuration`

**重要欄位**：
```json
{
  "issuer": "https://op.example.com",
  "authorization_endpoint": "https://op.example.com/authorize",
  "token_endpoint": "https://op.example.com/token",
  "userinfo_endpoint": "https://op.example.com/userinfo",
  "jwks_uri": "https://op.example.com/.well-known/jwks.json",
  "end_session_endpoint": "https://op.example.com/logout",
  "scopes_supported": ["openid", "profile", "email"],
  "response_types_supported": ["code"],
  "id_token_signing_alg_values_supported": ["RS256", "ES256"],
  "subject_types_supported": ["public"],
  "claims_supported": ["sub", "iss", "name", "email"]
}
```

**Client 使用 Discovery 的好處**：
- 端點自動更新（不需要硬編碼）
- Key Rotation 自動支援（透過 jwks_uri 動態拉取）

---

## 9. 常見錯誤模式

### ❌ 最常見：id_token 當 Access Token 用

```javascript
// ❌ 錯誤
fetch('/api/data', {
  headers: { 'Authorization': `Bearer ${idToken}` } // id_token 不應傳給 API
});

// ✅ 正確
fetch('/api/data', {
  headers: { 'Authorization': `Bearer ${accessToken}` } // 使用 access_token
});
```

### ❌ 解碼 id_token 但不驗證簽名

```javascript
// ❌ 危險：只 decode，攻擊者可以偽造
const payload = JSON.parse(atob(idToken.split('.')[1]));

// ✅ 安全：必須驗證簽名
const payload = await jose.jwtVerify(idToken, jwks, {
  issuer: 'https://op.example.com',
  audience: CLIENT_ID,
});
```

### ❌ 用 email 作為唯一識別碼

某些 OP 允許用戶更改 email，或在帳號刪除後重新分配 email。
應使用 `sub`（永不改變）作為主鍵，`email` 只用於顯示。

### ❌ 不驗證 nonce 導致 Replay Attack

截取過的舊 id_token 可以被重新使用讓受害者登入。
每次授權流程都必須生成新的 nonce 並在驗證時核對。

### ❌ 未驗證 UserInfo 回應的 sub

```javascript
// ❌ 危險：直接信任 UserInfo 回應
const userInfo = await fetchUserInfo(accessToken);
const userId = userInfo.sub;

// ✅ 安全：確認 UserInfo.sub 與 id_token.sub 一致
const userInfo = await fetchUserInfo(accessToken);
if (userInfo.sub !== idTokenPayload.sub) {
  throw new Error('UserInfo sub mismatch - possible token substitution');
}
```

---

## 10. 重要規範清單

| 規範 | 名稱 | 重要性 |
|------|------|--------|
| OpenID Connect Core 1.0 | 核心規範 | ⭐ 必讀 |
| OpenID Connect Discovery 1.0 | Discovery Document | ⭐ 必讀 |
| OpenID Connect Session Management 1.0 | Session 管理 | 🔶 重要 |
| OpenID Connect Front-Channel Logout 1.0 | 前端登出 | 🔶 重要 |
| OpenID Connect Back-Channel Logout 1.0 | 後端登出（推薦） | 🔶 重要 |
| RFC 9700 | OAuth 2.1 | ⭐ 必讀（底層） |
| RFC 7636 | PKCE | ⭐ 必讀 |
| RFC 7519 | JWT | ⭐ 必讀 |
| RFC 7517 | JWK | 🔶 重要 |
