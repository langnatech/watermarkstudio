package com.watermarkstudio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.watermarkstudio.model.MediaType
import com.watermarkstudio.model.MediaItem
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.viewmodel.WatermarkViewModel
import kotlinx.coroutines.launch
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

    if (showPremiumDialog) {
        AlertDialog(
            onDismissRequest = { showPremiumDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFFFBBF24))
                    Text("Premium Feature", fontWeight = FontWeight.Bold)
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
                    Text("View Plans")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPremiumDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    val mediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        viewModel.addMediaUris(context, uris)
    }

    val imageWatermarkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.addWatermark(WatermarkConfig(WatermarkType.IMAGE, imageUri = it))
            selectedWatermarkIndex = uiState.watermarkConfigs.lastIndex // Select the newly added one
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(uiState.processedMediaUris) {
        if (!uiState.isProcessing && uiState.processedMediaUris.isNotEmpty()) {
            if (!uiState.isPremium) {
                val activity = context as? android.app.Activity
                if (activity != null) {
                    com.watermarkstudio.util.InterstitialAdLoader.showAd(activity) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("All items processed and saved to gallery!")
                        }
                    }
                } else {
                    snackbarHostState.showSnackbar("All items processed and saved to gallery!")
                }
            } else {
                snackbarHostState.showSnackbar("All items processed and saved to gallery!")
            }
        }
    }

    LaunchedEffect(mode) {
        viewModel.clearWatermarks()
        if (mode == WatermarkType.REMOVE) {
            viewModel.addWatermark(WatermarkConfig(WatermarkType.REMOVE))
        } else {
            viewModel.addWatermark(WatermarkConfig(WatermarkType.TEXT, text = "Watermark"))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (!uiState.isPremium && uiState.freeExportsUsedToday >= uiState.maxFreeExportsPerDay) {
                premiumDialogMessage = "You have exhausted your 3 free daily exports. Upgrade to Pro for unlimited exports, removed duration limits, watermark-free results, and zero ads!"
                showPremiumDialog = true
            } else {
                if (viewModel.consumeFreeExport(context)) {
                    viewModel.processAll(context)
                }
            }
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
                                if (mode == WatermarkType.REMOVE) "Remove Watermark" else "Add Watermark",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Studio Console Workspace",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = { /* Settings */ },
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
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Type Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ToolButton(
                                    icon = Icons.Default.TextFields,
                                    selected = uiState.watermarkConfigs.getOrNull(selectedWatermarkIndex)?.type == WatermarkType.TEXT,
                                    onClick = { viewModel.addWatermark(WatermarkConfig(WatermarkType.TEXT, text = "Watermark")) }
                                )
                                ToolButton(
                                    icon = Icons.Default.Image,
                                    selected = uiState.watermarkConfigs.getOrNull(selectedWatermarkIndex)?.type == WatermarkType.IMAGE,
                                    onClick = { imageWatermarkLauncher.launch("image/*") }
                                )
                                ToolButton(
                                    icon = Icons.Default.Layers,
                                    selected = false,
                                    onClick = { /* Layers list toggle */ }
                                )
                            }
                            Text(
                                "PARAMETER CONTROLS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF6366F1),
                                letterSpacing = 1.2.sp
                            )
                        }

                        // Watermark Config
                        if (uiState.watermarkConfigs.isNotEmpty()) {
                            WatermarkConfigTools(
                                config = uiState.watermarkConfigs.getOrNull(selectedWatermarkIndex) ?: uiState.watermarkConfigs.last(),
                                onUpdate = { updated -> 
                                    val idx = if (selectedWatermarkIndex == -1) uiState.watermarkConfigs.size - 1 else selectedWatermarkIndex
                                    viewModel.updateWatermark(idx, updated)
                                }
                            )
                        } else if (uiState.selectedMedia.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                    .clickable { mediaLauncher.launch("*/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Select Media to Begin", color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            }
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

                        // Apply Button (Electric premium gradient sheen)
                        Button(
                            onClick = { 
                                if (!uiState.isPremium && uiState.freeExportsUsedToday >= uiState.maxFreeExportsPerDay) {
                                    premiumDialogMessage = "You have exhausted your 3 free daily exports. Upgrade to Pro for unlimited exports, removed duration limits, watermark-free results, and zero ads!"
                                    showPremiumDialog = true
                                } else {
                                    if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
                                        permissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    } else {
                                        if (viewModel.consumeFreeExport(context)) {
                                            viewModel.processAll(context)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (uiState.selectedMedia.isNotEmpty() && !uiState.isProcessing) {
                                        Brush.linearGradient(colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5)))
                                    } else {
                                        Brush.linearGradient(colors = listOf(Color(0xFF1E293B), Color(0xFF1E293B)))
                                    }
                                ),
                            shape = RoundedCornerShape(16.dp),
                            enabled = uiState.selectedMedia.isNotEmpty() && !uiState.isProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent, 
                                contentColor = Color.White,
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = Color.White.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Process & Export", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
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
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { mediaLauncher.launch("*/*") }
                            ) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White.copy(alpha = 0.08f), modifier = Modifier.size(96.dp))
                            }
                        } else {
                            val firstItem = uiState.selectedMedia.first()
                            PreviewContainer(firstItem, uiState.watermarkConfigs)
                            
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
                                    "CONV_VIEWFINDER [1 / ${uiState.selectedMedia.size}]",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White.copy(alpha = 0.9f),
                                    letterSpacing = 1.2.sp
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
                                    .clickable { mediaLauncher.launch("*/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add more", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // Processing Overlay
            if (uiState.isProcessing) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(progress = { uiState.processingProgress }, color = MaterialTheme.colorScheme.primary, strokeWidth = 6.dp, modifier = Modifier.size(80.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Processing Batch...", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${(uiState.processingProgress * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
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
fun PreviewContainer(item: MediaItem, configs: List<WatermarkConfig>) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        
        configs.forEach { config ->
            WatermarkOverlay(config)
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
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            when (config.type) {
                WatermarkType.TEXT -> {
                    Text(
                        config.text,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = (14 * config.scale).sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp
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
                    Box(
                        modifier = Modifier
                            .size((100 * config.scale).dp)
                            .border(2.dp, Color.Red, RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "REMOVE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WatermarkConfigTools(config: WatermarkConfig, onUpdate: (WatermarkConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (config.type == WatermarkType.TEXT) {
            OutlinedTextField(
                value = config.text,
                onValueChange = { onUpdate(config.copy(text = it)) },
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
        }
        
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Transparency", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Text("${(config.opacity * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
            }
            Slider(
                value = config.opacity,
                onValueChange = { onUpdate(config.copy(opacity = it)) },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF6366F1),
                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Scale", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Text("x${String.format("%.1f", config.scale)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
            }
            Slider(
                value = config.scale,
                onValueChange = { onUpdate(config.copy(scale = it)) },
                valueRange = 0.1f..3f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF6366F1),
                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Horizontal Position", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Text("${config.x.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
            }
            Slider(
                value = config.x,
                onValueChange = { onUpdate(config.copy(x = it)) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF6366F1),
                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Vertical Position", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Text("${config.y.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
            }
            Slider(
                value = config.y,
                onValueChange = { onUpdate(config.copy(y = it)) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF6366F1),
                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
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
