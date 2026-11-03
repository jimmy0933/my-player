# My Player · 重新建立的 Android 播放器

由於原本 `jimmy0933b3-sudo/my-player` 儲存庫已無法存取，本專案是依照先前討論的功能需求，在 **`jimmy0933/my-player` 重新建立**的原生 Android 程式，不是原始碼或 Git 歷史的完整還原。

## 功能

- **本機照片與影片**：MediaStore 掃描，不需網路權限；影片用 AndroidX Media3 ExoPlayer 1.11.1 播放
- **相簿列表**：全部、影片、照片、資料夾、收藏；檔名／資料夾搜尋，最新／最舊／名稱／大小排序
- **縮圖／清單切換**、資料夾收藏、媒體收藏；可拖曳的右側快速捲動滑條
- **全螢幕預覽**：影片自動播放；約 2 秒後自動隱藏控制列；中央點擊播放／暫停
- **手勢**：影片左右滑即時快退／快轉，上下滑切換相簿項目，雙指縮放／平移，放大後雙擊還原
- **上一部／下一部影片**：會跳過照片；直橫向畫面有可拖曳進度條、目前時間、影片總時間
- **進度條設定**：寬度 60%～100%、粗細 80%～250%，儲存於本機
- **選取操作**：複製、移動、永久刪除。**複製與移動使用 App 內直接顯示的可捲動資料夾清單**，也可輸入新子資料夾，避免系統選擇器空白
- **刪除**：App 提示後仍依 Android MediaStore 系統授權；Android 12+ 可另外申請一次媒體管理權限。移動會先複製，再要求刪除原件，取消刪除時原檔保留
- **不能快轉的 MP4**：播放器提供「修復快轉」選項，嘗試用 MediaExtractor／MediaMuxer 建立 `Movies/MyPlayer/` 新封裝副本，不修改原檔（不能修復所有檔案／編碼）

## 建置

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

Android SDK 36、JDK 17、Gradle 8.13，Kotlin + Jetpack Compose，Android 11（API 30）以上。

## GitHub Actions 自動簽章及僅最新 Release

每次推送 `main` 會執行單元測試、編譯、簽章、驗證 APK、更新 [最新 Release](https://github.com/jimmy0933/my-player/releases/latest)，並刪除舊 Release。

**請在此新儲存庫設定以下 GitHub Actions Secrets**，與舊 APK 使用同一個簽章金鑰，才有機會在手機上直接更新：

- `KEYSTORE_BASE64`：JKS／PKCS12 憑證檔的 base64
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

若沒有設定，Workflow 可產生有明確標示的 **debug-signed** APK 供先行測試，但它不能用來覆蓋不同憑證簽章的舊版 App，且不同 CI 執行的預設 debug 金鑰也不保證相同。

## 限制

本次是重建版，舊專案的某些細節、資料庫格式和相簿排序位置可能無法百分之百恢復。特定硬體解碼、部分媒體讀取權限、不同品牌的刪除確認及 MP4 修復效果，仍須安裝真機測試。
