package com.firefly.befirefly.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "QrScanner"

@Composable
fun QrCodeScanner(
    onQrCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var statusText by remember { mutableStateOf("Initializing camera...") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (!granted) statusText = "Camera permission denied"
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            val hasScanned = remember { AtomicBoolean(false) }
            
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setTargetResolution(Size(1280, 720))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(
                                Executors.newSingleThreadExecutor(),
                                QrCodeAnalyzer(
                                    onQrCodeScanned = { result ->
                                        if (hasScanned.compareAndSet(false, true)) {
                                            Log.d(TAG, "✅ QR Code scanned successfully: ${result.take(30)}...")
                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                android.widget.Toast.makeText(ctx, "✅ QR Code detected!", android.widget.Toast.LENGTH_SHORT).show()
                                                onQrCodeScanned(result)
                                            }
                                        }
                                    },
                                    onStatusUpdate = { status ->
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            statusText = status
                                        }
                                    }
                                )
                            )

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                statusText = "Point camera at QR code"
                            }
                            Log.d(TAG, "Camera bound successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Camera setup failed", e)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                statusText = "Camera error: ${e.message}"
                            }
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlay
            ScannerOverlay(statusText = statusText)
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera permission required", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun ScannerOverlay(statusText: String = "Point camera at QR code") {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val animatedY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scanner_line"
    )

    val boxSize = 280.dp
    val boxSizePx = with(LocalDensity.current) { boxSize.toPx() }
    val cornerRadius = 16.dp
    val cornerRadiusPx = with(LocalDensity.current) { cornerRadius.toPx() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            val left = (canvasWidth - boxSizePx) / 2
            val top = (canvasHeight - boxSizePx) / 2
            val right = left + boxSizePx
            val bottom = top + boxSizePx

            drawPath(
                path = Path().apply {
                    addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            rect = Rect(left, top, right, bottom),
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                        )
                    )
                    fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                },
                color = Color.Black.copy(alpha = 0.6f)
            )

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = GeometrySize(boxSizePx, boxSizePx),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                style = Stroke(width = 4.dp.toPx())
            )
            
            val lineY = top + (boxSizePx * animatedY)
            drawLine(
                color = Color(0xFF007AFF),
                start = Offset(left + 20f, lineY),
                end = Offset(right - 20f, lineY),
                strokeWidth = 2.dp.toPx()
            )
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Scan QR Code",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                statusText,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp
            )
        }
    }
}

/**
 * QR Code image analyzer with multiple scanning strategies.
 * 
 * Strategy:
 * 1. First try scanning the FULL frame (no crop) — catches QR codes anywhere
 * 2. If that fails, try center crop — reduces noise for focused scanning
 * 3. Uses TRY_HARDER and QR_CODE-only format for best accuracy
 * 4. Properly handles rowStride padding on all devices
 */
private class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit,
    private val onStatusUpdate: ((String) -> Unit)? = null
) : ImageAnalysis.Analyzer {
    
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE)
        ))
    }
    
    private var frameCount = 0

    override fun analyze(image: ImageProxy) {
        try {
            frameCount++
            
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val width = image.width
            val height = image.height

            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            
            // Log every 60 frames (~2 seconds) so we know the analyzer is alive
            if (frameCount % 60 == 1) {
                Log.d(TAG, "📷 Analyzing frame #$frameCount (${width}x${height}, rowStride=$rowStride)")
                onStatusUpdate?.invoke("Scanning... (frame $frameCount)")
            }

            // Strategy 1: Scan FULL frame (no cropping)
            var result = tryDecode(data, rowStride, height, 0, 0, width, height)
            
            // Strategy 2: Center crop (60% of frame) for noisy environments
            if (result == null) {
                val cropW = width * 3 / 5
                val cropH = height * 3 / 5
                val cropLeft = (width - cropW) / 2
                val cropTop = (height - cropH) / 2
                result = tryDecode(data, rowStride, height, cropLeft, cropTop, cropW, cropH)
            }
            
            if (result != null && result.isNotBlank()) {
                Log.d(TAG, "🎯 Decoded QR (frame #$frameCount): ${result.take(30)}...")
                onQrCodeScanned(result)
            }
        } catch (e: Exception) {
            if (e !is com.google.zxing.NotFoundException) {
                Log.w(TAG, "Analyze error: ${e.message}")
            }
        } finally {
            image.close()
        }
    }
    
    private fun tryDecode(
        data: ByteArray,
        dataWidth: Int,
        dataHeight: Int,
        cropLeft: Int,
        cropTop: Int,
        cropWidth: Int,
        cropHeight: Int
    ): String? {
        return try {
            val source = PlanarYUVLuminanceSource(
                data,
                dataWidth,
                dataHeight,
                cropLeft,
                cropTop,
                cropWidth,
                cropHeight,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val decoded = reader.decodeWithState(binaryBitmap)
            reader.reset()
            decoded.text
        } catch (e: com.google.zxing.NotFoundException) {
            reader.reset()
            null
        } catch (e: Exception) {
            reader.reset()
            null
        }
    }
}
