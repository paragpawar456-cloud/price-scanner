package com.parag.pricescanner.scan

import android.graphics.Rect
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.time.LocalDate

/** What the camera saw in a single frame. */
data class FrameResult(
    val prices: List<PriceParser.Candidate>,
    val nameGuess: String?,
    val barcode: String?,
    val mfgDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
) {
    val bestPrice: Double? get() = prices.firstOrNull()?.value
    val isEmpty: Boolean get() = prices.isEmpty() && barcode == null
}

/**
 * Runs on-device text recognition (English every frame, Hindi/Devanagari every few
 * frames for product names) and barcode scanning. Results arrive on the main thread.
 */
class ScanAnalyzer(
    private val isPaused: () -> Boolean,
    private val onResult: (FrameResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val devanagari = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE,
            )
            .build()
    )
    private var frame = 0L
    private var lastHindiName: String? = null

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null || isPaused()) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        val textTask = latin.process(image)
        val barcodeTask = barcodeScanner.process(image)
        val hindiTask: Task<Text>? = if (frame++ % 4 == 0L) devanagari.process(image) else null

        Tasks.whenAllComplete(listOfNotNull(textTask, barcodeTask, hindiTask)).addOnCompleteListener {
            try {
                val text = if (textTask.isSuccessful) textTask.result else null
                val codes = if (barcodeTask.isSuccessful) barcodeTask.result else emptyList()
                val lines = text?.let(::toLines).orEmpty()
                val label = LabelParser.parse(lines)

                val nameLines = lines.map { NameGuesser.Line(it.text, it.box.height) }.toMutableList()
                if (hindiTask != null) {
                    lastHindiName = if (hindiTask.isSuccessful) NameGuesser.guess(
                        toLines(hindiTask.result)
                            .filter { l -> l.text.any { it in 'ऀ'..'ॿ' } }
                            .map { NameGuesser.Line(it.text, it.box.height) }
                    ) else null
                }
                val name = NameGuesser.guess(nameLines) ?: lastHindiName

                val barcode = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                onResult(FrameResult(label.prices, name, barcode, label.mfgDate, label.expiryDate))
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun toLines(text: Text): List<LabelParser.Line> = text.textBlocks.flatMap { block ->
        block.lines.mapNotNull { line ->
            val lb = line.boundingBox ?: return@mapNotNull null
            LabelParser.Line(
                line.text,
                lb.toBox(),
                line.elements.mapNotNull { e -> e.boundingBox?.let { LabelParser.Word(e.text, it.toBox()) } },
            )
        }
    }

    private fun Rect.toBox() = LabelParser.Box(left, top, right, bottom)

    fun close() {
        latin.close()
        devanagari.close()
        barcodeScanner.close()
    }
}
