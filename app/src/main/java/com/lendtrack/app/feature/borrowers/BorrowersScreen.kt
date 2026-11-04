package com.lendtrack.app.feature.borrowers

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lendtrack.app.core.ui.components.*
import com.lendtrack.app.core.ui.theme.*
import com.lendtrack.app.core.util.DocumentCaptureHelper
import com.lendtrack.app.domain.model.PaymentMethod
import com.lendtrack.app.feature.loans.LtDropdownField
import java.util.Locale

@Composable
fun BorrowersScreen(
    onBorrowerClick: (String) -> Unit,
    onAddLoan:       (String) -> Unit = {},
    viewModel:       BorrowersViewModel = hiltViewModel(),
) {
    val borrowers    by viewModel.filteredBorrowers.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    var query        by remember { mutableStateOf("") }

    LaunchedEffect(query) { viewModel.onSearchQuery(query) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        LtPanel(
            title = "Borrowers",
            action = {
                LtButton(
                    text    = "♡  Add Borrower",
                    onClick = { viewModel.openAddDialog() },
                    icon    = Icons.Filled.PersonAdd,
                    small   = true,
                )
            },
        ) {
            LtSearchField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = "Search by name, contact, or address…",
                modifier      = Modifier.fillMaxWidth(),
                leadingIcon   = Icons.Filled.Search,
            )

            Spacer(Modifier.height(12.dp))

            if (borrowers.isEmpty()) {
                LtEmptyState("No borrowers yet. Add your first borrower to get started.")
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("Name",        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(2f))
                    Text("Contact",     style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1.5f))
                    Text("Prefers",     style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Text("Loans",       style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(0.7f))
                    Text("Outstanding", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Text("Status",      style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(0.8f))
                }
                LtDivider()

                borrowers.forEach { bws ->
                    BorrowerRow(bws = bws, onClick = { onBorrowerClick(bws.borrower.id) })
                    LtDivider()
                }
            }

            Spacer(Modifier.height(8.dp))
            LtNote("Tap a row to view the full borrower profile and loan history.")
        }
    }

    if (viewModel.showAddDialog) {
        AddBorrowerDialog(
            errorMessage = errorMessage,
            onDismiss    = { viewModel.dismissDialog() },
            onSave       = { fields -> viewModel.addBorrowerFull(fields) },
        )
    }
}

@Composable
private fun BorrowerRow(bws: BorrowerWithSummary, onClick: () -> Unit) {
    val statusText = when {
        bws.activeLoanCount == 0 && bws.loanCount == 0 -> "No loans"
        bws.activeLoanCount > 0 && bws.outstandingBalance > 0 -> "Active"
        bws.loanCount > 0 -> "Paid"
        else -> "—"
    }
    val statusColor = when (statusText) {
        "Active" -> LtDusty
        "Paid"   -> LtSage
        else     -> LtMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(bws.borrower.name, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary), modifier = Modifier.weight(2f))
        Text(bws.borrower.contactNumber, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.5f))
        Text(bws.borrower.preferredPaymentChannel.label, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(1f))
        Text(bws.loanCount.toString(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.7f))
        Text(bws.outstandingBalance.peso(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(statusText, style = MaterialTheme.typography.labelSmall.copy(color = statusColor, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(0.8f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Borrower dialog — required fields first, optional behind an expand toggle
// ─────────────────────────────────────────────────────────────────────────────

data class BorrowerFormFields(
    val name:                         String = "",
    val contactNumber:                String = "",
    val preferredPaymentChannel:      PaymentMethod = PaymentMethod.GCASH,
    val secondaryContactName:         String = "",
    val secondaryContactNumber:       String = "",
    val secondaryContactRelationship: String = "",
    val governmentIdType:             String = "",
    val governmentIdNumber:           String = "",
    val address:                      String = "",
    val barangay:                     String = "",
    val occupation:                   String = "",
    val notes:                        String = "",
    val idDocumentUri:                String? = null,
    val billingAddressDocumentUri:    String? = null,
    val idDocumentFirebasePath:       String? = null,
    val billingAddressFirebasePath:   String? = null,
)

@Composable
fun AddBorrowerDialog(
    errorMessage:  String?,
    onDismiss:     () -> Unit,
    onSave:        (BorrowerFormFields) -> Unit,
    initialFields: BorrowerFormFields = BorrowerFormFields(),   // G-9: prefill support
) {
    var fields by remember(initialFields) { mutableStateOf(initialFields) }
    // Pre-expand optional section if editing an existing borrower that has optional data
    var showOptional by remember(initialFields) {
        mutableStateOf(
            initialFields.address.isNotBlank() ||
            initialFields.barangay.isNotBlank() ||
            initialFields.occupation.isNotBlank() ||
            initialFields.notes.isNotBlank() ||
            initialFields.secondaryContactName.isNotBlank() ||
            initialFields.governmentIdType.isNotBlank()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = LtSurface,
        shape            = ModalShape,
        title = { Text("Add Borrower", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── Required fields ────────────────────────────────────────
                LtFormField("Full name *", fields.name) { fields = fields.copy(name = it) }
                LtFormField("Contact number *", fields.contactNumber) { fields = fields.copy(contactNumber = it) }

                // Preferred payment channel
                LtDropdownField(
                    label      = "Preferred payment channel",
                    selected   = fields.preferredPaymentChannel.label,
                    options    = PaymentMethod.values().map { it.label to it.name },
                    onSelected = { _, name -> fields = fields.copy(preferredPaymentChannel = PaymentMethod.valueOf(name)) },
                )

                // ── Optional fields toggle ─────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { showOptional = !showOptional }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text("Additional info (optional)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Icon(
                        if (showOptional) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                if (showOptional) {
                    // Contact reference
                    LtSectionLabel("Contact Reference / Guarantor")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LtFormField("Name", fields.secondaryContactName, modifier = Modifier.weight(1f)) { fields = fields.copy(secondaryContactName = it) }
                        LtFormField("Number", fields.secondaryContactNumber, modifier = Modifier.weight(1f)) { fields = fields.copy(secondaryContactNumber = it) }
                    }
                    LtFormField("Relationship (e.g. Spouse, Parent, Employer)", fields.secondaryContactRelationship) { fields = fields.copy(secondaryContactRelationship = it) }

                    // Government ID
                    LtSectionLabel("Government ID")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LtDropdownField(
                            label      = "ID Type",
                            selected   = fields.governmentIdType.ifBlank { "Select…" },
                            options    = listOf("PhilSys", "SSS", "GSIS", "Driver's License", "Passport", "Voter's ID", "PRC ID", "TIN", "Postal ID", "Other").map { it to it },
                            onSelected = { lbl, _ -> fields = fields.copy(governmentIdType = lbl) },
                            modifier   = Modifier.weight(1f),
                        )
                        LtFormField("ID Number", fields.governmentIdNumber, modifier = Modifier.weight(1f)) { fields = fields.copy(governmentIdNumber = it) }
                    }

                    // Location & occupation
                    LtSectionLabel("Location & Occupation")
                    LtFormField("Address", fields.address) { fields = fields.copy(address = it) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LtFormField("Barangay", fields.barangay, modifier = Modifier.weight(1f)) { fields = fields.copy(barangay = it) }
                        LtFormField("Occupation", fields.occupation, modifier = Modifier.weight(1f)) { fields = fields.copy(occupation = it) }
                    }

                    // Document upload sections
                    LtSectionLabel("Document Upload")
                    DocumentUploadSection(
                        label = "ID Document",
                        documentUri = fields.idDocumentUri,
                        onUriChange = { fields = fields.copy(idDocumentUri = it) },
                        onRemove = { fields = fields.copy(idDocumentUri = null) }
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    DocumentUploadSection(
                        label = "Billing Address Document",
                        documentUri = fields.billingAddressDocumentUri,
                        onUriChange = { fields = fields.copy(billingAddressDocumentUri = it) },
                        onRemove = { fields = fields.copy(billingAddressDocumentUri = null) }
                    )

                    LtFormField("Notes", fields.notes, singleLine = false) { fields = fields.copy(notes = it) }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(errorMessage, color = LtRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            LtButton(text = "Save Borrower", onClick = { onSave(fields) })
        },
        dismissButton = {
            LtButton(text = "Cancel", onClick = onDismiss, ghost = true)
        },
    )
}

@Composable
private fun LtSectionLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
fun LtFormField(
    label:      String,
    value:      String,
    singleLine: Boolean = true,
    modifier:   Modifier = Modifier,
    onChange:   (String) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value         = value,
            onValueChange = onChange,
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = singleLine,
            shape         = InputShape,
            textStyle     = MaterialTheme.typography.bodyMedium,
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = LtRose,
                unfocusedBorderColor    = MaterialTheme.colorScheme.outline,
                focusedContainerColor   = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                cursorColor             = LtRose,
            ),
        )
    }
}

@Composable
private fun DocumentUploadSection(
    label: String,
    documentUri: String?,
    onUriChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val captureHelper = remember { DocumentCaptureHelper(context) }
    
    // Temporary URI for camera capture
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    
    // Permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            // TODO: Show rationale dialog (will be handled in ViewModel in later steps)
        }
    }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            // Image captured to tempCameraUri, it's already compressed
            onUriChange(tempCameraUri.toString())
        }
    }
    
    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                // For now, store the selected URI directly
                // In production, we should compress it via DocumentCaptureHelper
                onUriChange(it.toString())
            } catch (e: Exception) {
                // TODO: Show error
            }
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (documentUri != null) {
                // Show thumbnail preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = Uri.parse(documentUri),
                        contentDescription = label,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    )
                    
                    Spacer(Modifier.weight(1f))
                    
                    LtButton(
                        text = "Remove",
                        onClick = onRemove,
                        ghost = true,
                        small = true,
                        icon = Icons.Filled.Delete
                    )
                }
            } else {
                // Show upload buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LtButton(
                        text = "Camera",
                        onClick = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            // For demo purposes, create a temp file
                            // In production, use proper borrower ID
                            val tempFile = captureHelper.getDocumentFile(
                                borrowerId = "temp",
                                documentType = if (label.contains("ID")) 
                                    DocumentCaptureHelper.ID_DOCUMENT 
                                else 
                                    DocumentCaptureHelper.BILLING_ADDRESS_DOCUMENT,
                                createIfMissing = true
                            )
                            tempCameraUri = Uri.fromFile(tempFile)
                            cameraLauncher.launch(tempCameraUri!!)
                        },
                        ghost = true,
                        small = true,
                        icon = Icons.Filled.CameraAlt,
                        modifier = Modifier.weight(1f)
                    )
                    
                    LtButton(
                        text = "Gallery",
                        onClick = { galleryLauncher.launch("image/*") },
                        ghost = true,
                        small = true,
                        icon = Icons.Filled.PhotoLibrary,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Text(
                    "No document uploaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Double.peso() = "₱" + String.format(Locale("en", "PH"), "%,.2f", this)
