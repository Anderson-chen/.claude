# OAuth 2.1 + OpenID Connect 完整安全檢查清單

> 版本：1.1 | 基於 OAuth 2.1（RFC 9700）、BCP 212、OpenID Connect Core 1.0
> 可直接輸出給團隊使用，每個項目標記 ✅ / ❌ / N/A

---

## A. Grant Type 合規性

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| A1 | 未使用 Implicit Grant | 🔴 | | OAuth 2.1 已廢除 |
| A2 | 未使用 Resource Owner Password Grant | 🔴 | | OAuth 2.1 已廢除 |
| A3 | 所有 Authorization Code Flow 均使用 PKCE | 🔴 | | Public + Confidential Client 均適用 |
| A4 | PKCE 使用 S256 method（非 plain） | 🔴 | | plain method 已廢棄 |
| A5 | code_verifier 使用 CSPRNG 生成 | 🟠 | | 不可用 Math.random() |
| A6 | code_verifier 長度 43-128 字元 | 🟠 | | 建議 64 字元 |
| A7 | M2M 場景正確使用 Client Credentials Grant | 🟡 | | |
| A8 | IoT/限制輸入設備使用 Device Grant | 🟡 | | 若適用 |

---

## B. Authorization Endpoint 安全

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| B1 | Authorization Endpoint 強制 HTTPS | 🔴 | | |
| B2 | state 參數已實作（CSRF 防護） | 🟠 | | |
| B3 | state 是加密強度的隨機值（≥128 bits） | 🟠 | | |
| B4 | state 與用戶 Session 綁定 | 🟠 | | |
| B5 | callback 時驗證 state | 🟠 | | |
| B6 | redirect_uri 精確比對（非前綴/wildcard） | 🔴 | | |
| B7 | redirect_uri 白名單已預先在 AS 登記 | 🔴 | | |
| B8 | authorization code 一次使用後立即作廢 | 🔴 | | |
| B9 | authorization code 有效期 ≤ 10 分鐘 | 🟠 | | 建議 1-5 分鐘 |
| B10 | 不透過 URL Fragment 傳回 access_token | 🔴 | | 即廢除 Implicit |

---

## C. Token Endpoint 安全

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| C1 | Token Endpoint 強制 HTTPS | 🔴 | | |
| C2 | 只接受 POST 方法 | 🟠 | | |
| C3 | Content-Type 必須為 application/x-www-form-urlencoded | 🟠 | | |
| C4 | 回應包含 Cache-Control: no-store | 🟠 | | |
| C5 | Confidential Client 有 Client Authentication | 🟠 | | |
| C6 | Client Secret 未硬編碼在程式碼中 | 🔴 | | |
| C7 | Client Secret 有 Rotation 機制 | 🟡 | | |
| C8 | 有 Rate Limiting 防止暴力攻擊 | 🟠 | | |

---

## D. Access Token 安全

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| D1 | Access Token 有合理有效期（≤60 分鐘） | 🟠 | | 建議 5-15 分鐘 |
| D2 | 未在 URL Query Parameter 傳遞 Token | 🔴 | | 用 Authorization Header |
| D3 | Token 存放在安全位置（非 localStorage） | 🟠 | | SPA 用 Memory |
| D4 | Token 未出現在 Log 中 | 🟠 | | |

**若使用 JWT**：

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| D5 | Resource Server 驗證 iss（發行者） | 🔴 | | |
| D6 | Resource Server 驗證 aud（目標受眾） | 🔴 | | |
| D7 | Resource Server 驗證 exp（過期時間） | 🔴 | | |
| D8 | Resource Server 驗證 JWT 簽名 | 🔴 | | |
| D9 | 不接受 alg: none | 🔴 | | |
| D10 | algorithm 使用白名單驗證（非信任 header 的 alg） | 🔴 | | |
| D11 | 簽名演算法為 RS256 / ES256 / PS256 | 🟠 | | 避免 HS256 於多方信任 |
| D12 | JWK Set 定期更新（支援 key rotation） | 🟡 | | |

---

## E. Refresh Token 安全

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| E1 | 已實作 Refresh Token Rotation | 🟠 | | OAuth 2.1 強制要求之一 |
| E2 | 舊的 RT 使用後立即廢棄 | 🟠 | | |
| E3 | 偵測 RT 重複使用（Token Family 廢棄） | 🟠 | | |
| E4 | RT 有合理有效期（非無限） | 🟠 | | |
| E5 | RT 存放在 HttpOnly Cookie 或安全位置 | 🟠 | | 防 XSS 讀取 |
| E6 | RT 與 Client 綁定（若支援） | 🟡 | | |
| E7 | M2M 場景不發放 RT | 🟡 | | Client Credentials 不需要 RT |

---

## F. 傳輸安全

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| F1 | 所有 OAuth 相關 URL 強制 HTTPS | 🔴 | | |
| F2 | TLS 版本 ≥ 1.2（建議 1.3） | 🟠 | | |
| F3 | HSTS Header 已設定 | 🟡 | | |
| F4 | 不允許 HTTP 降級 | 🟠 | | |

---

## G. 用戶端存放安全

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| G1 | SPA：Access Token 存在 Memory（非 localStorage） | 🟠 | | |
| G2 | SPA：Refresh Token 使用 HttpOnly Cookie | 🟠 | | |
| G3 | Mobile App：使用 Keychain/KeyStore | 🟠 | | |
| G4 | Cookie 設定 Secure + HttpOnly + SameSite | 🟠 | | |
| G5 | 未將 Token 存在 sessionStorage | 🟡 | | 仍可被 XSS 讀取 |

---

## H. Token 吊銷與生命週期

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| H1 | 登出時吊銷 Access Token（若 Opaque）| 🟠 | | |
| H2 | 登出時吊銷 Refresh Token | 🟠 | | |
| H3 | 已實作 Token Revocation API（RFC 7009） | 🟡 | | |
| H4 | 密碼更改時吊銷所有 Token | 🟠 | | |
| H5 | 帳號停用時立即吊銷所有 Token | 🟠 | | |
| H6 | 支援前端登出（Front-Channel Logout，若 SSO） | 🟡 | | |

---

## I. CORS 與 Web 安全

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| I1 | Token Endpoint 的 CORS 設定限制 Origin | 🟠 | | 不能設 * |
| I2 | Authorization Server 有適當的 CSP | 🟡 | | |
| I3 | 無 XSS 漏洞（避免 Token 被 JS 讀取） | 🔴 | | |
| I4 | X-Frame-Options 防止 Clickjacking | 🟡 | | |

---

## J. 日誌與監控

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| J1 | 授權事件有 Audit Log | 🟠 | | |
| J2 | Token 發放/使用/吊銷有記錄 | 🟠 | | |
| J3 | Log 中不包含完整的 Token 值 | 🟠 | | 最多記錄前幾碼 |
| J4 | 異常授權行為有告警 | 🟡 | | |
| J5 | RT 重複使用有即時告警 | 🟠 | | |

---

## K. Authorization Server 設定（若自建）

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| K1 | AS Metadata Endpoint 已發布（RFC 8414） | 🟡 | | /.well-known/oauth-authorization-server |
| K2 | 不支援 Implicit 和 ROPC Grant | 🔴 | | |
| K3 | 強制 PKCE（即使是 Confidential Client） | 🔴 | | |
| K4 | redirect_uri 必須精確比對 | 🔴 | | |
| K5 | Client Registration 有管理流程 | 🟠 | | |
| K6 | 定期 Key Rotation（JWK） | 🟠 | | |
| K7 | PAR（Pushed Authorization Requests）支援 | 🟡 | | 建議 |

---

## L. OpenID Connect — id_token 驗證（若使用 OIDC）

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| L1 | id_token 簽名已驗證（使用 OP 的 Public Key） | 🔴 | | |
| L2 | 驗證 id_token 的 iss（必須符合 OP issuer） | 🔴 | | |
| L3 | 驗證 id_token 的 aud（必須包含自己的 client_id） | 🔴 | | |
| L4 | 驗證 id_token 的 exp（未過期） | 🔴 | | |
| L5 | 驗證 nonce（若授權請求有帶 nonce） | 🟠 | | 防 Replay Attack |
| L6 | nonce 是加密強度隨機值且與 Session 綁定 | 🟠 | | |
| L7 | nonce 使用後標記為已使用（防重複使用） | 🟠 | | |
| L8 | 若使用 Hybrid Flow，驗證 at_hash / c_hash | 🟠 | | |
| L9 | id_token 未被當成 Access Token 使用 | 🔴 | | 最常見的 OIDC 設計錯誤 |
| L10 | id_token 未直接傳送至 Resource Server / API | 🔴 | | |
| L11 | alg 不接受 none（同 JWT 規範） | 🔴 | | |

---

## M. OpenID Connect — 用戶識別與 Claims（若使用 OIDC）

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| M1 | 使用 sub + iss 組合作為用戶唯一識別碼 | 🟠 | | 不單獨依賴 email |
| M2 | 未將 email 作為唯一主鍵（email 可被更改） | 🟡 | | 高安全場景升為 🟠 |
| M3 | 只請求必要的 Scope / Claims（最小權限） | 🟡 | | |
| M4 | 敏感 Claims 使用 UserInfo Endpoint（非 id_token） | 🟡 | | 避免 id_token 過大 |
| M5 | email_verified 欄位已驗證（若依賴 email） | 🟠 | | |

---

## N. OpenID Connect — Session 與登出（若使用 OIDC SSO）

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| N1 | 實作 RP-Initiated Logout（用戶主動登出） | 🟠 | | |
| N2 | post_logout_redirect_uri 已在 OP 登記並驗證 | 🟠 | | 防 Open Redirector |
| N3 | 登出時清除本地 Session 與 Token | 🟠 | | |
| N4 | 實作 Front-Channel Logout 或 Back-Channel Logout | 🟠 | | SSO 場景必要 |
| N5 | Back-Channel Logout 端點驗證 logout_token | 🟠 | | |
| N6 | OIDC Session Management（iframe 檢查）若使用 | 🟡 | | |
| N7 | 多 RP SSO 場景，任一 RP 登出可通知其他 RP | 🟠 | | |

---

## O. OpenID Connect — Discovery 與 Provider 設定（若自建 OP）

| # | 檢查項目 | 嚴重度 | 狀態 | 備註 |
|---|---------|--------|------|------|
| O1 | 發布 OIDC Discovery Document（/.well-known/openid-configuration） | 🟡 | | |
| O2 | Discovery Document 中的端點均使用 HTTPS | 🔴 | | |
| O3 | jwks_uri 回傳正確且最新的 Public Key | 🟠 | | |
| O4 | 支援 Key Rotation（jwks_uri 動態拉取） | 🟠 | | |
| O5 | id_token_signing_alg_values_supported 不包含 none | 🔴 | | |
| O6 | userinfo_endpoint 需要 Bearer Token 才能存取 | 🟠 | | |

---

## 嚴重度說明

| 符號 | 等級 | 說明 |
|------|------|------|
| 🔴 | CRITICAL | 必須立即修復，有已知攻擊手法 |
| 🟠 | HIGH | 應在近期修復，有重大安全風險 |
| 🟡 | MEDIUM | 建議修復，最佳實踐 |
| ⚪ | N/A | 不適用於此系統 |
