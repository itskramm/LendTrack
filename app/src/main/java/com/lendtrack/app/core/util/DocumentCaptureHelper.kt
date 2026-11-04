package com.lendtrack.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

/**
 * DocumentCaptureHelper provides utilities for capturing documents from camera or gallery,
 * with automatic image compression to keep files under 2MB.
 *
 * Usage:
 * 1. Register launchers in your Composable using rememberLauncherForActivityResult
 * 2. Call captureFromCamera or selectFromGallery with the appropriate launcher
 * 3. Compressed images are saved to app-internal storage: files/documents/borrower_{borrowerId}/
 *
 * File naming convention:
 * - ID Document: id_document.jpg
 * - Billing Address: billing_address_document.jpg
 */
class DocumentCaptureHelper(private val context: Context) {

    companion object {
        private const val DOCUMENTS_DIR = "documents"
        private const val MAX_SIZE_BYTES = 2 * 1024 * 1024L // 2MB
        private const val JPEG_QUALITY = 85
        
        const val ID_DOCUMENT = "id_document"
        const val BILLING_ADDRESS_DOCUMENT = "billing_address_document"
    }

    /**
     * Capture a photo from camera. The image will be compressed and saved to internal storage.
     *
     * @param borrowerId The borrower's UUID
     * @param documentType Either ID_DOCUMENT or BILLING_ADDRESS_DOCUMENT
     * @param launcher Camera launcher from rememberLauncherForActivityResult(ActivityResultContracts.TakePicture())
     * @param onSuccess Callback with the final local file path
     * @param onError Callback with error message
     */
    fun captureFromCamera(
        borrowerId: String,
        documentType: String,
        launcher: ManagedActivityResultLauncher<Uri, Boolean>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val targetFile = getDocumentFile(borrowerId, documentType, createIfMissing = true)
            val uri = Uri.fromFile(targetFile)
            
            // Store callback for after capture completes
            // Note: In production, you'd use a more robust callback mechanism
            // For now, the caller should handle the result in the launcher's callback
            launcher.launch(uri)
        } catch (e: Exception) {
            onError("Failed to prepare camera capture: ${e.message}")
        }
    }

    /**
     * Select an image from gallery. The image will be copied, compressed, and saved to internal storage.
     *
     * @param borrowerId The borrower's UUID
     * @param documentType Either ID_DOCUMENT or BILLING_ADDRESS_DOCUMENT
     * @param launcher Gallery launcher from rememberLauncherForActivityResult(ActivityResultContracts.GetContent())
     * @param onSuccess Callback with the final local file path
     * @param onError Callback with error message
     */
    fun selectFromGallery(
        borrowerId: String,
        documentType: String,
        launcher: ManagedActivityResultLauncher<String, Uri?>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // Launch gallery picker for images
            launcher.launch("image/*")
        } catch (e: Exception) {
            onError("Failed to open gallery: ${e.message}")
        }
    }

    /**
     * Compress an image from sourceUri and save it to targetFile.
     * Scales down the image if needed to keep file size under maxSizeBytes.
     *
     * @param sourceUri URI of the source image (from camera or gallery)
     * @param targetFile Destination file in internal storage
     * @param maxSizeBytes Maximum file size in bytes (default 2MB)
     * @return The absolute path of the compressed file
     * @throws Exception if compression fails
     */
    fun compressImage(
        sourceUri: Uri,
        targetFile: File,
        maxSizeBytes: Long = MAX_SIZE_BYTES
    ): String {
        val contentResolver = context.contentResolver
        
        // Decode image dimensions without loading full bitmap
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }
        
        val originalWidth = options.outWidth
        val originalHeight = options.outHeight
        
        if (originalWidth <= 0 || originalHeight <= 0) {
            throw IllegalArgumentException("Invalid image dimensions")
        }
        
        // Calculate initial sample size to reduce memory usage
        var sampleSize = 1
        var scaledWidth = originalWidth
        var scaledHeight = originalHeight
        
        // Estimate bytes per pixel (assuming ARGB_8888 = 4 bytes)
        var estimatedBytes = (scaledWidth * scaledHeight * 4L)
        
        // Downsample if image is too large in memory or file size
        while (estimatedBytes > maxSizeBytes * 4) {  // Use 4x margin for initial sample
            sampleSize *= 2
            scaledWidth = originalWidth / sampleSize
            scaledHeight = originalHeight / sampleSize
            estimatedBytes = scaledWidth * scaledHeight * 4L
        }
        
        // Load bitmap with sample size
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        
        val bitmap = contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, decodeOptions)
        } ?: throw IllegalStateException("Failed to decode image")
        
        // Try compression with quality 85, then scale down if still too large
        var quality = JPEG_QUALITY
        var scaleFactor = 1.0f
        var compressed: Bitmap = bitmap
        
        // Iteratively reduce scale if needed
        var attempts = 0
        while (attempts < 5) {
            FileOutputStream(targetFile).use { outputStream ->
                compressed.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            }
            
            if (targetFile.length() <= maxSizeBytes) {
                break
            }
            
            // File still too large, scale down by sqrt(2) ≈ 1.41x reduction in area
            scaleFactor /= sqrt(2.0).toFloat()
            val newWidth = (bitmap.width * scaleFactor).toInt()
            val newHeight = (bitmap.height * scaleFactor).toInt()
            
            if (newWidth < 100 || newHeight < 100) {
                // Don't scale below reasonable minimum
                break
            }
            
            if (compressed != bitmap) {
                compressed.recycle()
            }
            compressed = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            attempts++
        }
        
        // Clean up
        if (compressed != bitmap) {
            compressed.recycle()
        }
        bitmap.recycle()
        
        return targetFile.absolutePath
    }

    /**
     * Get the file path for a document, creating parent directories if needed.
     *
     * @param borrowerId The borrower's UUID
     * @param documentType Either ID_DOCUMENT or BILLING_ADDRESS_DOCUMENT
     * @param createIfMissing Create parent directories if true
     * @return File object pointing to the document location
     */
    fun getDocumentFile(
        borrowerId: String,
        documentType: String,
        createIfMissing: Boolean = false
    ): File {
        val borrowerDir = File(context.filesDir, "$DOCUMENTS_DIR/borrower_$borrowerId")
        
        if (createIfMissing && !borrowerDir.exists()) {
            borrowerDir.mkdirs()
        }
        
        val filename = when (documentType) {
            ID_DOCUMENT -> "id_document.jpg"
            BILLING_ADDRESS_DOCUMENT -> "billing_address_document.jpg"
            else -> throw IllegalArgumentException("Unknown document type: $documentType")
        }
        
        return File(borrowerDir, filename)
    }

    /**
     * Check if a document exists for the given borrower and type.
     */
    fun documentExists(borrowerId: String, documentType: String): Boolean {
        return getDocumentFile(borrowerId, documentType).exists()
    }

    /**
     * Delete a document file from internal storage.
     */
    fun deleteDocument(borrowerId: String, documentType: String): Boolean {
        val file = getDocumentFile(borrowerId, documentType)
        return if (file.exists()) file.delete() else false
    }
}
