package com.ahmesh.app.presentation.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ahmesh.app.R
import com.ahmesh.app.data.pairing.QrCodeHelper
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.util.concurrent.Executors

@Composable
fun QrCodeScannerCard(
    manualCodeInput: String,
    hasScanError: Boolean,
    onManualCodeChange: (String) -> Unit,
    onManualSubmit: () -> Unit,
    onQrCodeDetected: (String) -> Unit,
    onScanFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingZxingScan by remember { mutableStateOf(false) }
    var showAlignmentGuide by rememberSaveable { mutableStateOf(true) }

    val zxingScannerPrompt = stringResource(id = R.string.pairing_zxing_prompt)
    val zxingScanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { scanResult ->
        val contents = scanResult.contents
        if (!contents.isNullOrBlank()) {
            onQrCodeDetected(contents)
        } else if (scanResult.originalIntent != null) {
            onScanFailed()
        }
    }

    val launchZxingScanner = {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(zxingScannerPrompt)
            setBeepEnabled(false)
            setBarcodeImageEnabled(false)
            setOrientationLocked(false)
        }
        zxingScanLauncher.launch(options)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted && pendingZxingScan) {
            pendingZxingScan = false
            launchZxingScanner()
        } else {
            pendingZxingScan = false
        }
    }

    val borderStrokeColor by animateColorAsState(
        targetValue = if (hasScanError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
        },
        animationSpec = tween(durationMillis = 250),
        label = "scanner_card_border"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("qr_scanner_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(width = 1.dp, color = borderStrokeColor)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.pairing_scanner_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.pairing_scanner_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val guideToggleCd = stringResource(id = R.string.pairing_scanner_guide_toggle_cd)
                FilterChip(
                    selected = showAlignmentGuide,
                    onClick = { showAlignmentGuide = !showAlignmentGuide },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusStrong,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(id = R.string.pairing_scanner_guide_toggle),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .semantics { contentDescription = guideToggleCd }
                        .testTag("qr_scanner_guide_toggle")
                )
            }

            AnimatedContent(
                targetState = hasCameraPermission,
                transitionSpec = {
                    fadeIn(tween(260)) togetherWith fadeOut(tween(200))
                },
                label = "scanner_permission_transition"
            ) { permitted ->
                if (permitted) {
                    CameraQrViewfinder(
                        hasScanError = hasScanError,
                        showAlignmentGuide = showAlignmentGuide,
                        onQrCodeDetected = onQrCodeDetected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.08f)
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("qr_scanner_permission_card"),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = stringResource(id = R.string.pairing_camera_permission_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(id = R.string.pairing_camera_permission_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("qr_scanner_grant_camera_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(stringResource(id = R.string.pairing_grant_camera_cta))
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showAlignmentGuide,
                enter = fadeIn(tween(220)) + expandVertically(tween(240)),
                exit = fadeOut(tween(180)) + shrinkVertically(tween(200))
            ) {
                QrScannerAlignmentTipsCard()
            }

            OutlinedButton(
                onClick = {
                    if (hasCameraPermission) {
                        launchZxingScanner()
                    } else {
                        pendingZxingScan = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("pairing_launch_zxing_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.pairing_launch_zxing_scanner))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

            Text(
                text = stringResource(id = R.string.pairing_manual_divider),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = manualCodeInput,
                onValueChange = onManualCodeChange,
                label = { Text(stringResource(id = R.string.pairing_manual_input_label)) },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pairing_manual_code_input")
            )

            Button(
                onClick = onManualSubmit,
                enabled = manualCodeInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("pairing_manual_submit_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.pairing_manual_submit))
            }
        }
    }
}

@Composable
private fun CameraQrViewfinder(
    hasScanError: Boolean,
    showAlignmentGuide: Boolean,
    onQrCodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }

    val primaryAccent = MaterialTheme.colorScheme.primary
    val secondaryAccent = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error

    val isCameraActive = boundCamera != null && !cameraError
    val beamAlpha by animateFloatAsState(
        targetValue = if (isCameraActive) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "qr_beam_fade"
    )
    val viewfinderBorderColor by animateColorAsState(
        targetValue = if (hasScanError) errorColor else primaryAccent.copy(alpha = 0.65f),
        animationSpec = tween(durationMillis = 250),
        label = "qr_viewfinder_border"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "qr_scan_beam")
    val beamFraction by infiniteTransition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.86f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "qr_beam_y"
    )
    val beamBreath by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "qr_beam_breath"
    )

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                cameraProviderRef?.unbindAll()
            }
            analysisExecutor.shutdown()
        }
    }

    AnimatedContent(
        targetState = cameraError,
        transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
        label = "camera_error_transition"
    ) { hasError ->
        if (hasError) {
            Surface(
                modifier = modifier.testTag("qr_camera_unavailable_notice"),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.pairing_camera_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .border(1.5.dp, viewfinderBorderColor, RoundedCornerShape(20.dp))
                    .testTag("qr_camera_viewfinder")
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener(
                            {
                                runCatching {
                                    val cameraProvider = cameraProviderFuture.get()
                                    cameraProviderRef = cameraProvider
                                    val preview = Preview.Builder().build().apply {
                                        surfaceProvider = previewView.surfaceProvider
                                    }
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .apply {
                                            setAnalyzer(analysisExecutor) { imageProxy ->
                                                try {
                                                    val decoded = QrCodeHelper.decodeQrFromImageProxy(imageProxy)
                                                    if (!decoded.isNullOrBlank()) {
                                                        ContextCompat.getMainExecutor(ctx).execute {
                                                            onQrCodeDetected(decoded)
                                                        }
                                                    }
                                                } finally {
                                                    imageProxy.close()
                                                }
                                            }
                                        }

                                    cameraProvider.unbindAll()
                                    boundCamera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                }.onFailure {
                                    cameraError = true
                                }
                            },
                            ContextCompat.getMainExecutor(ctx)
                        )
                        previewView
                    }
                )

                val hasTorch = boundCamera?.cameraInfo?.hasFlashUnit() == true
                QrScannerAlignmentOverlay(
                    hasScanError = hasScanError,
                    showDetailedGuide = showAlignmentGuide,
                    isBeamActive = beamAlpha > 0.01f,
                    beamFraction = beamFraction,
                    beamAlpha = beamAlpha * beamBreath,
                    bottomBadgePaddingDp = if (hasTorch) 62 else 12,
                    modifier = Modifier.fillMaxSize()
                )

                if (hasTorch) {
                    FilledTonalButton(
                        onClick = {
                            val next = !isTorchEnabled
                            isTorchEnabled = next
                            boundCamera?.cameraControl?.enableTorch(next)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("qr_scanner_torch_button"),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(
                            imageVector = if (isTorchEnabled) {
                                Icons.Rounded.FlashlightOn
                            } else {
                                Icons.Rounded.FlashlightOff
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                id = if (isTorchEnabled) R.string.pairing_torch_on else R.string.pairing_torch_off
                            ),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun QrScannerAlignmentOverlay(
    hasScanError: Boolean,
    showDetailedGuide: Boolean,
    isBeamActive: Boolean = true,
    beamFraction: Float = 0.5f,
    beamAlpha: Float = 0.85f,
    bottomBadgePaddingDp: Int = 12,
    modifier: Modifier = Modifier
) {
    val primaryAccent = MaterialTheme.colorScheme.primary
    val secondaryAccent = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error
    val scrimColor = MaterialTheme.colorScheme.scrim
    val overlayCd = stringResource(id = R.string.pairing_scanner_overlay_cd)

    Box(
        modifier = modifier
            .semantics { contentDescription = overlayCd }
            .testTag("qr_scanner_alignment_overlay")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = size.minDimension * 0.14f
            val cornerLen = size.minDimension * 0.11f
            val stroke = 3.5.dp.toPx()

            val left = inset
            val top = inset
            val right = size.width - inset
            val bottom = size.height - inset
            val targetCorner = CornerRadius(14.dp.toPx(), 14.dp.toPx())

            if (showDetailedGuide) {
                val scrimPath = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRoundRect(
                        RoundRect(
                            left = left,
                            top = top,
                            right = right,
                            bottom = bottom,
                            cornerRadius = targetCorner
                        )
                    )
                }
                drawPath(
                    path = scrimPath,
                    color = scrimColor.copy(alpha = 0.44f)
                )

                val guideDash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                val innerInset = 10.dp.toPx()
                drawRoundRect(
                    color = (if (hasScanError) errorColor else primaryAccent).copy(alpha = 0.42f),
                    topLeft = Offset(left + innerInset, top + innerInset),
                    size = androidx.compose.ui.geometry.Size(
                        width = (right - left - innerInset * 2).coerceAtLeast(10f),
                        height = (bottom - top - innerInset * 2).coerceAtLeast(10f)
                    ),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    style = Stroke(width = 1.2.dp.toPx(), pathEffect = guideDash)
                )

                // Center crosshair reticle
                val centerX = (left + right) / 2f
                val centerY = (top + bottom) / 2f
                val crossArm = 9.dp.toPx()
                val crossColor = (if (hasScanError) errorColor else Color.White).copy(alpha = 0.65f)
                drawLine(
                    color = crossColor,
                    start = Offset(centerX - crossArm, centerY),
                    end = Offset(centerX + crossArm, centerY),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = crossColor,
                    start = Offset(centerX, centerY - crossArm),
                    end = Offset(centerX, centerY + crossArm),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            val tlColor = if (hasScanError) errorColor else primaryAccent
            val trColor = if (hasScanError) errorColor else secondaryAccent

            drawLine(tlColor, Offset(left, top), Offset(left + cornerLen, top), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(tlColor, Offset(left, top), Offset(left, top + cornerLen), strokeWidth = stroke, cap = StrokeCap.Round)

            drawLine(trColor, Offset(right, top), Offset(right - cornerLen, top), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(trColor, Offset(right, top), Offset(right, top + cornerLen), strokeWidth = stroke, cap = StrokeCap.Round)

            drawLine(trColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(trColor, Offset(left, bottom), Offset(left, bottom - cornerLen), strokeWidth = stroke, cap = StrokeCap.Round)

            drawLine(tlColor, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(tlColor, Offset(right, bottom), Offset(right, bottom - cornerLen), strokeWidth = stroke, cap = StrokeCap.Round)

            if (isBeamActive) {
                val beamY = top + (bottom - top) * beamFraction.coerceIn(0.08f, 0.92f)
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(
                            primaryAccent.copy(alpha = 0.25f * beamAlpha),
                            secondaryAccent.copy(alpha = 0.25f * beamAlpha)
                        )
                    ),
                    start = Offset(left + 8.dp.toPx(), beamY),
                    end = Offset(right - 8.dp.toPx(), beamY),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(
                            primaryAccent.copy(alpha = beamAlpha),
                            secondaryAccent.copy(alpha = beamAlpha)
                        )
                    ),
                    start = Offset(left + 10.dp.toPx(), beamY),
                    end = Offset(right - 10.dp.toPx(), beamY),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        if (showDetailedGuide) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp, start = 14.dp, end = 14.dp)
                    .testTag("qr_scanner_guide_pill"),
                shape = RoundedCornerShape(50),
                color = if (hasScanError) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
                } else {
                    scrimColor.copy(alpha = 0.68f)
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (hasScanError) errorColor else primaryAccent.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (hasScanError) {
                            Icons.Rounded.ErrorOutline
                        } else {
                            Icons.Rounded.CenterFocusStrong
                        },
                        contentDescription = null,
                        tint = if (hasScanError) errorColor else primaryAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(
                            id = if (hasScanError) {
                                R.string.pairing_scanner_overlay_error_hint
                            } else {
                                R.string.pairing_scanner_overlay_align_hint
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasScanError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            Color.White
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomBadgePaddingDp.dp, start = 14.dp, end = 14.dp)
                    .testTag("qr_scanner_guide_distance_badge"),
                shape = RoundedCornerShape(50),
                color = scrimColor.copy(alpha = 0.62f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Text(
                    text = stringResource(id = R.string.pairing_scanner_overlay_distance_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun QrScannerAlignmentTipsCard(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("qr_scanner_alignment_guide_banner"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CenterFocusStrong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(id = R.string.pairing_scanner_guide_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            QrAlignmentTipRow(
                icon = Icons.Rounded.QrCode2,
                text = stringResource(id = R.string.pairing_scanner_guide_step_1)
            )
            QrAlignmentTipRow(
                icon = Icons.Rounded.CenterFocusStrong,
                text = stringResource(id = R.string.pairing_scanner_guide_step_2)
            )
            QrAlignmentTipRow(
                icon = Icons.Rounded.WbSunny,
                text = stringResource(id = R.string.pairing_scanner_guide_step_3)
            )
        }
    }
}

@Composable
private fun QrAlignmentTipRow(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun QrScanRetryDialog(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("pairing_retry_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = stringResource(id = R.string.pairing_retry_dialog_title),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("pairing_retry_confirm_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(id = R.string.pairing_retry_dialog_cta))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(stringResource(id = R.string.pairing_dismiss_feedback))
            }
        }
    )
}
