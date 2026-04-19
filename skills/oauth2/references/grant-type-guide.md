# Grant Type 選型指引

## 快速決策樹

```
你的應用是否有人類用戶參與授權？
│
├─ 是 ──────────────────────────────────────────────────────┐
│                                                            │
│  用戶使用什麼設備？                                         │
│  │                                                         │
│  ├─ 一般瀏覽器 / Web App / SPA / Mobile App               │
│  │   └──► Authorization Code Grant + PKCE  ✅              │
│  │        （Confidential Client 同樣需要 PKCE）            │
│  │                                                         │
│  └─ IoT / 智慧電視 / CLI / 輸入受限設備                    │
│      └──► Device Authorization Grant (RFC 8628)  ✅        │
│                                                            │
└─ 否（純機器對機器，無用戶）──────────────────────────────────┘
    │
    └──► Client Credentials Grant  ✅
         （搭配 mTLS 或 DPoP 更安全）
```

---

## 各 Grant Type 詳細比較

### Authorization Code + PKCE ✅

| 屬性 | 說明 |
|------|------|
| RFC | RFC 9700（OAuth 2.1）, RFC 7636（PKCE） |
| 適用 Client | Public Client（SPA、Native App）、Confidential Client（Web App with Backend） |
| 是否需要用戶互動 | ✅ 是 |
| 安全等級 | 🟢 高 |
| 複雜度 | 🟡 中 |

**何時選這個**：
- 用戶需要登入你的應用
- 用戶需要授權你的應用代表他存取資源
- 任何有瀏覽器可用的場景

**實作重點**：
- Public Client（SPA/Mobile）：**只能靠 PKCE** 確保安全（沒有 client_secret）
- Confidential Client（有 Backend）：PKCE + client_secret 雙重保護
- `state` 參數必填（CSRF 防護）
- `redirect_uri` 必須精確比對，提前在 AS 中註冊

---

### Client Credentials Grant ✅

| 屬性 | 說明 |
|------|------|
| RFC | RFC 9700, RFC 6749 §4.4 |
| 適用 Client | Confidential Client（後端服務） |
| 是否需要用戶互動 | ❌ 否 |
| 安全等級 | 🟢 高（若搭配 mTLS/DPoP） |
| 複雜度 | 🟢 低 |

**何時選這個**：
- 微服務之間的 API 呼叫
- Batch Job 或 Scheduled Task 需要存取 API
- 沒有代表特定用戶的情境

**實作重點**：
- 必須使用強 Client Authentication（建議 Private Key JWT 或 mTLS）
- Scope 設計要最小化（least privilege）
- **不發放 Refresh Token**（直接重新申請 AT）
- 考慮 DPoP 綁定防 Token 被偷後濫用

---

### Device Authorization Grant ✅（RFC 8628）

| 屬性 | 說明 |
|------|------|
| RFC | RFC 8628 |
| 適用 Client | Public Client（無瀏覽器設備） |
| 是否需要用戶互動 | ✅ 是（在另一個設備） |
| 安全等級 | 🟡 中（視實作而定） |
| 複雜度 | 🟡 中 |

**何時選這個**：
- 智慧電視 App
- CLI 工具（如 `gh auth login`）
- IoT 設備（鍵盤輸入困難）
- 印表機、遊戲主機

**安全注意事項**：
- `user_code` 應足夠短且易輸入（通常 8 字元）
- `user_code` 有效期建議 5-15 分鐘
- 輪詢間隔必須遵守 `interval` 欄位（通常 5 秒）
- 若 `slow_down` 錯誤，間隔需增加 5 秒

---

### ❌ Implicit Grant（已廢棄，禁止使用）

**為何廢棄的詳細說明**：

1. **Referrer Header 洩漏**：
   ```
   用戶被重導到 https://app.com/callback#access_token=SECRET
   頁面載入第三方圖片/腳本時：
   Referer: https://app.com/callback#access_token=SECRET
   → Token 被第三方收到 ❌
   ```

2. **瀏覽器歷史記錄**：
   Token 出現在 URL Fragment，存在瀏覽器歷史記錄中

3. **Open Redirector 攻擊**：
   無 PKCE 保護，code/token 可被竊取

4. **無法驗證接收者**：
   AS 無法確認 Token 真的給了對的應用

**遷移方案**：
```diff
- response_type=token
+ response_type=code
+ code_challenge=<PKCE S256>
+ code_challenge_method=S256
```

---

### ❌ Resource Owner Password Credentials（已廢棄，禁止使用）

**為何廢棄的詳細說明**：

1. **破壞安全委託模型**：
   用戶將密碼直接給了第三方應用，違背「委託授權」初衷

2. **無法支援 MFA**：
   密碼輸入框無法插入 OTP/FIDO2 流程

3. **鼓勵密碼共享反模式**：
   應用儲存密碼 → 密碼重複使用風險

4. **無法支援 Federated Identity**：
   無法重導到 Google/GitHub 等 IdP

**遷移方案**：
- 有瀏覽器 → Authorization Code + PKCE
- 無瀏覽器 → Device Authorization Grant
- 第一方應用特殊情境 → 考慮 [Client-Initiated Backchannel Authentication (CIBA)](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html)

---

## 特殊情境選型

### 第一方應用（你同時擁有 Client 和 AS）

雖然 ROPC 在此情境曾被使用，OAuth 2.1 仍廢除它。
**建議做法**：使用 Authorization Code + PKCE，但簡化用戶體驗（如自動重導、無需額外同意頁）。

### 離線存取（需要長期 Refresh Token）

使用 Authorization Code + PKCE，並在 scope 中請求 `offline_access`（OpenID Connect）。
確保 RT Rotation 已實作。

### 細粒度授權（Fine-Grained Authorization）

搭配 Resource Indicators（RFC 8707）將不同 Resource Server 的 Token 分開，
避免一個 Token 可以存取所有資源。

### 多租戶（Multi-Tenant）

- 每個 Tenant 有獨立的 Client Registration
- AS 的 `iss` 要能區分 Tenant（如 `https://auth.example.com/tenants/{id}`）
- 或使用支援多租戶的 AS（如 Auth0 Organization、Keycloak Realm）
