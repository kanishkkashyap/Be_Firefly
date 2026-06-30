package com.firefly.befirefly.ui.components

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

@Composable
fun QrCodeGenerator(
    content: String,
    size: Dp = 280.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(content) {
        generateQrCode(content)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR Code",
                modifier = Modifier.size(size)
            )
        } else {
            Text("QR Error", color = Color.Red, fontSize = 12.sp)
        }
    }
}

private fun generateQrCode(content: String): Bitmap? {
    return try {
        val width = 512
        val height = 512
        
        // Use hints for better scanability
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, // Medium error correction
            EncodeHintType.MARGIN to 2, // Small quiet zone margin
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            width,
            height,
            hints
        )
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) // Higher quality
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        
        Log.d("QrGenerator", "Generated QR code for content length: ${content.length}")
        bitmap
    } catch (e: Exception) {
        Log.e("QrGenerator", "Failed to generate QR code for: ${content.take(20)}...", e)
        null
    }
}
