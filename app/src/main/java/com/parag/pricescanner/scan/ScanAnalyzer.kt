package com.parag.pricescanner.scan

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** What the camera saw in a single frame. */
data class FrameResult(
    val prices: List<PriceParser.Candidate>,
    val nameGuess: String?,
    val barcode: String?,
) {
    val bestPrice: Double? get() = prices.firstOrNull()?.value
    val isEmpty: Boolean get() = prices.isEmpty() && barcode == null
}

/**
 * Runs on-device text recognition and barcode scanning on each camera frame.
 * Results are delivered on the main thread via [onResult].
 */
class ScanAnalyzer(
    private val isPaused: () -> Boolean,
    private val onResult: (FrameResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE,
            )
            .build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null || isPaused()) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        val textTask = textRecognizer.process(image)
        val barcodeTask = barcodeScanner.process(image)

        Tasks.whenAllComplete(textTask, barcodeTask).addOnCompleteListener {
            try {
                val text = if (textTask.isSuccessful) textTask.result else null
                val codes = if (barcodeTask.isSuccessful) barcodeTask.result else emptyList()

                val prices = text?.let { PriceParser.candidates(it.text) }.orEmpty()
                val lines = text?.textBlocks.orEmpty().flatMap { block ->
                    block.lines.map { NameGuesser.Line(it.text, it.boundingBox?.height() ?: 0) }
                }
                val barcode = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                onResult(FrameResult(prices, NameGuesser.guess(lines), barcode))
            } finally {
                imageProxy.close()
            }
        }
    }

    fun close() {
        textRecognizer.close()
        barcodeScanner.close()
    }
}
