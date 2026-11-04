# Implementation Plan: Phase 4 - Document Upload System

## Overview
Add ID and billing address document capture, storage, and sync functionality to the LendTrack borrower management system. Documents will be stored locally in app-specific internal storage and synced to Firebase Storage.

---

## Implementation Steps

- [ ] 1. **Database Migration 6→7: Add document columns to BorrowerEntity**
      
      Add four new nullable String columns to the borrowers table:
      - `id_document_uri` (local file path)
      - `billing_address_document_uri` (local file path)
      - `id_document_firebase_path` (Firebase Storage path)
      - `billing_address_document_firebase_path` (Firebase Storage path)
      
      Files:
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/core/database/entities/Entities.kt` - Add columns to BorrowerEntity data class
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/core/database/migrations/Migrations.kt` - Create MIGRATION_6_7
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/core/database/LendTrackDatabase.kt` - Update version to 7, add MIGRATION_6_7 to migrations list
      
      Verify: Build the project with `./gradlew assembleDebug` — compilation succeeds without database errors.

- [ ] 2. **Update domain model and repository to handle document fields**
      
      Add document fields to the Borrower domain model and update BorrowerRepository to include them in add/update operations. Update the payload builder to serialize document paths for Firebase Realtime Database sync.
      
      Files:
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/domain/model/Models.kt` - Add document fields to Borrower data class
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/data/repository/BorrowerRepository.kt` - Add document parameters to addBorrower/updateBorrower functions, update entity mapping and payload building
      
      Verify: Build with `./gradlew assembleDebug` — no compilation errors.

- [ ] 3. **Add dependencies: Coil for image loading and Firebase Storage**
      
      Add Coil Compose library (v2.5.0) for image loading/display and Firebase Storage KTX to the Firebase BOM dependencies.
      
      Files:
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/build.gradle.kts` - Add `implementation("io.coil-kt:coil-compose:2.5.0")` and `implementation(libs.firebase.storage.ktx)` under Firebase BOM
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/gradle/libs.versions.toml` - Add `firebase-storage-ktx = { group = "com.google.firebase", name = "firebase-storage-ktx" }` to [libraries]
      
      Verify: Sync Gradle and build with `./gradlew assembleDebug` — dependencies resolve successfully.

- [ ] 4. **Add permissions to AndroidManifest.xml**
      
      Add CAMERA, READ_MEDIA_IMAGES (API 33+), and READ_EXTERNAL_STORAGE (API <33) permissions for document capture and gallery access.
      
      Files:
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/AndroidManifest.xml` - Add three permission declarations after existing permissions
      
      Verify: Build with `./gradlew assembleDebug` — manifest merges without errors.

- [ ] 5. **Update FileProvider paths to include documents directory**
      
      Extend file_provider_paths.xml to expose the documents directory for sharing captured images.
      
      Files:
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/res/xml/file_provider_paths.xml` - Add `<files-path name="documents" path="documents/" />` entry
      
      Verify: Build with `./gradlew assembleDebug` — XML validates successfully.

- [ ] 6. **Create DocumentCaptureHelper utility class**
      
      Build a utility class to handle camera capture and gallery selection using ActivityResultContracts. Include image compression (max 2MB) and storage in app-specific internal storage at `context.filesDir/documents/borrower_{id}/`.
      
      Files:
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/core/util/DocumentCaptureHelper.kt` - Create new file with DocumentCaptureHelper class
      
      Verify: Build with `./gradlew assembleDebug` — utility compiles without errors.

- [ ] 7. **Create DocumentStorageRepository for Firebase Storage sync**
      
      Create a repository to handle Firebase Storage uploads/downloads. Implement upload path structure: `/users/{userId}/borrowers/{borrowerId}/documents/{documentType}.jpg`. Include progress tracking, retry logic, and deletion when documents are removed locally. Use Hilt @Singleton injection.
      
      Files:
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/data/repository/DocumentStorageRepository.kt` - Create new repository following BorrowerRepository pattern
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/data/repository/RepositoryModule.kt` - Add @Provides binding if needed (check if @Singleton auto-injection is sufficient)
      
      Verify: Build with `./gradlew assembleDebug` — Hilt DI compiles and repository is injectable.

- [ ] 8. **Create DocumentViewerScreen for full-screen image viewing**
      
      Build a Compose screen with pinch-to-zoom, pan gestures, and a close button. Use Coil's AsyncImage for loading. Follow existing screen patterns from BorrowerDetailScreen.kt.
      
      Files:
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/feature/borrowers/DocumentViewerScreen.kt` - Create new Composable screen
      
      Verify: Build with `./gradlew assembleDebug` — screen compiles, no layout errors.

- [ ] 9. **Update BorrowerFormFields and AddBorrowerDialog for document upload**
      
      Add document URI fields to BorrowerFormFields data class. Add two document upload sections in AddBorrowerDialog (one for ID, one for billing address) using Material3 cards. Show thumbnail previews when documents exist. Include camera and gallery buttons with runtime permission requests. Follow the existing optional fields expandable pattern.
      
      Files:
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowersScreen.kt` - Update BorrowerFormFields data class and AddBorrowerDialog UI
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowersViewModel.kt` - Update addBorrowerFull to pass document URIs to repository
      
      Verify: Run the app and open Add Borrower dialog — new document sections render correctly, permission requests work.

- [ ] 10. **Update BorrowerDetailScreen to display and manage documents**
      
      Add two document cards (ID Document and Billing Address Document) after the borrower header card. Display thumbnails using Coil AsyncImage. Add tap-to-view navigation to DocumentViewerScreen. Show "Replace" and "Remove" buttons for each document. Display Firebase sync status indicator (uploading/synced/failed). Show placeholder state when no document uploaded.
      
      Files:
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailScreen.kt` - Add document display cards in the Column layout after the borrower header
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailViewModel.kt` - Add functions for document upload/remove/replace with Firebase Storage sync
      
      Verify: Run the app, view a borrower with documents — thumbnails display, tap opens full-screen viewer, sync status badges show correctly.

- [ ] 11. **Update navigation graph to include DocumentViewerScreen**
      
      Add a new route for document viewer and wire it from BorrowerDetailScreen tap gestures.
      
      Files:
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/navigation/LendTrackNavGraph.kt` - Add DocumentViewerScreen route with documentUri parameter
      
      Verify: Run the app, navigate from borrower detail to document viewer — navigation works, image displays with zoom/pan.

- [ ] 12. **Implement Firebase Storage sync in BorrowerDetailViewModel**
      
      Wire DocumentStorageRepository into BorrowerDetailViewModel. Upload documents immediately after capture/selection. Implement retry logic for failed uploads. Download documents when viewing borrower on a new device (if Firebase path exists but local URI is missing). Update Borrower entity with both local URI and Firebase path after successful upload.
      
      Files:
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailViewModel.kt` - Inject DocumentStorageRepository, implement upload/download/delete flows
      
      Verify: Run the app, upload a document — Firebase Storage console shows the file at `/users/{userId}/borrowers/{borrowerId}/documents/{type}.jpg`, local Room database has both URIs populated.

- [ ] 13. **Add error handling and offline support**
      
      Implement error dialogs for permission denied, camera unavailable, upload failed (with retry button), storage full, and network issues. Queue uploads for retry when device comes online. Show clear sync status in the UI (pending/syncing/synced/failed badges using existing LtSyncBadge component).
      
      Files:
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailViewModel.kt` - Add error state management and offline queue logic
      - `/Users/mark/LendTrack/.worktrees/phase-4-documents/app/src/main/java/com/lendtrack/app/data/repository/DocumentStorageRepository.kt` - Implement retry mechanism and offline queue
      
      Verify: Test with airplane mode enabled — upload queues locally. Re-enable network — upload completes and status badge changes to "Synced".

- [ ] 14. **Test dark mode compatibility**
      
      Verify all new UI components (document cards, viewer screen, upload buttons, thumbnails) render correctly in dark mode using MaterialTheme.colorScheme colors only (no hardcoded colors).
      
      Files: (No file changes — visual verification only)
      
      Verify: Toggle system dark mode, navigate through borrower detail and document screens — all components adapt correctly, text is readable, images have proper contrast.

- [ ] 15. **Integration testing: Full document lifecycle**
      
      End-to-end test: Add borrower → capture ID document → capture billing address document → view documents in detail screen → replace a document → remove a document → verify Firebase Storage has correct files → verify local Room database state matches.
      
      Files: (No new files — manual testing)
      
      Verify: Complete the full workflow manually on device/emulator — all steps work, no crashes, sync completes successfully, data persists after app restart.

---

## Digest

### Files Modified

**Database (Migration 6→7)**
- `BorrowerEntity` (Entities.kt, lines 71-106): Add 4 nullable String columns: `idDocumentUri`, `billingAddressDocumentUri`, `idDocumentFirebasePath`, `billingAddressDocumentFirebasePath`
- `Migrations.kt` (end of file): Create `MIGRATION_6_7` using ALTER TABLE ADD COLUMN for each new field with DEFAULT NULL
- `LendTrackDatabase.kt` (line 15): Change version from 6 to 7; add MIGRATION_6_7 to migrations list in DatabaseModule.kt

**Domain Model**
- `Borrower` data class (Models.kt, domain/model/): Add same 4 document fields matching BorrowerEntity

**Repository**
- `BorrowerRepository.kt` (lines 67-92 addBorrower, lines 96-133 updateBorrower): Add document parameters to functions, update entity creation and update operations, extend buildBorrowerPayload to include document paths

**Dependencies**
- `app/build.gradle.kts` (dependencies block, line ~80): Add Coil and Firebase Storage
- `gradle/libs.versions.toml` ([libraries] section): Add firebase-storage-ktx entry

**Permissions & Provider**
- `AndroidManifest.xml` (after line 10): Add CAMERA, READ_MEDIA_IMAGES, READ_EXTERNAL_STORAGE permissions
- `file_provider_paths.xml` (line 4): Add `<files-path name="documents" path="documents/" />`

**New Files**
- `app/src/main/java/com/lendtrack/app/core/util/DocumentCaptureHelper.kt`: Camera/gallery helper with compression
- `app/src/main/java/com/lendtrack/app/data/repository/DocumentStorageRepository.kt`: Firebase Storage upload/download/delete with retry
- `app/src/main/java/com/lendtrack/app/feature/borrowers/DocumentViewerScreen.kt`: Full-screen image viewer with zoom/pan

**UI Updates**
- `BorrowersScreen.kt` (lines 143-169 BorrowerFormFields, lines 171-296 AddBorrowerDialog): Add document fields to data class, add two document upload sections with camera/gallery buttons and thumbnail previews
- `BorrowersViewModel.kt` (line 71 addBorrowerFull): Pass document URIs to repository
- `BorrowerDetailScreen.kt` (after line 153, inside Column): Add two document cards (ID and billing address) with thumbnails, tap-to-view, replace/remove buttons, sync status badges
- `BorrowerDetailViewModel.kt`: Inject DocumentStorageRepository, add upload/download/replace/remove functions with Firebase sync, error handling, offline queue
- `LendTrackNavGraph.kt` (navigation routes): Add DocumentViewerScreen route with documentUri parameter

### Tests

**Existing Tests** (verify they still pass):
- `LoanCalculatorTest.kt` — loan calculation logic (not affected)
- `PaymentAllocationTest.kt` — payment allocation logic (not affected)
- `LoanValidationTest.kt` — loan validation (not affected)

**New Tests** (not created — manual verification only per task requirements):
- Would need DocumentStorageRepositoryTest for upload/download/retry logic
- Would need DocumentCaptureHelperTest for image compression and file operations

### Verification Commands

**Build**: `cd /Users/mark/LendTrack/.worktrees/phase-4-documents && ./gradlew assembleDebug`
- Verifies compilation, Hilt DI, Room schema generation, manifest merge

**Unit Tests** (existing): `cd /Users/mark/LendTrack/.worktrees/phase-4-documents && ./gradlew test`
- Confirms existing tests still pass after database migration

**Manual Testing on Device/Emulator**:
- Install APK: `./gradlew installDebug`
- Add borrower with documents → verify capture works
- View borrower detail → verify thumbnails display
- Tap thumbnail → verify full-screen viewer opens with zoom
- Check Firebase Storage console → verify files uploaded to correct paths
- Check Room database (Database Inspector in Android Studio) → verify local URIs and Firebase paths populated
- Toggle dark mode → verify UI renders correctly
- Enable airplane mode, capture document → verify queues locally
- Re-enable network → verify sync completes

### What I Verified

- Database at version 6 with migrations 1→2, 2→3, 3→4, 4→5, 5→6 already in place
- BorrowerEntity has all Phase 1-3 fields (name, contact, payment channel, government ID, etc.)
- BorrowerRepository follows idempotency pattern with SyncOperationEntity for Firebase Realtime Database sync
- Firebase Realtime Database sync active via FirebaseSyncManager (not Firebase Storage yet)
- Existing UI uses Material3, Jetpack Compose, Hilt DI, and follows the LtPanel/LtButton/LtSyncBadge component library
- AddBorrowerDialog already has expandable optional fields section — document fields fit naturally here
- FileProvider already configured for cache exports, can extend for documents directory
- Coil and Firebase Storage not yet in dependencies — need to add
- No existing document-related code — greenfield feature
- Build system: Gradle 8.9, Kotlin 2.0.21, AGP 8.5.2, compileSdk 35, minSdk 26
- Test framework: JUnit 4 with existing unit tests for loan calculations
