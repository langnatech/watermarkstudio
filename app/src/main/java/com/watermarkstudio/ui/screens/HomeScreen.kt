package com.watermarkstudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermarkstudio.viewmodel.WatermarkViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermarkstudio.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddWatermark: () -> Unit,
    onNavigateToAddLogoWatermark: () -> Unit,
    onNavigateToRemoveWatermark: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    viewModel: WatermarkViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(0) } // 0: Add, 1: Remove
    var currentTab by remember { mutableStateOf(0) } // 0: Editor, 1: Library

    LaunchedEffect(Unit) {
        viewModel.checkPremium(context)
        viewModel.refreshProcessedLibrary(context)
        if (viewModel.consumePendingLibraryTab()) {
            currentTab = 1
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab == 1) {
            viewModel.refreshProcessedLibrary(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090E1A), // Astro navy/slate deep
                        Color(0xFF02050A)  // Space black
                    )
                )
            )
    ) {
        // High-end ambient atmospheric glowing sphere behind header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4F46E5).copy(alpha = 0.18f), // Soft neon glow
                            Color.Transparent
                        )
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    if (!uiState.isPremium) {
                        com.watermarkstudio.util.AdMobBannerAd(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF090E1A))
                                .padding(vertical = 4.dp)
                        )
                    }
                    NavigationBar(
                        containerColor = Color(0xFF090E1A).copy(alpha = 0.92f),
                        tonalElevation = 8.dp,
                        modifier = Modifier.border(
                            1.dp,
                            Color.White.copy(alpha = 0.05f)
                        )
                    ) {
                        NavigationBarItem(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            icon = { 
                                Icon(
                                    Icons.Default.EditNote, 
                                    contentDescription = null,
                                    tint = if (currentTab == 0) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                ) 
                            },
                            label = { Text(stringResource(R.string.tab_editor), color = if (currentTab == 0) Color.White else Color.White.copy(alpha = 0.6f), fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal) }
                        )
                        NavigationBarItem(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            icon = { 
                                Icon(
                                    Icons.Default.FolderOpen, 
                                    contentDescription = null,
                                    tint = if (currentTab == 1) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                ) 
                            },
                            label = { Text(stringResource(R.string.tab_library), color = if (currentTab == 1) Color.White else Color.White.copy(alpha = 0.6f), fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (currentTab == 1) {
                    LibrarySection(
                        processedUris = uiState.processedMediaUris,
                        onBackToEditor = { currentTab = 0 }
                    )
                } else {
                    // Sleek Branding Top bar
                    Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.BrandingWatermark,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    stringResource(R.string.app_display_name),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                                if (uiState.isPremium) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(Color(0xFFFBBF24), Color(0xFFF59E0B))
                                                )
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "PRO",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                }
                            }
                            Text(
                                stringResource(R.string.engine_subtitle),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Luxury Crown Subscribe Trigger
                    IconButton(
                        onClick = onNavigateToSubscription,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.isPremium) Color(0xFFFBBF24).copy(alpha = 0.1f)
                                else Color.White.copy(alpha = 0.04f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (uiState.isPremium) Color(0xFFFBBF24).copy(alpha = 0.3f) 
                                        else Color.White.copy(alpha = 0.08f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.WorkspacePremium,
                            contentDescription = "Subscription Plans",
                            tint = if (uiState.isPremium) Color(0xFFFBBF24) else Color(0xFF6366F1)
                        )
                    }
                }

                // Premium Promotion Banner (Luxury design)
                if (!uiState.isPremium) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 20.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF1E1E38), // Deep velvet slate
                                        Color(0xFF10101C)  // Rich steel
                                    )
                                )
                            )
                            .border(
                                width = 1.2.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF4F46E5).copy(alpha = 0.6f),
                                        Color(0xFFEC4899).copy(alpha = 0.2f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable(onClick = onNavigateToSubscription)
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    Color(0xFFFBBF24).copy(alpha = 0.2f),
                                                    Color.Transparent
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WorkspacePremium,
                                        contentDescription = "Royal Badge",
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = stringResource(R.string.upgrade_pro_banner),
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                stringResource(R.string.royal_save_60),
                                                color = Color(0xFFFCA5A5),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                    Text(
                                        text = stringResource(R.string.upgrade_pro_banner_features),
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Go",
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 20.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.05f))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.pro_subscription_active_label),
                            color = Color(0xFF10B981),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Interactive Mode Selector
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                        .padding(4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val addSelected = selectedMode == 0
                        Button(
                            onClick = { selectedMode = 0 },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (addSelected) Color(0xFF1E293B) else Color.Transparent,
                                contentColor = if (addSelected) Color.White else Color(0xFF64748B)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = null
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (addSelected) Color(0xFF6366F1) else Color(0xFF64748B)
                                )
                                Text(stringResource(R.string.btn_add_watermark), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        val removeSelected = selectedMode == 1
                        Button(
                            onClick = { selectedMode = 1 },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (removeSelected) Color(0xFF1E293B) else Color.Transparent,
                                contentColor = if (removeSelected) Color.White else Color(0xFF64748B)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = null
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (removeSelected) Color(0xFF10B981) else Color(0xFF64748B)
                                )
                                Text(stringResource(R.string.btn_remove_stamp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Hero Workspace Import Deck (Visual Glass deck)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.03f),
                                    Color.White.copy(alpha = 0.01f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.09f),
                                    Color.White.copy(alpha = 0.02f)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable { 
                            if (selectedMode == 0) onNavigateToAddWatermark() else onNavigateToRemoveWatermark()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Soft inner aura radial gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        (if (selectedMode == 0) Color(0xFF6366F1) else Color(0xFF10B981)).copy(alpha = 0.08f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        // Import trigger disc
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.03f))
                                .border(1.dp, Color.White.copy(alpha = 0.07f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = if (selectedMode == 0) {
                                                listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
                                            } else {
                                                listOf(Color(0xFF10B981), Color(0xFF059669))
                                            }
                                        )
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (selectedMode == 0) Icons.Default.AddPhotoAlternate else Icons.Default.AutoFixHigh,
                                    contentDescription = "Trigger File Select",
                                    modifier = Modifier.size(32.dp),
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = if (selectedMode == 0) {
                                stringResource(R.string.home_open_watermark_studio)
                            } else {
                                stringResource(R.string.home_open_remove_studio)
                            },
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (selectedMode == 0) {
                                stringResource(R.string.home_open_studio_subtitle)
                            } else {
                                stringResource(R.string.home_open_remove_subtitle)
                            },
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )

                        if (selectedMode == 1 && !uiState.isPremium) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val remaining =
                                (uiState.maxFreeExportsPerDay - uiState.freeExportsUsedToday).coerceAtLeast(0)
                            Text(
                                text = stringResource(R.string.remove_free_exports_badge, remaining),
                                color = Color(0xFF10B981),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }

                    // Floating Glass badge label
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            stringResource(R.string.studio_console_label),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                // Quick Launch Deck Title
                Text(
                    stringResource(R.string.title_quick_actions),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF6366F1),
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionItem(
                        icon = Icons.Default.TextFields,
                        label = stringResource(R.string.action_text),
                        accentColor = Color(0xFF8B5CF6),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToAddWatermark
                    )
                    QuickActionItem(
                        icon = Icons.Default.Image,
                        label = stringResource(R.string.action_logo),
                        accentColor = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToAddLogoWatermark
                    )
                    QuickActionItem(
                        icon = Icons.Default.Layers,
                        label = stringResource(R.string.action_multilayer),
                        accentColor = Color(0xFFEC4899),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.setPendingMultilayer(true)
                            onNavigateToAddWatermark()
                        },
                    )
                }
                }
            }
        }
    }
}

@Composable
fun QuickActionItem(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.02f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.1f))
                .border(1.dp, accentColor.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
fun LibrarySection(
    processedUris: List<android.net.Uri>,
    onBackToEditor: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (processedUris.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.02f))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(42.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.no_processed_files_yet_title),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.no_processed_files_yet_desc),
                color = Color(0xFF64748B),
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onBackToEditor,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.btn_launch_studio_console), fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.processed_library_title),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF6366F1),
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = stringResource(R.string.processed_library_desc_format, processedUris.size),
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            }

            items(processedUris.size) { index ->
                val uri = processedUris[index]
                val mimeType = try {
                    context.contentResolver.getType(uri) ?: ""
                } catch (e: Exception) {
                    ""
                }
                val isVideo = mimeType.contains("video", ignoreCase = true) || uri.toString().contains(".mp4", ignoreCase = true)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.02f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isVideo) Color(0xFF10B981).copy(alpha = 0.1f) 
                                    else Color(0xFF3B82F6).copy(alpha = 0.1f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isVideo) Icons.Default.PlayArrow else Icons.Default.Image,
                                contentDescription = null,
                                tint = if (isVideo) Color(0xFF10B981) else Color(0xFF3B82F6),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            val filename = uri.lastPathSegment ?: (if (isVideo) "video.mp4" else "image.jpg")
                            Text(
                                text = filename,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isVideo) stringResource(R.string.format_ultra_hd_video) else stringResource(R.string.format_high_res_image),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, if (isVideo) "video/*" else "image/*")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, context.getString(R.string.toast_no_app_format), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.05f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = if (isVideo) "video/*" else "image/*"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share_media_chooser)))
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, context.getString(R.string.toast_failed_share), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.05f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

