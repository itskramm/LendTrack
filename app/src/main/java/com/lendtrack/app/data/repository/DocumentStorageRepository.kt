package com.lendtrack.app.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing document uploads and downloads with Firebase Storage.
 * 
 * Storage structure: /users/{userId}/borrowers/{borrowerId}/documents/{documentType}.jpg
 * 
 * Features:
 * - Progress tracking for uploads and downloads
 * - Retry mechanism for failed operations
 * - Automatic authentication check
 */
@Singleton
class DocumentStorageRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseStorage: FirebaseStorage
) {
    
    // In-memory queue for failed operations
    private val failedUploads = mutableListOf<RetryableUpload>()
    private val failedDownloads = mutableListOf<RetryableDownload>()

    /**
     * Upload a document to Firebase Storage.
     * 
     * @param localUri Local file URI (e.g., from DocumentCaptureHelper)
     * @param borrowerId Borrower's UUID
     * @param documentType Document type identifier (e.g., "id_document")
     * @return Flow emitting upload progress and result
     */
    fun uploadDocument(
        localUri: String,
        borrowerId: String,
        documentType: String
    ): Flow<UploadState> = callbackFlow {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            trySend(UploadState.Failed("User not authenticated"))
            close()
            return@callbackFlow
        }

        val file = File(localUri)
        if (!file.exists()) {
            trySend(UploadState.Failed("Local file not found: $localUri"))
            close()
            return@callbackFlow
        }

        val firebasePath = "users/$userId/borrowers/$borrowerId/documents/$documentType.jpg"
        val storageRef = firebaseStorage.reference.child(firebasePath)
        val fileUri = Uri.fromFile(file)

        try {
            val uploadTask = storageRef.putFile(fileUri)

            // Listen for progress
            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                trySend(UploadState.Uploading(progress))
            }

            // Listen for completion
            uploadTask.addOnSuccessListener {
                trySend(UploadState.Success(firebasePath))
                close()
            }

            uploadTask.addOnFailureListener { exception ->
                val errorMessage = exception.message ?: "Unknown upload error"
                
                // Queue for retry
                val retryable = RetryableUpload(localUri, borrowerId, documentType)
                failedUploads.add(retryable)
                
                trySend(UploadState.Failed(errorMessage, retryCallback = {
                    uploadDocument(localUri, borrowerId, documentType)
                }))
                close(exception)
            }

        } catch (e: Exception) {
            trySend(UploadState.Failed(e.message ?: "Upload initialization failed"))
            close(e)
        }

        awaitClose {
            // Cleanup if needed
        }
    }

    /**
     * Download a document from Firebase Storage to local file.
     * 
     * @param firebasePath Path in Firebase Storage (e.g., from BorrowerEntity.idDocumentFirebasePath)
     * @param localTargetPath Local file path where document should be saved
     * @return Flow emitting download progress and result
     */
    fun downloadDocument(
        firebasePath: String,
        localTargetPath: String
    ): Flow<DownloadState> = callbackFlow {
        if (firebasePath.isBlank()) {
            trySend(DownloadState.Failed("Firebase path is empty"))
            close()
            return@callbackFlow
        }

        val targetFile = File(localTargetPath)
        
        // Ensure parent directory exists
        targetFile.parentFile?.mkdirs()

        try {
            val storageRef = firebaseStorage.reference.child(firebasePath)
            val downloadTask = storageRef.getFile(targetFile)

            // Listen for progress
            downloadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                trySend(DownloadState.Downloading(progress))
            }

            // Listen for completion
            downloadTask.addOnSuccessListener {
                trySend(DownloadState.Success)
                close()
            }

            downloadTask.addOnFailureListener { exception ->
                val errorMessage = exception.message ?: "Unknown download error"
                
                // Queue for retry
                val retryable = RetryableDownload(firebasePath, localTargetPath)
                failedDownloads.add(retryable)
                
                trySend(DownloadState.Failed(errorMessage, retryCallback = {
                    downloadDocument(firebasePath, localTargetPath)
                }))
                close(exception)
            }

        } catch (e: Exception) {
            trySend(DownloadState.Failed(e.message ?: "Download initialization failed"))
            close(e)
        }

        awaitClose {
            // Cleanup if needed
        }
    }

    /**
     * Delete a document from Firebase Storage.
     * 
     * @param firebasePath Path in Firebase Storage
     * @return Result indicating success or failure
     */
    suspend fun deleteDocument(firebasePath: String): Result<Unit> {
        if (firebasePath.isBlank()) {
            return Result.failure(IllegalArgumentException("Firebase path is empty"))
        }

        return try {
            val storageRef = firebaseStorage.reference.child(firebasePath)
            storageRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retry all queued failed uploads.
     * Returns a list of Flows for each retry operation.
     */
    fun retryAllUploads(): List<Flow<UploadState>> {
        val uploads = failedUploads.toList()
        failedUploads.clear()
        return uploads.map { retry ->
            uploadDocument(retry.localUri, retry.borrowerId, retry.documentType)
        }
    }

    /**
     * Retry all queued failed downloads.
     * Returns a list of Flows for each retry operation.
     */
    fun retryAllDownloads(): List<Flow<DownloadState>> {
        val downloads = failedDownloads.toList()
        failedDownloads.clear()
        return downloads.map { retry ->
            downloadDocument(retry.firebasePath, retry.localTargetPath)
        }
    }

    /**
     * Clear all failed operation queues.
     */
    fun clearFailedQueues() {
        failedUploads.clear()
        failedDownloads.clear()
    }

    /**
     * Get count of failed operations.
     */
    fun getFailedOperationsCount(): Pair<Int, Int> {
        return Pair(failedUploads.size, failedDownloads.size)
    }
}

// ── State Classes ─────────────────────────────────────────────────────────────

/**
 * Represents the state of a document upload operation.
 */
sealed class UploadState {
    /**
     * Upload in progress.
     * @param progress Progress percentage (0-100)
     */
    data class Uploading(val progress: Int) : UploadState()
    
    /**
     * Upload completed successfully.
     * @param firebasePath The path in Firebase Storage where document was saved
     */
    data class Success(val firebasePath: String) : UploadState()
    
    /**
     * Upload failed.
     * @param error Error message
     * @param retryCallback Optional callback to retry the operation
     */
    data class Failed(
        val error: String,
        val retryCallback: (() -> Flow<UploadState>)? = null
    ) : UploadState()
}

/**
 * Represents the state of a document download operation.
 */
sealed class DownloadState {
    /**
     * Download in progress.
     * @param progress Progress percentage (0-100)
     */
    data class Downloading(val progress: Int) : DownloadState()
    
    /**
     * Download completed successfully.
     */
    data object Success : DownloadState()
    
    /**
     * Download failed.
     * @param error Error message
     * @param retryCallback Optional callback to retry the operation
     */
    data class Failed(
        val error: String,
        val retryCallback: (() -> Flow<DownloadState>)? = null
    ) : DownloadState()
}

// ── Internal Retry Data Classes ───────────────────────────────────────────────

private data class RetryableUpload(
    val localUri: String,
    val borrowerId: String,
    val documentType: String
)

private data class RetryableDownload(
    val firebasePath: String,
    val localTargetPath: String
)
