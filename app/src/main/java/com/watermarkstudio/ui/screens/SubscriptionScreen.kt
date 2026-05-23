package com.watermarkstudio.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermarkstudio.billing.BillingProducts
import com.watermarkstudio.util.BillingUiEvent
import com.watermarkstudio.util.RestorePurchaseResult
import com.watermarkstudio.viewmodel.WatermarkViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermarkstudio.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    viewModel: WatermarkViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val billingProducts by viewModel.billingProducts.collectAsStateWithLifecycle()
    val purchaseSuccess by viewModel.purchaseSuccessEvent.collectAsStateWithLifecycle()
    val purchaseFlowFinished by viewModel.purchaseFlowFinishedEvent.collectAsStateWithLifecycle()
    val billingUiEvent by viewModel.billingUiEvent.collectAsStateWithLifecycle()
    val restorePurchaseResult by viewModel.restorePurchaseResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // Localized labels for plans
    val planWeeklyTitle = stringResource(R.string.plan_weekly_title)
    val planWeeklyTag = stringResource(R.string.plan_weekly_tag)
    val planWeeklyDesc = stringResource(R.string.plan_weekly_desc)
    
    val planMonthlyTitle = stringResource(R.string.plan_monthly_title)
    val planMonthlyTag = stringResource(R.string.plan_monthly_tag)
    val planMonthlyDesc = stringResource(R.string.plan_monthly_desc)
    
    val planYearlyTitle = stringResource(R.string.plan_yearly_title)
    val planYearlyTag = stringResource(R.string.plan_yearly_tag)
    val planYearlyDesc = stringResource(R.string.plan_yearly_desc)

    val unitWeek = stringResource(R.string.unit_week)
    val unitMonth = stringResource(R.string.unit_month)
    val unitYear = stringResource(R.string.unit_year)

    // Extractor helper for localized store prices
    fun getProductPrice(productId: String, defaultPrice: String): String {
        val details = billingProducts.find { it.productId == productId } ?: return defaultPrice
        val pricingPhase = details.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull() ?: return defaultPrice
        return "${pricingPhase.formattedPrice} / ${when(productId) {
            BillingProducts.WEEKLY -> unitWeek
            BillingProducts.MONTHLY -> unitMonth
            BillingProducts.YEARLY -> unitYear
            else -> unitMonth
        }}"
    }

    // Available subscription plans
    val priceWeeklyFallback = stringResource(R.string.plan_price_weekly_fallback)
    val priceMonthlyFallback = stringResource(R.string.plan_price_monthly_fallback)
    val priceYearlyFallback = stringResource(R.string.plan_price_yearly_fallback)
    val plans =
        remember(
            billingProducts,
            planWeeklyTitle,
            planMonthlyTitle,
            planYearlyTitle,
            priceWeeklyFallback,
            priceMonthlyFallback,
            priceYearlyFallback,
        ) {
            listOf(
                SubPlan(
                    "weekly",
                    planWeeklyTitle,
                    getProductPrice(BillingProducts.WEEKLY, priceWeeklyFallback),
                    planWeeklyTag,
                    planWeeklyDesc,
                ),
                SubPlan(
                    "monthly",
                    planMonthlyTitle,
                    getProductPrice(BillingProducts.MONTHLY, priceMonthlyFallback),
                    planMonthlyTag,
                    planMonthlyDesc,
                    isPopular = true,
                ),
                SubPlan(
                    "yearly",
                    planYearlyTitle,
                    getProductPrice(BillingProducts.YEARLY, priceYearlyFallback),
                    planYearlyTag,
                    planYearlyDesc,
                ),
            )
        }
    
    var selectedPlanId by remember { mutableStateOf("monthly") }
    var isPurchasing by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.checkPremium(context)
    }

    LaunchedEffect(purchaseFlowFinished) {
        if (purchaseFlowFinished) {
            isPurchasing = false
            viewModel.resetPurchaseFlowFinishedEvent()
        }
    }

    LaunchedEffect(purchaseSuccess) {
        if (purchaseSuccess) {
            showSuccessDialog = true
            viewModel.resetPurchaseEvent()
        }
    }

    LaunchedEffect(billingUiEvent) {
        when (billingUiEvent) {
            is BillingUiEvent.ProductUnavailable -> {
                Toast.makeText(context, context.getString(R.string.billing_product_unavailable), Toast.LENGTH_LONG).show()
            }
            is BillingUiEvent.BillingNotReady -> {
                Toast.makeText(context, context.getString(R.string.billing_not_ready), Toast.LENGTH_SHORT).show()
            }
            is BillingUiEvent.QueryFailed -> {
                Toast.makeText(context, (billingUiEvent as BillingUiEvent.QueryFailed).message, Toast.LENGTH_SHORT).show()
            }
            null -> Unit
        }
        if (billingUiEvent != null) {
            viewModel.clearBillingUiEvent()
        }
    }

    LaunchedEffect(restorePurchaseResult) {
        when (restorePurchaseResult) {
            RestorePurchaseResult.SUCCESS -> {
                Toast.makeText(context, context.getString(R.string.toast_restore_success), Toast.LENGTH_LONG).show()
            }
            RestorePurchaseResult.NONE -> {
                Toast.makeText(context, context.getString(R.string.toast_restore_none), Toast.LENGTH_LONG).show()
            }
            RestorePurchaseResult.ERROR -> {
                Toast.makeText(context, context.getString(R.string.toast_restore_error), Toast.LENGTH_LONG).show()
            }
            null -> Unit
        }
        if (restorePurchaseResult != null) {
            viewModel.clearRestorePurchaseResult()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Slate 900
                        Color(0xFF020617)  // Slate 950
                    )
                )
            )
    ) {
        // Gradient glow in background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E1B4B).copy(alpha = 0.6f), // Indigo 950
                            Color.Transparent
                        )
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.subscription_top_title),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Crown header with glowing effect
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFBBF24).copy(alpha = 0.15f),
                            modifier = Modifier
                                .size(72.dp)
                                .border(1.5.dp, Color(0xFFFBBF24), CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = "Premium Icon",
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = stringResource(R.string.privilege_header),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = stringResource(R.string.privilege_subheader),
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8), // Slate 400
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            lineHeight = 20.sp
                        )
                    }
                }

                // Premium Privileges List
                item {
                    PremiumPrivilegesSection()
                }

                // Select Plan Header
                item {
                    Text(
                        text = stringResource(R.string.choose_pro_plan),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFBBF24),
                        letterSpacing = 1.5.sp
                    )
                }

                // Subscription plans mapping
                items(plans.size) { index ->
                    val plan = plans[index]
                    val isSelected = selectedPlanId == plan.id
                    
                    PlanCard(
                        plan = plan,
                        isSelected = isSelected,
                        onClick = { selectedPlanId = plan.id }
                    )
                }

                // Subscribe CTA Button
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                val activity = context as? android.app.Activity
                                if (activity != null) {
                                    isPurchasing = true
                                    val started = viewModel.makePurchase(activity, selectedPlanId)
                                    if (!started) {
                                        isPurchasing = false
                                        Toast.makeText(context, "Billing service is not ready. Please try again.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Unable to start purchase. Please try again.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3B82F6), // Blue 500
                                contentColor = Color.White
                            ),
                            enabled = !isPurchasing
                        ) {
                            if (isPurchasing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (selectedPlanId == "weekly") stringResource(R.string.btn_start_trial) else stringResource(R.string.btn_subscribe_now),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Restore and legal
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.btn_restore_purchase),
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable {
                                        Toast.makeText(context, context.getString(R.string.toasting_restoring), Toast.LENGTH_SHORT).show()
                                        viewModel.restorePurchases()
                                    }
                            )
                            Divider(
                                color = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .height(12.dp)
                                    .width(1.dp)
                            )
                            Text(
                                text = stringResource(R.string.btn_terms_service),
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.terms_of_service_url)))
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, context.getString(R.string.toast_terms_show), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            Divider(
                                color = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .height(12.dp)
                                    .width(1.dp)
                            )
                            Text(
                                text = stringResource(R.string.btn_privacy_policy),
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_policy_url)))
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, context.getString(R.string.toast_privacy_show), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        Text(
                            text = stringResource(R.string.btn_manage_subscription),
                            color = Color(0xFF818CF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    val packageName = context.packageName
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            "https://play.google.com/store/account/subscriptions?package=$packageName",
                                        ),
                                    )
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, context.getString(R.string.billing_not_ready), Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 4.dp),
                        )

                        Text(
                            text = stringResource(R.string.subscription_autorenew_disclaimer),
                            color = Color(0xFF64748B), // Slate 500
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }

        // Success Dialog
        AnimatedVisibility(
            visible = showSuccessDialog,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(32.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF1E293B))
                        .padding(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(R.string.dialog_congrats_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.dialog_congrats_desc),
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = {
                            showSuccessDialog = false
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.btn_lets_start),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumPrivilegesSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        PrivilegeRow(
            icon = Icons.Rounded.Timer,
            iconTint = Color(0xFF3B82F6),
            title = stringResource(R.string.privilege_video_limits_title),
            desc = stringResource(R.string.privilege_video_limits_desc)
        )
        PrivilegeRow(
            icon = Icons.Rounded.AutoFixHigh,
            iconTint = Color(0xFFEC4899),
            title = stringResource(R.string.privilege_region_removal_title),
            desc = stringResource(R.string.privilege_region_removal_desc)
        )
        PrivilegeRow(
            icon = Icons.Rounded.Verified,
            iconTint = Color(0xFF10B981),
            title = stringResource(R.string.privilege_brand_signature_title),
            desc = stringResource(R.string.privilege_brand_signature_desc)
        )
        PrivilegeRow(
            icon = Icons.Rounded.HighQuality,
            iconTint = Color(0xFF8B5CF6),
            title = stringResource(R.string.privilege_hd_outputs_title),
            desc = stringResource(R.string.privilege_hd_outputs_desc)
        )
    }
}

@Composable
fun PrivilegeRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun PlanCard(
    plan: SubPlan,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.08f)
    val bgColor = if (isSelected) Color(0xFF1E293B) else Color.White.copy(alpha = 0.02f)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Radio button representation
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(
                            1.5.dp, 
                            if (isSelected) Color(0xFFFBBF24) else Color(0xFF64748B), 
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFBBF24))
                        )
                    }
                }
                
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = plan.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (plan.isPopular) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFBBF24))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    stringResource(R.string.plan_most_popular_badge),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = plan.desc,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = plan.price,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                if (plan.tag.isNotEmpty()) {
                    Text(
                        text = plan.tag,
                        fontSize = 10.sp,
                        color = if (isSelected) Color(0xFFFBBF24) else Color(0xFF64748B),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

data class SubPlan(
    val id: String,
    val title: String,
    val price: String,
    val tag: String,
    val desc: String,
    val isPopular: Boolean = false
)
