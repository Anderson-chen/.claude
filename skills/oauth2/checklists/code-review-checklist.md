# OAuth 2.1 程式碼審查清單

> 給開發者在 Code Review 時快速掃描使用

---

## 快速掃描 Pattern（高風險程式碼搜尋）

在程式碼庫中搜尋以下 pattern，發現即標記審查：

```bash
# 搜尋可能使用 Implicit Flow 的跡象
grep -r "response_type=token" .
grep -r "ResponseType.*token" .

# 搜尋 Resource Owner Password Grant
grep -r "grant_type=password" .
grep -r "GrantType.*password" .

# 搜尋 plain PKCE
grep -r "code_challenge_method=plain" .
grep -r "plain.*challenge" .

# 搜尋 localStorage 存 Token
grep -r "localStorage.*token" . --include="*.js" --include="*.ts"
grep -r "localStorage.*access" . --include="*.js" --include="*.ts"

# 搜尋硬編碼的 client_secret
grep -r "client_secret.*=" . --include="*.js" --include="*.ts" --include="*.py"

# 搜尋 alg: none
grep -r '"none"' . --include="*.js" --include="*.ts"
grep -r "algorithm.*none" .

# 搜尋不驗證 JWT 的跡象
grep -r "verify.*false" .
grep -r "ignoreExpiration.*true" .
```

---

## 關鍵程式碼審查點

### PKCE 實作審查

```javascript
// ❌ 不安全：使用 Math.random()
const codeVerifier = Math.random().toString(36).repeat(3);

// ❌ 不安全：長度不足
const codeVerifier = crypto.randomBytes(10).toString('base64url'); // 只有 ~14 字元

// ✅ 安全：正確實作
function generateCodeVerifier() {
  const array = new Uint8Array(32); // 32 bytes → 43 字元 base64url
  crypto.getRandomValues(array);
  return base64url(array); // 必須是 URL-safe base64，無 padding
}

async function generateCodeChallenge(verifier) {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return base64url(digest); // BASE64URL(SHA256(verifier))
}
```

---

### State 參數審查

```javascript
// ❌ 不安全：可預測的 state
const state = Date.now().toString();
const state = userId + '_oauth';

// ❌ 不安全：未與 Session 綁定
const state = crypto.randomUUID(); // 生成後沒有存到 Session

// ✅ 安全：正確實作
function startOAuthFlow(req) {
  const state = crypto.randomBytes(32).toString('hex');
  req.session.oauthState = state; // 存到 Session
  req.session.oauthStartTime = Date.now(); // 可選：限制有效期
  return buildAuthorizationUrl({ state });
}

function handleCallback(req) {
  const { state, code } = req.query;
  
  // 必須驗證 state
  if (!state || state !== req.session.oauthState) {
    throw new Error('Invalid state - possible CSRF attack');
  }
  
  // 清除使用過的 state
  delete req.session.oauthState;
  
  // 繼續 token exchange...
}
```

---

### JWT 驗證審查

```javascript
// ❌ 不安全：信任 Token header 的 alg
const decoded = jwt.decode(token); // 只解碼，不驗證
const payload = jwt.verify(token, secret); // 使用 Token 自己說的 alg

// ❌ 不安全：不驗證 aud
jwt.verify(token, publicKey, {
  algorithms: ['RS256'],
  // 缺少 audience 驗證
});

// ❌ 不安全：跳過過期驗證
jwt.verify(token, publicKey, {
  algorithms: ['RS256'],
  ignoreExpiration: true, // 危險！
});

// ✅ 安全：完整驗證
const payload = jwt.verify(token, publicKey, {
  algorithms: ['RS256', 'ES256'], // 白名單
  issuer: 'https://auth.example.com', // 驗證 iss
  audience: 'https://api.example.com', // 驗證 aud
  // exp 預設會驗證
});
```

---

### Redirect URI 審查

```javascript
// ❌ 不安全：前綴比對
function isValidRedirectUri(registered, provided) {
  return provided.startsWith(registered); // 危險！
}

// ❌ 不安全：包含比對
function isValidRedirectUri(registered, provided) {
  return provided.includes(registered); // 危險！
}

// ❌ 不安全：正則比對（若 pattern 寫得不好）
function isValidRedirectUri(pattern, provided) {
  return new RegExp(pattern).test(provided); // 容易有漏洞
}

// ✅ 安全：精確字串比對
function isValidRedirectUri(registeredUris, provided) {
  return registeredUris.includes(provided); // 精確比對陣列中的任一個
}
```

---

### Token 存放審查（前端）

```javascript
// ❌ 不安全：localStorage（可被 XSS 讀取）
localStorage.setItem('access_token', token);

// ❌ 不安全：sessionStorage（也可被 XSS 讀取）
sessionStorage.setItem('access_token', token);

// ✅ 安全：Memory（最安全，但頁面重新整理後會消失）
let accessToken = null; // 存在 JS 變數中（關閉分頁就消失）

// ✅ 安全：HttpOnly Cookie（防 XSS，但要防 CSRF）
// 由 Backend 設定：
res.cookie('refresh_token', rt, {
  httpOnly: true,    // JavaScript 無法讀取
  secure: true,      // 只透過 HTTPS 傳送
  sameSite: 'strict' // 防 CSRF
});
```

---

### Refresh Token Rotation 審查

```javascript
// ❌ 不安全：RT 可以重複使用
app.post('/token', async (req, res) => {
  const { refresh_token } = req.body;
  const user = await validateRefreshToken(refresh_token);
  const newAccessToken = generateAccessToken(user);
  // 舊的 RT 沒有廢棄！攻擊者可以一直使用
  res.json({ access_token: newAccessToken, refresh_token }); 
});

// ✅ 安全：Rotation + 竊取偵測
app.post('/token', async (req, res) => {
  const { refresh_token } = req.body;
  
  // 查詢 RT 狀態
  const rtRecord = await db.findRefreshToken(refresh_token);
  
  if (!rtRecord) {
    return res.status(401).json({ error: 'invalid_grant' });
  }
  
  if (rtRecord.used) {
    // ⚠️ 舊的 RT 被重複使用 → 可能是竊取
    await db.revokeTokenFamily(rtRecord.familyId);
    return res.status(401).json({ error: 'invalid_grant' });
  }
  
  // 廢棄舊的 RT
  await db.markRefreshTokenUsed(refresh_token);
  
  // 發放新的 RT（同一個 Family）
  const newRefreshToken = await db.createRefreshToken({
    userId: rtRecord.userId,
    familyId: rtRecord.familyId,
  });
  
  const newAccessToken = generateAccessToken(rtRecord.userId);
  
  res.json({
    access_token: newAccessToken,
    refresh_token: newRefreshToken,
    expires_in: 900,
  });
});
```

---

## 常見框架/Library 審查重點

### Node.js / Express

```javascript
// 注意：passport-oauth2 預設可能不強制 PKCE
// 需要手動確認設定

// jsonwebtoken library 注意：
// 1. 指定 algorithms 選項
// 2. 不使用 jwt.decode() 驗證（它不驗證簽名）
```

### Python / Django / FastAPI

```python
# authlib / python-jose：確認 algorithm 白名單
# django-oauth-toolkit：確認版本是否支援 PKCE

# 危險：
payload = jwt.decode(token, key)  # 某些 library 預設信任 alg header

# 安全：
payload = jwt.decode(token, key, algorithms=["RS256", "ES256"])
```

### Java / Spring

```java
// Spring Security OAuth：確認版本，舊版可能不支援 PKCE
// nimbus-jose-jwt：確認 JWSAlgorithm 白名單

// 危險：接受任何演算法
JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

// 安全：驗證演算法
if (!JWSAlgorithm.RS256.equals(signedJWT.getHeader().getAlgorithm())) {
    throw new SecurityException("Unsupported algorithm");
}
```
