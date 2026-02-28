package net.nonimi.camscan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import net.nonimi.camscan.ui.theme.CAMSCANTheme
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    @ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CAMSCANTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onExit = { finish() }
                    )
                }
            }
        }
    }
}

@ExperimentalGetImage
@Composable
fun MainScreen(modifier: Modifier = Modifier, onExit: () -> Unit) {
    var scannedBarcodes by remember { mutableStateOf<List<Barcode>>(emptyList()) }
    var selectedBarcode by remember { mutableStateOf<Barcode?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var isScanningMultiple by remember { mutableStateOf(false) }

    // Live detection states for overlay
    var liveBarcodes by remember { mutableStateOf<List<Barcode>>(emptyList()) }
    var analyzerWidth by remember { mutableStateOf(1) }
    var analyzerHeight by remember { mutableStateOf(1) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            showCamera = true
        }
    }

    if (showCamera) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isScanningMultiple) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Scanning... (Found ${liveBarcodes.size} in current frame)",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(8.dp).align(Alignment.CenterHorizontally)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val executor = ContextCompat.getMainExecutor(ctx)

                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                val options = BarcodeScannerOptions.Builder()
                                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                                    .build()
                                val scanner = BarcodeScanning.getClient(options)

                                // Logic for multi-frame aggregation
                                val uniqueBarcodesMap = mutableMapOf<String, Barcode>()
                                var framesSinceLastNewBarcode = 0
                                val maxEmptyFrames = 10 // Increased a bit to give more time for live boxes

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also {
                                        it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                            val image = imageProxy.image
                                            if (image != null) {
                                                val rotation = imageProxy.imageInfo.rotationDegrees
                                                val isRotated = rotation == 90 || rotation == 270
                                                analyzerWidth = if (isRotated) imageProxy.height else imageProxy.width
                                                analyzerHeight = if (isRotated) imageProxy.width else imageProxy.height

                                                val inputImage = InputImage.fromMediaImage(
                                                    image,
                                                    rotation
                                                )
                                                scanner.process(inputImage)
                                                    .addOnSuccessListener { barcodes ->
                                                        liveBarcodes = barcodes

                                                        if (barcodes.isNotEmpty()) {
                                                            isScanningMultiple = true
                                                            var foundNew = false
                                                            barcodes.forEach { barcode ->
                                                                val raw = barcode.rawValue ?: "unknown"
                                                                if (!uniqueBarcodesMap.containsKey(raw)) {
                                                                    uniqueBarcodesMap[raw] = barcode
                                                                    foundNew = true
                                                                }
                                                            }

                                                            if (foundNew) {
                                                                framesSinceLastNewBarcode = 0
                                                            } else {
                                                                framesSinceLastNewBarcode++
                                                            }

                                                            if (framesSinceLastNewBarcode >= maxEmptyFrames) {
                                                                scannedBarcodes = uniqueBarcodesMap.values.toList()
                                                                executor.execute {
                                                                    cameraProvider.unbindAll()
                                                                    showCamera = false
                                                                    isScanningMultiple = false
                                                                    liveBarcodes = emptyList()
                                                                }
                                                            }
                                                        } else if (isScanningMultiple) {
                                                            framesSinceLastNewBarcode++
                                                            if (framesSinceLastNewBarcode >= maxEmptyFrames) {
                                                                scannedBarcodes = uniqueBarcodesMap.values.toList()
                                                                executor.execute {
                                                                    cameraProvider.unbindAll()
                                                                    showCamera = false
                                                                    isScanningMultiple = false
                                                                    liveBarcodes = emptyList()
                                                                }
                                                            }
                                                        }
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Log.e("CAMERA_DEBUG", "Error scanning", e)
                                                    }
                                                    .addOnCompleteListener { imageProxy.close() }
                                            }
                                        }
                                    }

                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                Log.e("CAMERA_DEBUG", "Use case binding failed", e)
                            }
                        }, executor)

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay for rough bounding boxes
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val scaleX = size.width / analyzerWidth.toFloat()
                    val scaleY = size.height / analyzerHeight.toFloat()

                    liveBarcodes.forEach { barcode ->
                        barcode.boundingBox?.let { rect ->
                            // Rough mapping: scale the rect from analyzer resolution to view resolution
                            val left = rect.left * scaleX
                            val top = rect.top * scaleY
                            val right = rect.right * scaleX
                            val bottom = rect.bottom * scaleY

                            drawRect(
                                color = Color.Green,
                                topLeft = Offset(left, top),
                                size = Size(right - left, bottom - top),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Scanned Results",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scannedBarcodes) { barcode ->
                    BarcodeItem(barcode = barcode) {
                        selectedBarcode = barcode
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        scannedBarcodes = emptyList()
                        liveBarcodes = emptyList()
                        when (PackageManager.PERMISSION_GRANTED) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                                showCamera = true
                            }
                            else -> {
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("SCAN AGAIN")
                }
                Button(
                    onClick = onExit,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("EXIT")
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(text = "Version: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Build Time: ${BuildConfig.BUILD_TIME}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    selectedBarcode?.let { barcode ->
        BarcodeDetailsDialog(barcode = barcode, onDismiss = { selectedBarcode = null })
    }
}

@Composable
fun BarcodeItem(barcode: Barcode, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = barcode.displayValue ?: barcode.rawValue ?: "No Data",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Type: ${getBarcodeTypeString(barcode.valueType)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun BarcodeDetailsDialog(barcode: Barcode, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Barcode Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(label = "Value", value = barcode.rawValue ?: "N/A")
                DetailRow(label = "Type", value = getBarcodeTypeString(barcode.valueType))
                DetailRow(label = "Format", value = getBarcodeFormatString(barcode.format))

                barcode.boundingBox?.let { box ->
                    DetailRow(label = "Bounding Box", value = "L:${box.left}, T:${box.top}, R:${box.right}, B:${box.bottom}")
                }

                barcode.cornerPoints?.let { corners ->
                    val cornersStr = corners.joinToString("\n") { "(${it.x}, ${it.y})" }
                    DetailRow(label = "Corners", value = cornersStr)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

fun getBarcodeTypeString(type: Int): String = when (type) {
    Barcode.TYPE_CONTACT_INFO -> "Contact Info"
    Barcode.TYPE_EMAIL -> "Email"
    Barcode.TYPE_ISBN -> "ISBN"
    Barcode.TYPE_PHONE -> "Phone"
    Barcode.TYPE_PRODUCT -> "Product"
    Barcode.TYPE_SMS -> "SMS"
    Barcode.TYPE_TEXT -> "Text"
    Barcode.TYPE_URL -> "URL"
    Barcode.TYPE_WIFI -> "Wi-Fi"
    Barcode.TYPE_GEO -> "Geo Location"
    Barcode.TYPE_CALENDAR_EVENT -> "Calendar Event"
    Barcode.TYPE_DRIVER_LICENSE -> "Driver License"
    else -> "Unknown"
}

fun getBarcodeFormatString(format: Int): String = when (format) {
    Barcode.FORMAT_CODE_128 -> "CODE 128"
    Barcode.FORMAT_CODE_39 -> "CODE 39"
    Barcode.FORMAT_CODE_93 -> "CODE 93"
    Barcode.FORMAT_CODABAR -> "CODABAR"
    Barcode.FORMAT_DATA_MATRIX -> "DATA MATRIX"
    Barcode.FORMAT_EAN_13 -> "EAN 13"
    Barcode.FORMAT_EAN_8 -> "EAN 8"
    Barcode.FORMAT_ITF -> "ITF"
    Barcode.FORMAT_QR_CODE -> "QR CODE"
    Barcode.FORMAT_UPC_A -> "UPC A"
    Barcode.FORMAT_UPC_E -> "UPC E"
    Barcode.FORMAT_PDF417 -> "PDF417"
    Barcode.FORMAT_AZTEC -> "AZTEC"
    else -> "Unknown"
}

@ExperimentalGetImage
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    CAMSCANTheme {
        MainScreen(onExit = {})
    }
}
