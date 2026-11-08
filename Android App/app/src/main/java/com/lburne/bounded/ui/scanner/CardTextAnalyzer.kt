package com.lburne.bounded.ui.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class CardTextAnalyzer(private val viewModel: ScannerViewModel) : ImageAnalysis.Analyzer, AutoCloseable {

    // One recognizer for the analyzer's lifetime; creating one per frame leaked native resources.
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(image)
            .addOnSuccessListener { visionText -> viewModel.processVisionText(visionText) }
            .addOnFailureListener { e -> e.printStackTrace() }
            .addOnCompleteListener { imageProxy.close() }
    }

    override fun close() {
        recognizer.close()
    }
}
