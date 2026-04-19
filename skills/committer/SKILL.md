---
name: committer
description: "You MUST use this skill whenever the user asks to commit, push, git commit, git push, 提交, 推送, or any variation of committing or pushing code changes. Do NOT run git commit or git push commands directly — always invoke this skill first."
---

# Git Commit & Push 標準流程

當使用者要求 commit 或 push 時，依照以下步驟執行，**不得跳過任何步驟**。

---

## 步驟 1：檢查工作目錄狀態

同時執行以下指令（parallel）：
- `git status` — 確認已暫存、未暫存、未追蹤的檔案
- `git diff --cached` — 查看已暫存的變更內容
- `git diff` — 查看未暫存的變更內容
- `git log --oneline -5` — 了解此 repo 的 commit 風格

---

## 步驟 2：分析變更並決定暫存範圍

- 若有 **未暫存的變更**，詢問使用者要暫存哪些檔案，或是否全部暫存
- **禁止暫存**以下類型的檔案（除非使用者明確要求）：
  - `.env`、`*.local`、任何含有密鑰或憑證的檔案
  - 二進位大檔（除非專案本身管理靜態資源）
- 優先使用 `git add <specific-file>` 而非 `git add -A`

---

## 步驟 3：產生 Conventional Commits 格式的 commit message

根據變更內容產生符合以下規範的 commit message：

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

### Type 對照表

| Type       | 使用時機                         |
|------------|----------------------------------|
| `feat`     | 新增功能                         |
| `fix`      | 修復 bug                         |
| `refactor` | 重構（不影響功能）               |
| `style`    | 程式碼格式調整（不影響邏輯）     |
| `docs`     | 文件變更                         |
| `test`     | 新增或修改測試                   |
| `chore`    | 建置工具、依賴更新等雜項         |
| `perf`     | 效能優化                         |
| `ci`       | CI/CD 設定變更                   |
| `build`    | 影響建置系統的變更               |

### Scope 建議
根據專案結構推斷 scope，例如：`auth`、`diet`、`exercise`、`weight`、`ui`、`api`、`config`

### Subject 規則
- 使用**繁體中文**描述（若 repo 慣例為英文則用英文）
- 不超過 72 個字元
- 不加句號結尾
- 動詞開頭（新增、修正、調整、移除…）

---

## 步驟 4：確認後執行 commit

使用 HEREDOC 格式執行 commit，確保格式正確：

```bash
git commit -m "$(cat <<'EOF'
<type>(<scope>): <subject>

<body if needed>
EOF
)"
```

- 若 pre-commit hook 失敗：**修正問題後建立新 commit**，絕對不使用 `--no-verify`
- 確認 commit 成功後執行 `git status` 驗證

---

## 步驟 5：Push 至遠端（需使用者確認）

Push 前**必須明確告知**使用者即將執行的指令，並等待確認：

```
即將執行：git push origin <branch>
是否繼續？
```

確認後執行：
```bash
git push origin <branch>
```

若是首次推送新分支：
```bash
git push -u origin <branch>
```

**禁止行為：**
- 禁止 `--force` 推送至 `main`/`master`（需使用者明確授權才可對其他分支使用）
- 禁止跳過 hooks（`--no-verify`）

---

## 步驟 6：完成回報

Push 成功後簡潔回報：
- Commit hash（前 7 碼）
- Commit message
- 推送至哪個遠端分支

---

## 注意事項

- **不主動 commit**：使用者未要求時，絕對不自行執行 commit
- **不批次跳步**：每個步驟依序完成，不跳過確認環節
- 若遇到合併衝突，先協助解決衝突，不使用 `checkout .` 或 `reset --hard` 直接丟棄
- **嚴格禁止**在 commit message 中加入任何 `Co-Authored-By:` 欄位，包含但不限於 `Co-Authored-By: Claude`、`Co-Authored-By: AI AGENT` 或任何 AI 相關署名
