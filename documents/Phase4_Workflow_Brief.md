# Phase 4: Document Upload System Workflow Brief

## Task: ID & Billing Address Image Upload with Firebase Storage

### Goal
Implement image upload functionality for borrower government ID and billing address proof, with compression, Firebase Storage integration, and display in app.

### Context
- **Prerequisites**: Phases 1, 2, 3 complete (Firebase working)
- **Estimated Time**: 6-8 hours
- **Complexity**: Medium-High
- **New Services**: Firebase Storage, Image compression, Coil image loading

### Feature Breakdown

#### 1. Dependencies & Setup (30 min)

**Add Dependencies:**
```kotlin
// build.gradle.kts (app level)
implementation("com.google.firebase:firebase-storage-ktx:20.3.0")
implementation("io.coil-kt:coil-compose:2.5.0")
```

**Firebase Storage Setup:**
- Verify Storage is enabled in Firebase Console
- Configure storage rules for user-specific paths
- Test connection

#### 2. Database Schema (1 hour)

**New Table:**
```sql
CREATE TABLE borrower_documents (
    id TEXT PRIMARY KEY NOT NULL,
    borrower_id TEXT NOT NULL,
    document_type TEXT NOT NULL,  -- GOVERNMENT_ID, BILLING_ADDRESS, OTHER
    file_name TEXT NOT NULL,
    local_file_path TEXT,         -- Local cache
    firebase_storage_path TEXT,   -- Cloud path
    uploaded_at TEXT NOT NULL,
    sync_status TEXT NOT NULL,
    FOREIGN KEY(borrower_id) REFERENCES borrowers(id) ON DELETE CASCADE
)
```

**Migration:**
- Create MIGRATION_4_5
- Add new table
- No data migration needed

**Files to Create:**
- `BorrowerDocumentEntity.kt`
- `BorrowerDocumentDao.kt`

**Files to Modify:**
- `Migrations.kt`
- `LendTrackDatabase.kt`
- `DatabaseModule.kt`

#### 3. Domain Models (30 min)

**Add to DomainModels.kt:**
```kotlin
enum class DocumentType {
    GOVERNMENT_ID,
    BILLING_ADDRESS,
    OTHER
}

data class BorrowerDocument(
    val id: String = UUID.randomUUID().toString(),
    val borrowerId: String,
    val documentType: DocumentType,
    val fileName: String,
    val localFilePath: String?,
    val firebaseStoragePath: String?,
    val uploadedAt: LocalDateTime = LocalDateTime.now(),
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
```

#### 4. Document Storage Manager (2-3 hours)

**Create Service:**
`app/src/main/java/com/lendtrack/app/core/storage/DocumentStorageManager.kt`

**Features:**
- Upload image to Firebase Storage
- Compress image before upload (max 1024x1024, 80% quality)
- Save local copy
- Store metadata in database
- Download image from cloud
- Delete document (cloud + local + database)
- Handle offline mode (local only, sync later)

**Key Methods:**
```kotlin
suspend fun uploadDocument(
    borrowerId: String,
    documentType: DocumentType,
    imageUri: Uri
): Result<BorrowerDocument>

suspend fun downloadDocument(
    documentId: String
): Result<File>

suspend fun deleteDocument(
    documentId: String
): Result<Unit>

private suspend fun compressImage(
    uri: Uri
): File
```

**Image Compression Logic:**
1. Decode bitmap from URI
2. Calculate scale ratio (max 1024x1024)
3. Scale bitmap if needed
4. Compress to JPEG at 80% quality
5. Save to cache directory
6. Return File

**Firebase Storage Path:**
```
lendtrack/{userId}/borrowers/{borrowerId}/documents/{fileName}
```

#### 5. Repository Layer (1 hour)

**Create:**
`app/src/main/java/com/lendtrack/app/data/repository/DocumentRepository.kt`

**Methods:**
```kotlin
fun observeDocumentsByBorrower(borrowerId: String): Flow<List<BorrowerDocument>>

suspend fun uploadDocument(
    borrowerId: String,
    documentType: DocumentType,
    imageUri: Uri
): Result<String>  // Returns document ID

suspend fun deleteDocument(documentId: String): Result<Unit>

suspend fun getDocument(documentId: String): BorrowerDocument?
```

**Integration:**
- Use DocumentStorageManager for cloud operations
- Use DocumentDao for local operations
- Queue sync operations
- Add activity log entries

#### 6. UI Components (2 hours)

**Create DocumentUploadSection Composable:**
```kotlin
@Composable
fun DocumentUploadSection(
    label: String,
    documentType: DocumentType,
    existingDocument: BorrowerDocument?,
    onUpload: (Uri) -> Unit,
    onDelete: (String) -> Unit,
)
```

**Features:**
- If no document: Show "Upload" button
- If document exists: Show image preview
- Image clickable to view full screen
- Delete button overlay
- Upload progress indicator
- Error handling

**Image Picker Integration:**
```kotlin
val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
) { uri ->
    uri?.let { onUpload(it) }
}
```

**Files to Create:**
- `DocumentComponents.kt`

#### 7. Borrower Detail Integration (1 hour)

**Add to BorrowerDetailScreen:**
```kotlin
LtPanel(title = "Documents") {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DocumentUploadSection(
            label = "Government ID",
            documentType = DocumentType.GOVERNMENT_ID,
            existingDocument = state.governmentId,
            onUpload = { uri -> 
                viewModel.uploadDocument(DocumentType.GOVERNMENT_ID, uri) 
            },
            onDelete = { docId -> 
                viewModel.deleteDocument(docId) 
            }
        )
        
        DocumentUploadSection(
            label = "Billing Address Proof",
            documentType = DocumentType.BILLING_ADDRESS,
            existingDocument = state.billingAddress,
            onUpload = { uri -> 
                viewModel.uploadDocument(DocumentType.BILLING_ADDRESS, uri) 
            },
            onDelete = { docId -> 
                viewModel.deleteDocument(docId) 
            }
        )
    }
}
```

**Update ViewModel:**
- Load documents on borrower load
- Handle upload/delete operations
- Show loading/error states
- Update UI on success

**Files to Modify:**
- `BorrowerDetailScreen.kt`
- `BorrowerDetailViewModel.kt`
- `BorrowerDetailUiState.kt`

#### 8. Full Screen Image Viewer (Optional, 1 hour)

**Create:**
`FullScreenImageDialog.kt`

**Features:**
- Show image in full screen
- Pinch to zoom
- Close button
- Share button
- Delete button

#### 9. Offline Support (30 min)

**Features:**
- Upload images locally when offline
- Queue for Firebase Storage upload
- Sync when online
- Show sync status badge

**Files to Modify:**
- `DocumentStorageManager.kt`
- `FirebaseSyncManager.kt`

#### 10. Firebase Storage Rules (15 min)

**Configure in Firebase Console:**
```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /lendtrack/{userId}/borrowers/{borrowerId}/documents/{document} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

### Testing Requirements

**Unit Tests:**
- Image compression logic
- Path generation
- Upload/download flows

**Integration Tests:**
- Upload to Firebase Storage
- Download from Firebase Storage
- Delete from Firebase Storage
- Offline mode behavior

**Manual Tests:**
- Select image from gallery
- Verify compression works
- Upload succeeds
- Image displays correctly
- Can delete document
- Multiple documents per borrower
- Offline upload queues correctly
- Large images (5MB+) handle gracefully
- Different aspect ratios

### Error Handling

**Common Errors:**
- Permission denied (camera/storage)
- Image too large
- Upload failed (network)
- Storage quota exceeded
- Invalid file format
- Corrupted image

**User-Friendly Messages:**
```kotlin
when (error) {
    is SecurityException -> "Permission denied to access images"
    is IOException -> "Failed to read image file"
    is FirebaseException -> "Upload failed. Please try again."
    else -> "Unexpected error occurred"
}
```

### Performance Considerations

**Image Compression:**
- Max dimension: 1024px
- Quality: 80%
- Format: JPEG
- Expected size: 100-300KB

**Caching:**
- Use Coil for image loading (automatic caching)
- Keep local copy for offline viewing
- Clear old cache periodically

**Upload:**
- Show progress indicator
- Allow background upload
- Retry on failure

### Security Considerations

**Privacy:**
- Images stored in user-specific paths
- Firebase rules enforce user isolation
- No public access to documents
- Local files in app-private directory

**Validation:**
- Check file type before upload
- Validate image dimensions
- Scan for malicious content (optional)
- Limit file size (max 10MB)

### Success Criteria
- [ ] Dependencies added successfully
- [ ] Database migration successful
- [ ] Can select image from gallery
- [ ] Image compresses before upload
- [ ] Upload to Firebase Storage succeeds
- [ ] Image displays in borrower detail
- [ ] Can delete uploaded document
- [ ] Multiple documents supported per borrower
- [ ] Works offline (local + sync later)
- [ ] Proper error messages shown
- [ ] No crashes with large images
- [ ] Build successful
- [ ] Manual testing complete

### Known Limitations
- Maximum file size: 10MB
- Supported formats: JPG, PNG only
- No video upload
- No PDF upload (future enhancement)
- One document per type per borrower (can be extended)

### Future Enhancements
- Multiple documents per type
- PDF support
- Document expiry dates
- OCR text extraction
- Document verification status
