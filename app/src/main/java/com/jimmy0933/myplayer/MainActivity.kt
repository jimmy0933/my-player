package com.jimmy0933.myplayer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 17, 32)
        window.navigationBarColor = android.graphics.Color.rgb(11, 17, 32)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = UiColors.Lavender,
                secondary = UiColors.Mint,
                background = UiColors.Background,
                surface = UiColors.Panel,
                onBackground = Color.White,
                onSurface = Color.White
            )) { MyPlayerApp() }
        }
    }
}

private fun requiredPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasLibraryPermission(activity: Activity): Boolean =
    requiredPermissions().any {
        activity.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

@Composable
private fun MyPlayerApp() {
    val context = LocalContext.current
    val activity = context as Activity
    val prefs = remember { context.getSharedPreferences("my_player_preferences", Activity.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var gallery by remember { mutableStateOf(emptyList<LocalMedia>()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableIntStateOf(0) }
    var permissionAsked by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("本機離線播放 · 影片與照片不會上傳") }
    var typeFilter by remember { mutableStateOf("全部") }
    // Always start at the indexed-media storage root, not the last opened
    // subfolder. Users can still change to All/Videos/Photos/Favorites tabs.
    var browserMode by remember { mutableStateOf("資料夾") }
    var folder by remember { mutableStateOf("") }
    var search by remember { mutableStateOf(prefs.getString("search", "") ?: "") }
    var sort by remember { mutableStateOf(prefs.getString("sort", "最新") ?: "最新") }
    var listMode by remember { mutableStateOf(prefs.getBoolean("list", false)) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())!!.toSet()) }
    var favoriteFolders by remember { mutableStateOf(prefs.getStringSet("favorite_folders", emptySet())!!.toSet()) }
    var viewerItems by remember { mutableStateOf<List<LocalMedia>?>(null) }
    var viewerIndex by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf("複製") }
    var operationItems by remember { mutableStateOf(emptyList<LocalMedia>()) }
    var deleteConfirmation by remember { mutableStateOf<List<LocalMedia>?>(null) }
    var deleteQueue by remember { mutableStateOf(emptyList<LocalMedia>()) }
    var activeDelete by remember { mutableStateOf(emptyList<LocalMedia>()) }
    var moveQueue by remember { mutableStateOf(emptyList<LocalMedia>()) }
    var activeMove by remember { mutableStateOf(emptyList<LocalMedia>()) }
    var moveTarget by remember { mutableStateOf("") }
    var movedCount by remember { mutableIntStateOf(0) }
    var moveErrors by remember { mutableStateOf(emptyList<String>()) }
    var operationErrors by remember { mutableStateOf(emptyList<String>()) }
    var showRepairPrompt by remember { mutableStateOf<LocalMedia?>(null) }
    var progressSize by remember { mutableFloatStateOf(PlayerMath.progressScale(prefs.getFloat("progress_size", 1f))) }
    var progressOpacity by remember { mutableFloatStateOf(PlayerMath.progressOpacity(prefs.getFloat("progress_opacity", .72f))) }

    fun refresh() { refreshing++ }
    fun persistFavorites(value: Set<String>) {
        favorites = value
        prefs.edit().putStringSet("favorites", value).apply()
    }
    fun persistFavoriteFolders(value: Set<String>) {
        favoriteFolders = value
        prefs.edit().putStringSet("favorite_folders", value).apply()
    }
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {
        permissionAsked = true
        refresh()
    }
    LaunchedEffect(Unit) {
        if (!hasLibraryPermission(activity) && !permissionAsked) {
            permissionAsked = true
            permissionsLauncher.launch(requiredPermissions())
        }
    }
    LaunchedEffect(refreshing) {
        loading = true
        gallery = MediaLibrary.load(context)
        loading = false
    }
    LaunchedEffect(viewerItems != null) {
        activity.requestedOrientation =
            if (viewerItems != null) ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    DisposableEffect(Unit) {
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val visible = remember(gallery, typeFilter, browserMode, folder, search, sort, favorites) {
        gallery.filter { item ->
            val isType = when (typeFilter) {
                "影片" -> item.isVideo
                "照片" -> !item.isVideo
                else -> true
            }
            val isFolder = browserMode != "資料夾" ||
                FolderNavigation.isDirect(item.relativePath, folder)
            val isFavorite = browserMode != "收藏" || item.key in favorites
            val isSearch = search.isBlank() ||
                item.name.contains(search, ignoreCase = true) ||
                item.relativePath.contains(search, ignoreCase = true)
            isType && isFolder && isFavorite && isSearch
        }.let { entries ->
            when (sort) {
                "名稱" -> entries.sortedBy { it.name.lowercase() }
                "大小" -> entries.sortedByDescending { it.size }
                "最舊" -> entries.sortedBy { it.dateAdded }
                else -> entries.sortedByDescending { it.dateAdded }
            }
        }
    }

    fun requestDelete(items: List<LocalMedia>) {
        if (items.isEmpty()) return
        deleteConfirmation = items.distinctBy { it.key }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val justRequested = activeDelete
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                delay(350)
                val current = MediaLibrary.load(context)
                val surviving = current.map { it.key }.toSet()
                val confirmedDeleted = justRequested.filter { it.key !in surviving }
                gallery = current
                selected = selected - confirmedDeleted.map { it.key }.toSet()
                persistFavorites(favorites - confirmedDeleted.map { it.key }.toSet())
                viewerItems = viewerItems?.filter { it.key !in confirmedDeleted.map { x -> x.key }.toSet() }
                    ?.takeIf { it.isNotEmpty() }
                viewerIndex = viewerIndex.coerceIn(0, (viewerItems?.size ?: 1) - 1)
                val failed = justRequested.size - confirmedDeleted.size
                notice = "確認刪除 ${confirmedDeleted.size} 個${if (failed > 0) "，另 $failed 個未能確認刪除" else ""}"
                if (failed > 0) deleteQueue = emptyList()
                activeDelete = emptyList()
            }
        } else {
            deleteQueue = emptyList()
            activeDelete = emptyList()
            notice = "已取消刪除，所有原始檔案保留"
        }
    }
    // Use MediaStore's native authorization, including when MANAGE_MEDIA was granted.
    // Android's approval can cover up to 100 items per request.
    LaunchedEffect(deleteQueue, activeDelete) {
        if (deleteQueue.isNotEmpty() && activeDelete.isEmpty()) {
            val batch = deleteQueue.take(100)
            activeDelete = batch
            deleteQueue = deleteQueue.drop(batch.size)
            try {
                val request = MediaStore.createDeleteRequest(context.contentResolver,
                    batch.map { it.uri })
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } catch (e: Exception) {
                notice = "無法要求刪除權限：${e.message ?: "請檢查系統設定"}"
                deleteQueue = emptyList()
                activeDelete = emptyList()
            }
        }
    }

    // Request temporary write access to the original URIs. Native relocation
    // avoids allocating a second copy of a large video before deleting it.
    val moveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { response ->
        val batch = activeMove
        if (response.resultCode == Activity.RESULT_OK) {
            scope.launch {
                val outcome = MediaOperations.move(context, batch, moveTarget)
                movedCount += outcome.moved.size
                moveErrors = moveErrors + outcome.failures
                selected = selected - outcome.moved.map { it.key }.toSet()
                gallery = MediaLibrary.load(context)
                activeMove = emptyList()
                if (moveQueue.isEmpty()) {
                    notice = "移動完成：成功 $movedCount 個，失敗 ${moveErrors.size} 個"
                    if (moveErrors.isNotEmpty()) operationErrors = moveErrors
                }
            }
        } else {
            val skipped = batch.size + moveQueue.size
            moveQueue = emptyList()
            activeMove = emptyList()
            notice = "已取消移動（尚未處理 $skipped 個），原始檔案保留"
        }
    }
    LaunchedEffect(moveQueue, activeMove) {
        if (moveQueue.isNotEmpty() && activeMove.isEmpty()) {
            val batch = moveQueue.take(100)
            activeMove = batch
            moveQueue = moveQueue.drop(batch.size)
            try {
                val request = MediaStore.createWriteRequest(
                    context.contentResolver, batch.map { it.uri })
                moveLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } catch (e: Exception) {
                moveErrors = moveErrors + "Android 寫入授權失敗：${e.message ?: e.javaClass.simpleName}"
                operationErrors = moveErrors
                notice = "移動失敗，請檢查媒體管理權限"
                activeMove = emptyList()
                moveQueue = emptyList()
            }
        }
    }

    val inViewer = viewerItems?.getOrNull(viewerIndex)
    BackHandler(inViewer != null) { viewerItems = null }
    BackHandler(inViewer == null && browserMode == "資料夾" && folder.isNotBlank()) {
        folder = FolderNavigation.parent(folder)
        selected = emptySet()
    }
    if (inViewer != null) {
        val viewing = viewerItems.orEmpty()
        ViewerScreen(
            media = inViewer,
            onBack = { viewerItems = null },
            onChange = { step ->
                val next = (viewerIndex + step).coerceIn(0, viewing.lastIndex)
                if (next != viewerIndex) {
                    viewerIndex = next
                    true
                } else false
            },
            onDelete = { requestDelete(listOf(inViewer)) },
            onRepairMp4 = { showRepairPrompt = inViewer },
            onPlaybackSettings = { showSettings = true },
            progressScale = progressSize, progressOpacity = progressOpacity
        )
    } else {
        GalleryScreen(
            allMedia = gallery, visibleMedia = visible,
            loading = loading, hasPermission = hasLibraryPermission(activity),
            notice = notice, browserMode = browserMode, typeFilter = typeFilter,
            folder = folder, search = search, sort = sort, listMode = listMode,
            selected = selected, favorites = favorites,
            favoriteFolders = favoriteFolders,
            onRequestPermission = { permissionsLauncher.launch(requiredPermissions()) },
            onRefresh = { refresh() },
            onMode = {
                browserMode = it
                // Entering folder browsing always starts from its root.
                folder = ""
            },
            onType = { typeFilter = it },
            onFolder = {
                folder = FolderNavigation.normalize(it)
                selected = emptySet()
            },
            onFavoriteFolder = { key ->
                persistFavoriteFolders(if (key in favoriteFolders) favoriteFolders - key
                    else favoriteFolders + key)
            },
            onSearch = { search = it; prefs.edit().putString("search", it).apply() },
            onSort = { sort = it; prefs.edit().putString("sort", it).apply() },
            onListMode = { listMode = !listMode; prefs.edit().putBoolean("list", listMode).apply() },
            onOpen = { entry ->
                viewerItems = visible
                viewerIndex = visible.indexOfFirst { it.key == entry.key }.coerceAtLeast(0)
            },
            onSelect = { entry ->
                selected = GallerySelection.toggle(selected, entry.key)
            },
            onFavoriteSelection = {
                if (selected.isNotEmpty()) {
                    val allSelectedFavorited = selected.all { it in favorites }
                    persistFavorites(if (allSelectedFavorited) favorites - selected
                        else favorites + selected)
                }
            },
            onClearSelection = { selected = emptySet() },
            onFileAction = { kind ->
                operation = kind
                operationItems = gallery.filter { it.key in selected }
                if (kind == "刪除") requestDelete(operationItems)
                else showFolderPicker = true
            },
            onOpenSettings = { showSettings = true }
        )
    }

    if (showFolderPicker) {
        FolderDestinationDialog(
            folders = MediaLibrary.folders(gallery),
            operation = operation,
            onDismiss = { showFolderPicker = false },
            onApply = { target ->
                showFolderPicker = false
                val source = operationItems
                if (operation == "移動") {
                    moveTarget = target
                    movedCount = 0
                    moveErrors = emptyList()
                    activeMove = emptyList()
                    moveQueue = source.distinctBy { it.key }
                    notice = "準備移動 ${source.size} 個檔案"
                } else {
                    scope.launch {
                        notice = "複製中：${source.size} 個檔案"
                        val result = MediaOperations.copy(context, source, target)
                        notice = "複製完成：成功 ${result.copied.size} 個，失敗 ${result.failures.size} 個"
                        if (result.failures.isNotEmpty()) operationErrors = result.failures
                        selected = selected - result.copied.map { it.source.key }.toSet()
                        refresh()
                    }
                }
            }
        )
    }

    if (operationErrors.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { operationErrors = emptyList() },
            title = { Text("檔案操作失敗原因") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("成功移動的檔案不會重新複製；失敗的原始檔案仍留在原處。")
                    Spacer(Modifier.height(8.dp))
                    Text(operationErrors.take(8).joinToString("\n\n"),
                        style = MaterialTheme.typography.bodySmall)
                    if (operationErrors.size > 8)
                        Text("另有 ${operationErrors.size - 8} 個錯誤")
                }
            },
            confirmButton = {
                TextButton(onClick = { operationErrors = emptyList() }) { Text("關閉") }
            }
        )
    }

    deleteConfirmation?.let { files ->
        AlertDialog(
            onDismissRequest = { deleteConfirmation = null },
            title = { Text("確認永久刪除？") },
            text = { Text("即將要求 Android 刪除 ${files.size} 個原始檔案。刪除無法從 My Player 復原。\n\nAndroid 可能依授權狀態另外顯示確認視窗。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteQueue = files
                    deleteConfirmation = null
                }) { Text("確認刪除", color = Color(0xFFFF9DA6)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmation = null }) { Text("保留檔案") }
            }
        )
    }

    showRepairPrompt?.let { item ->
        AlertDialog(onDismissRequest = { showRepairPrompt = null },
            title = { Text("修復無法快轉的 MP4") },
            text = { Text("在 Movies/MyPlayer 建立重新封裝的影片副本，保留原始檔。適用於部分索引損壞的 MP4，不進行重新編碼。") },
            confirmButton = {
                TextButton(onClick = {
                    showRepairPrompt = null
                    scope.launch {
                        notice = "正在重新封裝：${item.name}"
                        try {
                            MediaOperations.repairMp4(context, item)
                            notice = "修復副本已存到 Movies/MyPlayer"
                            refresh()
                        } catch (e: Exception) {
                            notice = "重新封裝失敗：${e.message ?: "格式不支援"}"
                        }
                    }
                }) { Text("建立副本") }
            },
            dismissButton = {
                TextButton(onClick = { showRepairPrompt = null }) { Text("取消") }
            }
        )
    }

    if (showSettings) {
        PlaybackSettingsDialog(
            size = progressSize, opacity = progressOpacity,
            canManage = Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(context),
            onDismiss = { showSettings = false },
            onSave = { size, opacity ->
                progressSize = PlayerMath.progressScale(size)
                progressOpacity = PlayerMath.progressOpacity(opacity)
                prefs.edit().putFloat("progress_size", progressSize)
                    .putFloat("progress_opacity", progressOpacity)
                    .remove("progress_width").apply()
                showSettings = false
            },
            onRequestManage = {
                if (Build.VERSION.SDK_INT >= 31) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA)) }
                        .onFailure { notice = "此裝置無法開啟媒體管理設定" }
                }
            }
        )
    }
}

@Composable
private fun PlaybackSettingsDialog(
    size: Float, opacity: Float, canManage: Boolean,
    onDismiss: () -> Unit,
    onSave: (Float, Float) -> Unit,
    onRequestManage: () -> Unit
) {
    var scale by remember(size) { mutableFloatStateOf(size) }
    // The control now moves in the intuitive direction: further right means
    // more transparent. Persisted values remain opacity for compatibility.
    var transparency by remember(opacity) { mutableFloatStateOf(1f - opacity) }
    var previewProgress by remember { mutableFloatStateOf(.4f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放器設定") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("進度條粗度：${PlayerMath.progressThicknessDp(scale).toInt()} dp")
                Slider(value = scale, onValueChange = { scale = it }, valueRange = .8f..2.5f)
                Text("進度條透明度：${(transparency * 100f).toInt()}%")
                Slider(
                    value = transparency,
                    onValueChange = { transparency = it },
                    valueRange = 0f.. .85f
                )
                Text(
                    "實際進度線預覽（可拖動）",
                    color = UiColors.Subtle, style = MaterialTheme.typography.bodySmall
                )
                Box(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)
                        .background(Color.Black, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .height(66.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Timeline(
                        previewProgress, { previewProgress = it },
                        scale, 1f - transparency
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(if (canManage) "媒體管理權限：已啟用"
                     else "媒體管理權限：未啟用（刪除時由 Android 再次確認）",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRequestManage) { Text("前往 Android 媒體管理設定") }
                Text("所有影片、照片及修復副本只儲存在本機。",
                    color = UiColors.Subtle, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(scale, 1f - transparency) }) { Text("儲存") } },
        dismissButton = { TextButton(onClick = { onSave(1f, .72f) }) { Text("恢復預設") } }
    )
}
