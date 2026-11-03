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
    var browserMode by remember { mutableStateOf(prefs.getString("browser", "全部") ?: "全部") }
    var folder by remember { mutableStateOf(prefs.getString("folder", "") ?: "") }
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
    var movingDelete by remember { mutableStateOf(false) }
    var showRepairPrompt by remember { mutableStateOf<LocalMedia?>(null) }
    var progressSize by remember { mutableFloatStateOf(PlayerMath.progressScale(prefs.getFloat("progress_size", 1f))) }
    var progressWidth by remember { mutableFloatStateOf(PlayerMath.progressWidth(prefs.getFloat("progress_width", .96f))) }

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
            val isFolder = folder.isBlank() || item.relativePath.trimEnd('/') == folder
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

    fun requestDelete(items: List<LocalMedia>, isMove: Boolean = false) {
        if (items.isEmpty()) return
        movingDelete = isMove
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
            notice = if (movingDelete) "已複製到目的資料夾，但取消刪除原檔，來源檔仍保留"
                else "已取消刪除，所有原始檔案保留"
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

    val inViewer = viewerItems?.getOrNull(viewerIndex)
    BackHandler(inViewer != null) { viewerItems = null }
    if (inViewer != null) {
        val viewing = viewerItems.orEmpty()
        val videoFlags = viewing.map { it.isVideo }
        val prev = PlayerMath.videoNeighbor(videoFlags, viewerIndex, -1)
        val next = PlayerMath.videoNeighbor(videoFlags, viewerIndex, 1)
        ViewerScreen(
            media = inViewer, position = viewerIndex, total = viewing.size,
            hasPreviousVideo = prev != null, hasNextVideo = next != null,
            onBack = { viewerItems = null },
            onChange = { step -> viewerIndex = (viewerIndex + step).coerceIn(0, viewing.lastIndex) },
            onPreviousVideo = { if (prev != null) viewerIndex = prev },
            onNextVideo = { if (next != null) viewerIndex = next },
            onDelete = { requestDelete(listOf(inViewer)) },
            onRepairMp4 = { showRepairPrompt = inViewer },
            onPlaybackSettings = { showSettings = true },
            progressScale = progressSize, progressWidth = progressWidth
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
                if (it != "資料夾") folder = ""
                prefs.edit().putString("browser", it).apply()
            },
            onType = { typeFilter = it },
            onFolder = { folder = it; prefs.edit().putString("folder", it).apply() },
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
                selected = if (entry.key in selected) selected - entry.key else selected + entry.key
            },
            onToggleFavorite = { entry ->
                persistFavorites(if (entry.key in favorites) favorites - entry.key
                    else favorites + entry.key)
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
                scope.launch {
                    notice = "${operation}中：${source.size} 個檔案"
                    val result = MediaOperations.copy(context, source, target)
                    notice = "已${operation} ${result.copied.size} 個" +
                        if (result.failures.isNotEmpty()) "；失敗 ${result.failures.size} 個" else ""
                    selected = selected - result.copied.map { it.source.key }.toSet()
                    refresh()
                    if (operation == "移動" && result.copied.isNotEmpty()) {
                        // Copy first, then request Android deletion. If rejected, source remains.
                        requestDelete(result.copied.map { it.source }, isMove = true)
                    }
                }
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
            size = progressSize, width = progressWidth,
            canManage = Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(context),
            onDismiss = { showSettings = false },
            onSave = { size, width ->
                progressSize = PlayerMath.progressScale(size)
                progressWidth = PlayerMath.progressWidth(width)
                prefs.edit().putFloat("progress_size", progressSize)
                    .putFloat("progress_width", progressWidth).apply()
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
    size: Float, width: Float, canManage: Boolean,
    onDismiss: () -> Unit,
    onSave: (Float, Float) -> Unit,
    onRequestManage: () -> Unit
) {
    var scale by remember(size) { mutableFloatStateOf(size) }
    var barWidth by remember(width) { mutableFloatStateOf(width) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放器設定") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("進度條粗細：${(scale * 100).toInt()}%")
                Slider(value = scale, onValueChange = { scale = it }, valueRange = .8f..2.5f)
                Text("進度條寬度：${(barWidth * 100).toInt()}%")
                Slider(value = barWidth, onValueChange = { barWidth = it }, valueRange = .6f..1f)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(if (canManage) "媒體管理權限：已啟用"
                     else "媒體管理權限：未啟用（刪除時由 Android 再次確認）",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRequestManage) { Text("前往 Android 媒體管理設定") }
                Text("所有影片、照片及修復副本只儲存在本機。",
                    color = UiColors.Subtle, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(scale, barWidth) }) { Text("儲存") } },
        dismissButton = { TextButton(onClick = { onSave(1f, .96f) }) { Text("恢復預設") } }
    )
}
