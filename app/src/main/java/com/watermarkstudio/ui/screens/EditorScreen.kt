package com.watermarkstudio.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.BoxWithConstraints
import com.watermarkstudio.util.RemovalRegion
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.watermarkstudio.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import android.graphics.Bitmap
import com.watermarkstudio.model.ExportStatus
import com.watermarkstudio.model.MediaType
import com.watermarkstudio.model.MediaItem
import com.watermarkstudio.removal.preview.RemovalPreviewHelper
import com.watermarkstudio.removal.video.VideoFrameExtractor
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkFontFamily
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.ui.components.watermarkDisplayText
import com.watermarkstudio.ui.components.WatermarkOutlinedText
import com.watermarkstudio.ui.components.DraggableWatermarkOverlay
import com.watermarkstudio.ui.components.previewFontSizeSp
import com.watermarkstudio.ui.components.InteractiveWatermarkPreview
import com.watermarkstudio.viewmodel.WatermarkViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: WatermarkViewModel,
    mode: WatermarkType,
    onBack: () -> Unit,
    onNavigateToSubscription: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var selectedWatermarkIndex by remember { mutableStateOf(-1) }
    var showPremiumDialog by remember { mutableStateOf(false) }
    var premiumDialogMessage by remember { mutableStateOf("") }
    var showLayersSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    if (showPremiumDialog) {
        AlertDialog(
            onDismissRequest = { showPremiumDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFFFBBF24))
                    Text(stringResource(R.string.dialog_premium_feature), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(premiumDialogMessage, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPremiumDialog = false
                        onNavigateToSubscription()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text(stringResource(R.string.btn_view_plans))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPremiumDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    val legacyMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        viewModel.addMediaUris(context, uris)
    }

    val visualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50),
    ) { uris ->
        viewModel.addMediaUris(context, uris)
    }

    val launchMediaPicker: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            visualMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
            )
        } else {
            legacyMediaLauncher.launch("*/*")
        }
    }

    val imageWatermarkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let {
            val newIndex = uiState.watermarkConfigs.size
            viewModel.addWatermark(WatermarkConfig(WatermarkType.IMAGE, imageUri = it))
            selectedWatermarkIndex = newIndex
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long,
            )
            viewModel.dismissError()
        }
    }

    LaunchedEffect(uiState.exportStatus) {
        when (uiState.exportStatus) {
            ExportStatus.SUCCESS,
            ExportStatus.PARTIAL,
            ExportStatus.FAILED,
            -> {
                delay(8_000)
                viewModel.clearExportStatus()
            }
            else -> Unit
        }
    }

    var lastHandledExportSuccessId by remember { mutableLongStateOf(0L) }

    LaunchedEffect(uiState.exportSuccessBatchId) {
        val batchId = uiState.exportSuccessBatchId
        if (batchId > 0L && batchId != lastHandledExportSuccessId && !uiState.isProcessing) {
            lastHandledExportSuccessId = batchId
            val successMessage = context.getString(R.string.snackbar_process_success)
            if (!uiState.isPremium) {
                val activity = context as? android.app.Activity
                if (activity != null) {
                    com.watermarkstudio.util.InterstitialAdLoader.showAd(activity) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(successMessage)
                            viewModel.clearExportSuccessEvent()
                        }
                    }
                } else {
                    snackbarHostState.showSnackbar(successMessage)
                    viewModel.clearExportSuccessEvent()
                }
            } else {
                snackbarHostState.showSnackbar(successMessage)
                viewModel.clearExportSuccessEvent()
            }
        }
    }

    LaunchedEffect(mode) {
        viewModel.resetEditorSession(mode)
        selectedWatermarkIndex = 0
        viewModel.checkPremium(context)
        if (viewModel.consumePendingImageWatermarkFocus()) {
            imageWatermarkLauncher.launch("image/*")
        }
        if (viewModel.consumePendingMultilayer()) {
            viewModel.addWatermark(WatermarkConfig(WatermarkType.TEXT, text = ""))
            selectedWatermarkIndex = 1
            showLayersSheet = true
        }
    }

    val activeWatermarkIndex =
        selectedWatermarkIndex.takeIf { it in uiState.watermarkConfigs.indices }
            ?: uiState.watermarkConfigs.indices.lastOrNull()
            ?: -1

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (viewModel.canStartExport(context)) {
                viewModel.processAll(context)
            } else {
                premiumDialogMessage = context.getString(R.string.dialog_free_exports_exhausted_desc)
                showPremiumDialog = true
            }
        }
    }

    val startExport: () -> Unit = {
        if (!viewModel.canStartExport(context)) {
            premiumDialogMessage = context.getString(R.string.dialog_free_exports_exhausted_desc)
            showPremiumDialog = true
        } else if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            permissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.processAll(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090E1A), // Celestial Slate Dark
                        Color(0xFF02050A)  // Space Black
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp, 24.dp, 24.dp, 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.03f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column {
                            Text(
                                if (mode == WatermarkType.REMOVE) {
                                    stringResource(R.string.editor_title_remove)
                                } else {
                                    stringResource(R.string.editor_title_add)
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                stringResource(R.string.editor_workspace_subtitle),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.03f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                }
            },
            bottomBar = {
                Column {
                    if (!uiState.isPremium) {
                        com.watermarkstudio.util.AdMobBannerAd(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A))
                                .padding(vertical = 4.dp)
                        )
                    }
                    // Controls Panel (Translucent physical console deck)
                    Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                            ),
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                        ),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.95f),
                    shadowElevation = 16.dp
                ) {
                    val bottomScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .heightIn(max = 440.dp)
                            .verticalScroll(bottomScroll)
                            .padding(24.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (mode == WatermarkType.REMOVE) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        stringResource(R.string.remove_watermark_header),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                                Text(
                                    stringResource(R.string.editor_remove_controls_header),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF10B981),
                                    letterSpacing = 1.2.sp,
                                )
                            }
                            if (activeWatermarkIndex >= 0) {
                                val removeConfig = uiState.watermarkConfigs[activeWatermarkIndex]
                                RemovalRegionControls(
                                    config = removeConfig,
                                    onUpdate = { updated ->
                                        viewModel.updateWatermark(activeWatermarkIndex, updated)
                                    },
                                )
                            }
                            Text(
                                stringResource(R.string.editor_remove_region_hint),
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ToolButton(
                                        icon = Icons.Default.TextFields,
                                        selected = uiState.watermarkConfigs.getOrNull(activeWatermarkIndex)?.type == WatermarkType.TEXT,
                                        onClick = {
                                            val textIndex = uiState.watermarkConfigs.indexOfFirst { it.type == WatermarkType.TEXT }
                                            if (textIndex >= 0) {
                                                selectedWatermarkIndex = textIndex
                                            } else {
                                                val newIndex = uiState.watermarkConfigs.size
                                                viewModel.addWatermark(WatermarkConfig(WatermarkType.TEXT, text = ""))
                                                selectedWatermarkIndex = newIndex
                                            }
                                        },
                                    )
                                    ToolButton(
                                        icon = Icons.Default.Image,
                                        selected = uiState.watermarkConfigs.getOrNull(activeWatermarkIndex)?.type == WatermarkType.IMAGE,
                                        onClick = { imageWatermarkLauncher.launch("image/*") },
                                    )
                                    ToolButton(
                                        icon = Icons.Default.Layers,
                                        selected = showLayersSheet,
                                        onClick = { showLayersSheet = true },
                                    )
                                }
                                Text(
                                    stringResource(R.string.parameter_controls_header),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF6366F1),
                                    letterSpacing = 1.2.sp,
                                )
                            }
                        }

                        EditorSelectMediaRow(
                            selectedCount = uiState.selectedMedia.size,
                            onSelectMedia = launchMediaPicker,
                        )

                        if (
                            mode != WatermarkType.REMOVE &&
                            uiState.selectedMedia.isNotEmpty() &&
                            activeWatermarkIndex >= 0
                        ) {
                            val previewItem = uiState.selectedMedia.first()
                            val activeConfig = uiState.watermarkConfigs[activeWatermarkIndex]
                            InteractiveWatermarkPreview(
                                mediaUri = previewItem.uri,
                                mediaType = previewItem.type,
                                config = activeConfig,
                                showBackground = true,
                                isActiveLayer = true,
                                onConfigUpdate = { updated ->
                                    viewModel.updateWatermark(activeWatermarkIndex, updated)
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                            )
                            Text(
                                stringResource(R.string.editor_drag_position_hint),
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                        }

                        if (activeWatermarkIndex >= 0 && mode != WatermarkType.REMOVE) {
                            WatermarkConfigTools(
                                config = uiState.watermarkConfigs[activeWatermarkIndex],
                                onUpdate = { updated ->
                                    viewModel.updateWatermark(activeWatermarkIndex, updated)
                                },
                            )
                        }

                        val hasVideoSelected = uiState.selectedMedia.any { it.type == com.watermarkstudio.model.MediaType.VIDEO }
                        if (hasVideoSelected) {
                            VideoLimitSettings(
                                selectedLimit = uiState.maxVideoDurationSec,
                                onLimitChange = { limit ->
                                    if (!uiState.isPremium && (limit == 30 || limit == 300)) {
                                        premiumDialogMessage = "Processing long videos (30s and 5-min PRO length) is a Pro premium feature. Please upgrade to remove video duration limits and export in ultra high definition!"
                                        showPremiumDialog = true
                                    } else {
                                        viewModel.setMaxVideoDuration(limit)
                                    }
                                },
                                isPremium = uiState.isPremium
                            )
                        }

                        if (!uiState.isPremium) {
                            val remaining = (uiState.maxFreeExportsPerDay - uiState.freeExportsUsedToday).coerceAtLeast(0)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF6366F1).copy(alpha = 0.08f))
                                    .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .clickable { onNavigateToSubscription() }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "Free Exports Today: $remaining / ${uiState.maxFreeExportsPerDay}",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    "Upgrade ➔",
                                    color = Color(0xFF818CF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (uiState.isProcessing) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(
                                    progress = { uiState.processingProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF818CF8),
                                    trackColor = Color.White.copy(alpha = 0.12f),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        stringResource(R.string.processing_batch),
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                    )
                                    if (uiState.processingItemTotal > 0) {
                                        Text(
                                            stringResource(
                                                R.string.export_progress_item,
                                                uiState.processingItemIndex,
                                                uiState.processingItemTotal,
                                            ),
                                            color = Color(0xFF818CF8),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible =
                                uiState.exportStatus == ExportStatus.SUCCESS ||
                                    uiState.exportStatus == ExportStatus.PARTIAL ||
                                    uiState.exportStatus == ExportStatus.FAILED,
                            enter = fadeIn() + slideInVertically { it / 2 },
                            exit = fadeOut() + slideOutVertically { it / 2 },
                        ) {
                            ExportResultBanner(
                                status = uiState.exportStatus,
                                message = uiState.exportStatusMessage,
                                onDismiss = { viewModel.clearExportStatus() },
                            )
                        }

                        Button(
                            onClick = {
                                if (!uiState.isProcessing) {
                                    startExport()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = uiState.selectedMedia.isNotEmpty() && !uiState.isProcessing,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF6366F1),
                                    contentColor = Color.White,
                                    disabledContainerColor = if (uiState.isProcessing) {
                                        Color(0xFF4F46E5)
                                    } else {
                                        Color(0xFF1E293B)
                                    },
                                    disabledContentColor = Color.White.copy(alpha = 0.9f),
                                ),
                        ) {
                            if (uiState.isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.btn_export_processing),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${(uiState.processingProgress * 100).toInt()}%",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            } else {
                                Icon(
                                    Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.btn_process_export),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    // Media Preview Workspace Viewfinder
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.5f))
                            .border(1.2.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.selectedMedia.isEmpty()) {
                            val accentColor =
                                if (mode == WatermarkType.REMOVE) Color(0xFF10B981) else Color(0xFF6366F1)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .clickable { launchMediaPicker() }
                                    .padding(24.dp),
                            ) {
                                Icon(
                                    if (mode == WatermarkType.REMOVE) Icons.Default.AutoFixHigh else Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = accentColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(72.dp),
                                )
                                Text(
                                    stringResource(R.string.editor_tap_select_media),
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    if (mode == WatermarkType.REMOVE) {
                                        stringResource(R.string.home_open_remove_subtitle)
                                    } else {
                                        stringResource(R.string.select_media_begin)
                                    },
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            val firstItem = uiState.selectedMedia.first()
                            PreviewContainer(
                                item = firstItem,
                                configs = uiState.watermarkConfigs,
                                isPremium = uiState.isPremium,
                                activeConfigIndex = activeWatermarkIndex,
                                onActiveConfigUpdate =
                                    if (activeWatermarkIndex >= 0) {
                                        { updated ->
                                            viewModel.updateWatermark(activeWatermarkIndex, updated)
                                        }
                                    } else {
                                        null
                                    },
                            )
                            
                            // High-end HUD Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    stringResource(
                                        R.string.editor_viewfinder_badge,
                                        1,
                                        uiState.selectedMedia.size,
                                    ),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White.copy(alpha = 0.9f),
                                    letterSpacing = 1.2.sp,
                                )
                            }
                        }
                    }

                // Batch List (Mini thumbnails)
                if (uiState.selectedMedia.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(uiState.selectedMedia) { item ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                
                                if (item.type == com.watermarkstudio.model.MediaType.VIDEO) {
                                    // Play/duration icon overlay
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        val durationSec = item.duration / 1000
                                        Text(
                                            text = "${durationSec}s",
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    
                                    // Trim warning
                                    if (uiState.maxVideoDurationSec > 0 && item.duration > uiState.maxVideoDurationSec * 1000) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(2.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.error)
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Cut",
                                                color = MaterialTheme.colorScheme.onError,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.removeMedia(item) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(16.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                }
                            }
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { launchMediaPicker() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add more", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

        }
    }

        if (uiState.isProcessing) {
            ExportProcessingOverlay(
                progress = uiState.processingProgress,
                itemIndex = uiState.processingItemIndex,
                itemTotal = uiState.processingItemTotal,
            )
        }
    }

    if (showLayersSheet && mode != WatermarkType.REMOVE) {
        val layersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showLayersSheet = false },
            sheetState = layersSheetState,
            containerColor = Color(0xFF0F172A),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.watermark_layers_title),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(uiState.watermarkConfigs) { index, config ->
                        val typeLabel = when (config.type) {
                            WatermarkType.TEXT -> stringResource(R.string.layer_type_text)
                            WatermarkType.IMAGE -> stringResource(R.string.layer_type_image)
                            WatermarkType.REMOVE -> stringResource(R.string.layer_type_remove)
                        }
                        val selected = index == activeWatermarkIndex
                        Surface(
                            onClick = { selectedWatermarkIndex = index },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) Color(0xFF6366F1).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) Color(0xFF6366F1) else Color.White.copy(alpha = 0.1f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(typeLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        if (config.type == WatermarkType.TEXT) {
                                            config.watermarkDisplayText(stringResource(R.string.hint_enter_watermark))
                                        } else {
                                            stringResource(R.string.layer_preview_image)
                                        },
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                    )
                                }
                                if (uiState.watermarkConfigs.size > 1) {
                                    IconButton(
                                        onClick = {
                                            val oldSelected = selectedWatermarkIndex
                                            viewModel.removeWatermark(index)
                                            selectedWatermarkIndex = when {
                                                index < oldSelected -> oldSelected - 1
                                                else -> oldSelected.coerceAtMost(uiState.watermarkConfigs.size - 1)
                                            }
                                        },
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                                    }
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        val newIndex = uiState.watermarkConfigs.size
                        viewModel.addWatermark(WatermarkConfig(WatermarkType.TEXT, text = ""))
                        selectedWatermarkIndex = newIndex
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.layer_add_text))
                }
            }
        }
    }

    if (showSettingsSheet) {
        val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = settingsSheetState,
            containerColor = Color(0xFF0F172A),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.editor_settings_title),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                SettingsLinkRow(
                    label = stringResource(R.string.btn_privacy_policy),
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, context.getString(R.string.privacy_policy_url).toUri()),
                        )
                    },
                )
                SettingsLinkRow(
                    label = stringResource(R.string.btn_terms_service),
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, context.getString(R.string.terms_of_service_url).toUri()),
                        )
                    },
                )
                if (!uiState.isPremium) {
                    Button(
                        onClick = {
                            showSettingsSheet = false
                            onNavigateToSubscription()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.btn_view_plans))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsLinkRow(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.05f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Color.White, fontSize = 14.sp)
            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun ExportProcessingOverlay(
    progress: Float,
    itemIndex: Int,
    itemTotal: Int,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = Color(0xFF818CF8),
                strokeWidth = 6.dp,
                modifier = Modifier.size(88.dp),
            )
            Text(
                stringResource(R.string.processing_batch),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "${(progress * 100).toInt()}%",
                color = Color(0xFF818CF8),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )
            if (itemTotal > 0) {
                Text(
                    stringResource(R.string.export_progress_item, itemIndex, itemTotal),
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF6366F1),
                trackColor = Color.White.copy(alpha = 0.15f),
            )
        }
    }
}

@Composable
private fun ExportResultBanner(
    status: ExportStatus,
    message: String?,
    onDismiss: () -> Unit,
) {
    val accentColor =
        when (status) {
            ExportStatus.SUCCESS -> Color(0xFF10B981)
            ExportStatus.PARTIAL -> Color(0xFFFBBF24)
            ExportStatus.FAILED -> Color(0xFFEF4444)
            else -> Color(0xFF64748B)
        }
    val title =
        when (status) {
            ExportStatus.SUCCESS -> stringResource(R.string.export_status_success_title)
            ExportStatus.PARTIAL -> stringResource(R.string.export_status_partial_title)
            ExportStatus.FAILED -> stringResource(R.string.export_status_failed_title)
            else -> ""
        }
    val icon =
        when (status) {
            ExportStatus.SUCCESS -> Icons.Default.CheckCircle
            ExportStatus.PARTIAL -> Icons.Default.Warning
            ExportStatus.FAILED -> Icons.Default.Error
            else -> Icons.Default.Info
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    message.orEmpty(),
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.export_status_dismiss),
                    color = accentColor,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
fun EditorSelectMediaRow(
    selectedCount: Int,
    onSelectMedia: () -> Unit,
) {
    val label =
        if (selectedCount == 0) {
            stringResource(R.string.editor_select_media_add)
        } else {
            stringResource(R.string.editor_select_media_count, selectedCount)
        }
    Surface(
        onClick = onSelectMedia,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selectedCount == 0) Color(0xFF6366F1).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selectedCount == 0) Color(0xFF6366F1).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = if (selectedCount == 0) Color(0xFF818CF8) else Color.White.copy(alpha = 0.7f),
                )
                Text(
                    label,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
fun ToolButton(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun PreviewContainer(
    item: MediaItem,
    configs: List<WatermarkConfig>,
    isPremium: Boolean,
    activeConfigIndex: Int = -1,
    onActiveConfigUpdate: ((WatermarkConfig) -> Unit)? = null,
) {
    val context = LocalContext.current
    val removeConfig = configs.firstOrNull { it.type == WatermarkType.REMOVE }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewLoading by remember { mutableStateOf(false) }

    LaunchedEffect(item.uri, item.type, removeConfig?.x, removeConfig?.y, removeConfig?.scale, isPremium) {
        previewBitmap?.recycle()
        previewBitmap = null
        previewLoading = true
        previewBitmap =
            when {
                item.type == MediaType.IMAGE && removeConfig != null ->
                    RemovalPreviewHelper.renderPreview(context, item.uri, removeConfig, isPremium)
                item.type == MediaType.VIDEO && removeConfig != null ->
                    RemovalPreviewHelper.renderVideoPreview(
                        context,
                        item.uri,
                        removeConfig,
                        isPremium,
                    )
                item.type == MediaType.VIDEO ->
                    withContext(Dispatchers.IO) {
                        VideoFrameExtractor.loadPreviewFrame(context, item.uri)
                    }
                else -> null
            }
        previewLoading = false
    }
    DisposableEffect(Unit) {
        onDispose {
            previewBitmap?.recycle()
            previewBitmap = null
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else if (item.type != MediaType.VIDEO) {
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        if (previewLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color(0xFF10B981),
            )
        }
        if (onActiveConfigUpdate != null && activeConfigIndex in configs.indices) {
            configs.forEachIndexed { index, config ->
                val isActive = index == activeConfigIndex
                DraggableWatermarkOverlay(
                    mediaUri = item.uri,
                    mediaType = item.type,
                    config = config,
                    isActiveLayer = isActive,
                    onConfigUpdate = { updated ->
                        if (isActive) {
                            onActiveConfigUpdate(updated)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else if (removeConfig == null || item.type != MediaType.IMAGE) {
            configs.forEach { config ->
                WatermarkOverlay(config)
            }
        } else {
            removeConfig.let { WatermarkOverlay(it) }
        }
    }
}

@Composable
fun WatermarkOverlay(config: WatermarkConfig) {
    val horizontalBias = (config.x / 100f) * 2f - 1f
    val verticalBias = (config.y / 100f) * 2f - 1f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Box(
            modifier = Modifier
                .align(androidx.compose.ui.BiasAlignment(horizontalBias, verticalBias))
                .alpha(config.opacity)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .background(
                    if (config.type == WatermarkType.TEXT) Color.Transparent else Color.Black.copy(alpha = 0.35f),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            when (config.type) {
                WatermarkType.TEXT -> {
                    WatermarkOutlinedText(
                        text = config.watermarkDisplayText(stringResource(R.string.hint_enter_watermark)),
                        config = config,
                        fontSize = config.previewFontSizeSp(1080f),
                    )
                }
                WatermarkType.IMAGE -> {
                    AsyncImage(
                        model = config.imageUri,
                        contentDescription = null,
                        modifier = Modifier.size((100 * config.scale).dp)
                    )
                }
                WatermarkType.REMOVE -> {
                    BoxWithConstraints {
                        val boxW = maxWidth * RemovalRegion.WIDTH_RATIO * config.scale
                        val boxH = maxHeight * RemovalRegion.HEIGHT_RATIO * config.scale
                        Box(
                            modifier = Modifier
                                .size(boxW, boxH)
                                .border(2.dp, Color(0xFF10B981), RoundedCornerShape(8.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.layer_type_remove),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val watermarkTextColorPresets =
    listOf(
        0xFFFFFFFF.toInt(),
        0xFF000000.toInt(),
        0xFFFF3B30.toInt(),
        0xFFFFCC00.toInt(),
        0xFF34C759.toInt(),
        0xFF007AFF.toInt(),
    )

@Composable
fun RemovalRegionControls(config: WatermarkConfig, onUpdate: (WatermarkConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.remove_region_size_format, config.scale),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF10B981),
            )
        }
        Slider(
            value = config.scale,
            onValueChange = { onUpdate(config.copy(scale = it)) },
            valueRange = 0.3f..3f,
            colors =
                SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF10B981),
                    inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                ),
        )
        Text(
            stringResource(R.string.editor_remove_drag_scale_hint),
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
fun WatermarkConfigTools(config: WatermarkConfig, onUpdate: (WatermarkConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (config.type == WatermarkType.REMOVE) {
            return@Column
        }
        if (config.type == WatermarkType.TEXT) {
            Text(
                stringResource(R.string.text_style_lbl),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF6366F1),
                letterSpacing = 1.sp,
            )
            OutlinedTextField(
                value = config.text,
                onValueChange = { onUpdate(config.copy(text = it)) },
                placeholder = {
                    Text(
                        stringResource(R.string.hint_enter_watermark),
                        color = Color(0xFF64748B),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF131D30),
                    unfocusedContainerColor = Color(0xFF0D1424),
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White.copy(alpha = 0.9f)
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )

            Text(
                stringResource(R.string.font_family_lbl),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WatermarkFontFamily.entries.toList()) { family ->
                    val selected = config.fontFamily == family
                    val labelRes =
                        when (family) {
                            WatermarkFontFamily.SANS -> R.string.font_sans
                            WatermarkFontFamily.SERIF -> R.string.font_serif
                            WatermarkFontFamily.MONOSPACE -> R.string.font_mono
                            WatermarkFontFamily.BOLD -> R.string.font_bold
                        }
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdate(config.copy(fontFamily = family)) },
                        label = { Text(stringResource(labelRes), fontSize = 12.sp) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF6366F1),
                                selectedLabelColor = Color.White,
                            ),
                    )
                }
            }

            Text(
                stringResource(R.string.text_color_lbl),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(watermarkTextColorPresets) { preset ->
                    val selected = (config.color and 0x00FFFFFF) == (preset and 0x00FFFFFF)
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(preset))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) Color(0xFF818CF8) else Color.White.copy(alpha = 0.2f),
                                    shape = CircleShape,
                                )
                                .clickable { onUpdate(config.copy(color = preset)) },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.text_size_sp_format, config.textSizeSp.toInt()),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                )
                Slider(
                    value = config.textSizeSp,
                    onValueChange = { onUpdate(config.copy(textSizeSp = it)) },
                    valueRange = 12f..72f,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF6366F1),
                            inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                        ),
                )
            }
        }

        if (config.type == WatermarkType.IMAGE) {
            Text(
                stringResource(R.string.pos_layout_header),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF6366F1),
                letterSpacing = 1.sp,
            )
            Text(
                stringResource(
                    R.string.x_offset_pct_format,
                    config.x.toInt(),
                ) + " · " +
                    stringResource(
                        R.string.y_offset_pct_format,
                        config.y.toInt(),
                    ),
                fontSize = 12.sp,
                color = Color(0xFF64748B),
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.opacity_pct_format, (config.opacity * 100).toInt()),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                    )
                }
                Slider(
                    value = config.opacity,
                    onValueChange = { onUpdate(config.copy(opacity = it)) },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF6366F1),
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                    ),
                )
            }
        }

        if (config.type == WatermarkType.IMAGE) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.size_scale_format, config.scale),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                    )
                }
                Slider(
                    value = config.scale,
                    onValueChange = { onUpdate(config.copy(scale = it)) },
                    valueRange = 0.1f..3f,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF6366F1),
                            inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                        ),
                )
            }
        }
    }
}

@Composable
fun VideoLimitSettings(
    selectedLimit: Int,
    onLimitChange: (Int) -> Unit,
    isPremium: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Video Length Limit",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (selectedLimit == 300) "Safety Max 5 Min (PRO)" else if (selectedLimit == 0) "Original" else "${selectedLimit}s Target",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val limits = listOf(10, 15, 30, 300)
            limits.forEach { limit ->
                val isLocked = !isPremium && (limit == 30 || limit == 300)
                val label = if (limit == 300) "5 Min" else "${limit}s"
                val displayLabel = if (isLocked) "$label 🔒" else label
                val isSelected = selectedLimit == limit
                
                Button(
                    onClick = { onLimitChange(limit) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                ) {
                    Text(displayLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
