package com.jimmy0933.myplayer

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

object UiColors {
    val Background = Color(0xFF0B1120)
    val Panel = Color(0xFF172239)
    val PanelLight = Color(0xFF22314D)
    val Lavender = Color(0xFF91A9FF)
    val Mint = Color(0xFF86E6CB)
    val Subtle = Color(0xFFAFBDD3)
}

@Composable
fun GalleryScreen(
    allMedia: List<LocalMedia>,
    visibleMedia: List<LocalMedia>,
    loading: Boolean,
    hasPermission: Boolean,
    notice: String,
    browserMode: String,
    typeFilter: String,
    folder: String,
    search: String,
    sort: String,
    listMode: Boolean,
    selected: Set<String>,
    favorites: Set<String>,
    favoriteFolders: Set<String>,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onMode: (String) -> Unit,
    onType: (String) -> Unit,
    onFolder: (String) -> Unit,
    onFavoriteFolder: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSort: (String) -> Unit,
    onListMode: () -> Unit,
    onOpen: (LocalMedia) -> Unit,
    onSelect: (LocalMedia) -> Unit,
    onFavoriteSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onFileAction: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(search.isNotBlank()) }
    val context = LocalContext.current
    // One shared decoder/cache for the gallery instead of one ImageLoader per tile.
    val galleryLoader = remember(context) {
        ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
    }
    val snackbar = remember { SnackbarHostState() }
    val selectionMode = GallerySelection.active(selected)
    val allFavorited = selectionMode && selected.all { it in favorites }
    val folders = remember(allMedia) {
        allMedia.groupBy { it.relativePath.trimEnd('/').ifBlank { "其他" } }
    }
    val activeTab = when (browserMode) {
        "資料夾" -> "資料夾"
        "收藏" -> "收藏"
        else -> typeFilter
    }
    val gridState = rememberLazyGridState()
    LaunchedEffect(browserMode, typeFilter, folder, sort, listMode) {
        gridState.scrollToItem(0)
    }
    LaunchedEffect(notice) {
        if (notice.isNotBlank() && !notice.startsWith("本機離線播放")) {
            snackbar.showSnackbar(notice, duration = SnackbarDuration.Short)
        }
    }

    Column(Modifier.fillMaxSize().background(UiColors.Background).statusBarsPadding()) {
        // Only one compact title/action row. Bulk actions live in the top-right overflow.
        Row(
            Modifier.fillMaxWidth().height(52.dp)
                .padding(start = 13.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(30.dp).background(
                    Brush.linearGradient(listOf(UiColors.Lavender, UiColors.Mint)),
                    RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(22.dp),
                    tint = UiColors.Background)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                if (selectionMode) "已選 ${selected.size} 項" else "My Player",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                color = if (selectionMode) UiColors.Mint else Color.White
            )
            if (selectionMode) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, "退出多選", tint = UiColors.Subtle)
                }
            } else {
                IconButton(onClick = { searchOpen = !searchOpen }) {
                    Icon(Icons.Default.Search, "搜尋影片、照片與資料夾", tint = Color.White)
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert,
                        if (selectionMode) "選取操作與更多設定" else "更多功能",
                        tint = Color.White)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    if (selectionMode) {
                        DropdownMenuItem(
                            text = { Text("複製選取（${selected.size}）") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            onClick = { menuOpen = false; onFileAction("複製") }
                        )
                        DropdownMenuItem(
                            text = { Text("移動選取（${selected.size}）") },
                            leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                            onClick = { menuOpen = false; onFileAction("移動") }
                        )
                        DropdownMenuItem(
                            text = { Text(if (allFavorited) "取消選取的收藏" else "收藏選取項目") },
                            leadingIcon = { Icon(Icons.Default.FavoriteBorder, null) },
                            onClick = { menuOpen = false; onFavoriteSelection() }
                        )
                        DropdownMenuItem(
                            text = { Text("刪除選取（${selected.size}）", color = Color(0xFFFFA4A9)) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFFFA4A9)) },
                            onClick = { menuOpen = false; onFileAction("刪除") }
                        )
                        DropdownMenuItem(
                            text = { Text("取消選取") },
                            onClick = { menuOpen = false; onClearSelection() }
                        )
                        HorizontalDivider()
                    }
                    DropdownMenuItem(
                        text = { Text(if (listMode) "改為縮圖模式" else "改為清單模式") },
                        leadingIcon = { Icon(Icons.Default.ViewModule, null) },
                        onClick = { menuOpen = false; onListMode() }
                    )
                    for (choice in listOf("最新", "最舊", "名稱", "大小")) {
                        DropdownMenuItem(
                            text = { Text("排序：$choice${if (sort == choice) " ✓" else ""}") },
                            onClick = { menuOpen = false; onSort(choice) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("重新掃描") },
                        leadingIcon = { Icon(Icons.Default.Refresh, null) },
                        onClick = { menuOpen = false; onRefresh() }
                    )
                    DropdownMenuItem(
                        text = { Text("播放器與權限設定") },
                        leadingIcon = { Icon(Icons.Default.Settings, null) },
                        onClick = { menuOpen = false; onOpenSettings() }
                    )
                }
            }
        }

        if (searchOpen && !selectionMode) {
            OutlinedTextField(
                value = search, onValueChange = onSearch,
                placeholder = { Text("搜尋檔名或資料夾") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                trailingIcon = {
                    IconButton(onClick = { onSearch(""); searchOpen = false }) {
                        Icon(Icons.Default.Close, "關閉搜尋")
                    }
                }
            )
        }

        // One slim scrollable navigation row replaces the old two chip rows and statistics.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (choice in listOf("全部", "影片", "照片", "資料夾", "收藏")) {
                FilterChip(
                    selected = activeTab == choice,
                    onClick = {
                        when (choice) {
                            "資料夾", "收藏" -> {
                                onType("全部")
                                onMode(choice)
                                onFolder("")
                            }
                            else -> {
                                onMode("全部")
                                onType(choice)
                                onFolder("")
                            }
                        }
                        onClearSelection()
                    },
                    label = { Text(choice, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = UiColors.Panel,
                        labelColor = UiColors.Subtle,
                        selectedContainerColor = UiColors.Lavender,
                        selectedLabelColor = UiColors.Background
                    )
                )
            }
        }

        if (folder.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().height(34.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onFolder(""); onClearSelection() }) {
                    Text("‹ 資料夾", style = MaterialTheme.typography.labelMedium)
                }
                Text(folder.substringAfterLast('/'), color = UiColors.Subtle,
                    style = MaterialTheme.typography.labelMedium,
                    overflow = TextOverflow.Ellipsis, maxLines = 1)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = UiColors.Mint)
                }
                !hasPermission && galleryIsEmpty(allMedia) ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PermMedia, null, tint = UiColors.Lavender,
                                modifier = Modifier.size(50.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("需要照片與影片讀取權限", color = UiColors.Subtle)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onRequestPermission) { Text("授權讀取") }
                        }
                    }
                browserMode == "資料夾" && folder.isBlank() -> {
                    val rows = folders.entries.sortedWith(
                        compareByDescending<Map.Entry<String, List<LocalMedia>>> {
                            it.key in favoriteFolders
                        }.thenBy { it.key }
                    )
                    if (rows.isEmpty()) {
                        EmptyGallery(Modifier.fillMaxSize(), "沒有找到資料夾")
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(rows, key = { it.key }) { entry ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(UiColors.Panel)
                                        .clickable { onFolder(entry.key) }
                                        .padding(start = 13.dp, end = 5.dp, top = 9.dp, bottom = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, null, tint = UiColors.Mint,
                                        modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.key.substringAfterLast('/'),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${entry.value.size} 個",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = UiColors.Subtle)
                                    }
                                    IconButton(onClick = { onFavoriteFolder(entry.key) }) {
                                        Icon(
                                            if (entry.key in favoriteFolders) Icons.Default.Star
                                            else Icons.Default.StarBorder,
                                            "收藏資料夾", tint = if (entry.key in favoriteFolders)
                                                UiColors.Mint else UiColors.Subtle)
                                    }
                                }
                            }
                        }
                    }
                }
                visibleMedia.isEmpty() ->
                    EmptyGallery(Modifier.fillMaxSize(), "目前沒有符合條件的照片或影片")
                else -> {
                    Box(Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (listMode) 1 else 3),
                            state = gridState,
                            modifier = Modifier.fillMaxSize().padding(end = 18.dp),
                            contentPadding = PaddingValues(
                                start = 7.dp, end = 2.dp, top = 4.dp, bottom = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(visibleMedia, key = { it.key }) { entry ->
                                MediaTile(
                                    entry = entry, listMode = listMode,
                                    imageLoader = galleryLoader,
                                    selectionMode = selectionMode,
                                    checked = entry.key in selected,
                                    favorite = entry.key in favorites,
                                    onClick = {
                                        if (selectionMode) onSelect(entry) else onOpen(entry)
                                    },
                                    onLongPress = { onSelect(entry) },
                                    onSelect = { onSelect(entry) }
                                )
                            }
                        }
                        if (visibleMedia.size > if (listMode) 8 else 21) {
                            GridFastScroll(
                                gridState, visibleMedia.size,
                                modifier = Modifier.align(Alignment.CenterEnd)
                                    .fillMaxHeight().width(20.dp)
                            )
                        }
                    }
                }
            }
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        }
        // Deliberately NO bottom bulk-action strip. All actions live in the top-right menu.
    }
}

private fun galleryIsEmpty(items: List<LocalMedia>) = items.isEmpty()

@Composable
private fun EmptyGallery(modifier: Modifier, text: String) {
    Box(modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().background(UiColors.Panel, RoundedCornerShape(20.dp))
            .padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Collections, null, tint = UiColors.Subtle,
                modifier = Modifier.size(60.dp))
            Spacer(Modifier.height(14.dp))
            Text(text, color = UiColors.Subtle)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    entry: LocalMedia,
    listMode: Boolean,
    imageLoader: ImageLoader,
    selectionMode: Boolean,
    checked: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onSelect: () -> Unit
) {
    val context = LocalContext.current
    val picture: @Composable () -> Unit = {
        Box(
            Modifier.then(if (listMode) Modifier.size(96.dp) else Modifier.fillMaxSize())
                .clip(RoundedCornerShape(if (listMode) 10.dp else 8.dp))
                .background(UiColors.PanelLight)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(entry.uri).crossfade(false).build(),
                imageLoader = imageLoader,
                contentDescription = entry.name, modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            if (checked) {
                Box(Modifier.fillMaxSize().background(UiColors.Lavender.copy(alpha = .22f)))
            }
            if (selectionMode) {
                IconButton(
                    onClick = onSelect,
                    modifier = Modifier.align(Alignment.TopStart).size(34.dp)
                ) {
                    Icon(
                        if (checked) Icons.Default.CheckCircle
                        else Icons.Default.RadioButtonUnchecked,
                        if (checked) "取消選取" else "選取媒體",
                        tint = if (checked) UiColors.Mint else Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }
            if (favorite) {
                Icon(
                    Icons.Default.Favorite, "已收藏",
                    tint = Color(0xFFFF9DBA),
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(5.dp).size(15.dp)
                )
            }
            if (entry.isVideo) {
                Text(
                    PlayerMath.durationLabel(entry.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = .78f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
    if (listMode) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                .background(UiColors.Panel)
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .padding(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            picture()
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(entry.name, maxLines = 2, color = Color.White,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(entry.folder, color = UiColors.Subtle, maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    } else {
        Box(
            Modifier.fillMaxWidth().aspectRatio(.95f)
                .clip(RoundedCornerShape(9.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
        ) {
            picture()
        }
    }
}

@Composable
private fun GridFastScroll(
    state: androidx.compose.foundation.lazy.grid.LazyGridState, count: Int, modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val height = maxHeight
        val trackPx = with(androidx.compose.ui.platform.LocalDensity.current) { height.toPx() }
        val thumbHeight = 54.dp
        val thumbHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { thumbHeight.toPx() }
        val fraction by remember(state, count) {
            derivedStateOf {
                val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                (state.firstVisibleItemIndex.toFloat() / (count - visible).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
        var pointer by remember { mutableFloatStateOf(0f) }
        Box(Modifier.fillMaxHeight().width(22.dp)
            .pointerInput(count, trackPx) {
                detectDragGestures(
                    onDragStart = { pos ->
                        pointer = pos.y
                        val target = ((pointer / trackPx) * (count - 1)).roundToInt()
                            .coerceIn(0, count - 1)
                        scope.launch { state.scrollToItem(target) }
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        pointer = (pointer + delta.y).coerceIn(0f, trackPx)
                        val target = ((pointer / trackPx) * (count - 1)).roundToInt()
                            .coerceIn(0, count - 1)
                        scope.launch { state.scrollToItem(target) }
                    }
                )
            }, contentAlignment = Alignment.TopCenter) {
            Box(Modifier.fillMaxHeight().width(3.dp).background(
                UiColors.Subtle.copy(alpha = .24f), CircleShape))
            Box(Modifier.offset(y = (height - thumbHeight) * fraction)
                .width(9.dp).height(thumbHeight).background(UiColors.Mint, CircleShape))
        }
    }
}

@Composable
fun FolderDestinationDialog(
    folders: List<String>, operation: String,
    onDismiss: () -> Unit, onApply: (String) -> Unit
) {
    val defaults = listOf("DCIM/Camera", "Movies", "Movies/MyPlayer", "Pictures",
        "Pictures/MyPlayer", "Download")
    val choices = (defaults + folders).distinct().sorted()
    var destination by remember(operation) {
        mutableStateOf(if (operation == "移動") "Movies/MyPlayer" else "Pictures/MyPlayer")
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("選擇${operation}的目的資料夾") },
        text = {
            Column {
                Text("可選現有資料夾，或直接輸入新資料夾路徑。",
                    color = UiColors.Subtle, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = destination, onValueChange = { destination = it },
                    label = { Text("目的資料夾") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(choices) { path ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (destination == path) UiColors.PanelLight else Color.Transparent)
                            .clickable { destination = path }.padding(9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = UiColors.Mint)
                            Spacer(Modifier.width(8.dp))
                            Text(path, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = destination.isNotBlank(),
                onClick = { onApply(destination) }) { Text("開始$operation") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

fun readableSize(size: Long): String = when {
    size >= 1_073_741_824L -> "%.1f GB".format(size / 1_073_741_824.0)
    size >= 1_048_576L -> "%.1f MB".format(size / 1_048_576.0)
    size >= 1024 -> "%.1f KB".format(size / 1024.0)
    else -> "$size B"
}
