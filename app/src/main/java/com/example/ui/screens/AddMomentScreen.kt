package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.HyperlinkUtils
import com.example.data.model.MomentCategory
import com.example.data.model.MomentHyperlink
import com.example.ui.components.SectionHeader
import com.example.ui.components.Spacing
import com.example.ui.components.TravelPrimaryButton
import com.example.ui.theme.Terracotta
import com.example.ui.util.PhotoUtils
import com.example.ui.viewmodel.TravelViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddMomentScreen(
    tripId: Long,
    viewModel: TravelViewModel,
    onNavigateBack: () -> Unit,
    momentId: Long? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isEditMode = momentId != null && momentId > 0

    // State preservation across configuration changes & process recreation
    var selectedCategoryName by rememberSaveable { mutableStateOf(MomentCategory.PHOTO.name) }
    var noteTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var rawHyperlinksJson by rememberSaveable { mutableStateOf("[]") }
    var attachedImageUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var tempCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraPermissionDeniedNotice by rememberSaveable { mutableStateOf(false) }
    var isPermanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var isCameraLaunching by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isInitialDataLoaded by rememberSaveable { mutableStateOf(false) }

    // Hyperlink Dialog State
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkDialogStart by remember { mutableStateOf(0) }
    var linkDialogEnd by remember { mutableStateOf(0) }
    var linkDialogSelectedText by remember { mutableStateOf("") }
    var linkDialogUrlInput by remember { mutableStateOf("") }
    var linkDialogUrlError by remember { mutableStateOf<String?>(null) }
    var isEditingExistingLink by remember { mutableStateOf(false) }

    val hyperlinks = remember(rawHyperlinksJson) {
        HyperlinkUtils.parseFromJson(rawHyperlinksJson)
    }

    fun updateHyperlinks(newLinks: List<MomentHyperlink>) {
        rawHyperlinksJson = HyperlinkUtils.serializeToJson(newLinks)
    }

    // Load initial data if editing an existing moment
    LaunchedEffect(momentId) {
        if (isEditMode && !isInitialDataLoaded) {
            val existing = viewModel.getMomentByIdSync(momentId!!)
            if (existing != null) {
                selectedCategoryName = existing.category.name
                noteTextFieldValue = TextFieldValue(
                    text = existing.note,
                    selection = TextRange(existing.note.length)
                )
                updateHyperlinks(existing.hyperlinks)
                attachedImageUriString = existing.imageUri
            }
            isInitialDataLoaded = true
        }
    }

    val selectedCategory = remember(selectedCategoryName) {
        MomentCategory.fromName(selectedCategoryName)
    }

    // Camera Capture Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        isCameraLaunching = false
        val uriStr = tempCameraUriString
        if (success && !uriStr.isNullOrBlank()) {
            val cameraUri = Uri.parse(uriStr)
            val permanentPath = PhotoUtils.copyUriToPermanentStorage(context, cameraUri)
            if (permanentPath != null) {
                attachedImageUriString = permanentPath
                errorMessage = null
            } else {
                errorMessage = "Could not process captured photo. Please try again."
            }
        } else {
            PhotoUtils.cleanUpTempFile(context, uriStr)
        }
    }

    fun triggerCamera() {
        if (isCameraLaunching) return
        isCameraLaunching = true
        errorMessage = null
        try {
            val uri = PhotoUtils.createCameraTempUri(context)
            tempCameraUriString = uri.toString()
            cameraLauncher.launch(uri)
        } catch (_: Exception) {
            isCameraLaunching = false
            errorMessage = "Camera application is unavailable. You can choose a photo from your gallery."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraPermissionDeniedNotice = false
            isPermanentlyDenied = false
            triggerCamera()
        } else {
            cameraPermissionDeniedNotice = true
            isCameraLaunching = false
            val activity = context as? Activity
            val shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: true
            isPermanentlyDenied = !shouldShowRationale
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val permanentPath = PhotoUtils.copyUriToPermanentStorage(context, uri)
            attachedImageUriString = permanentPath ?: uri.toString()
            errorMessage = null
        }
    }

    // Handler when user clicks the "🔗 Link" button
    fun onLinkActionClick() {
        val currentText = noteTextFieldValue.text
        val selection = noteTextFieldValue.selection

        if (selection.collapsed) {
            // Check if cursor is placed inside an existing link
            val cursor = selection.start
            val existingLink = hyperlinks.firstOrNull { cursor in it.startIndex..it.endIndex }
            if (existingLink != null) {
                val clampedStart = existingLink.startIndex.coerceIn(0, currentText.length)
                val clampedEnd = existingLink.endIndex.coerceIn(0, currentText.length)
                linkDialogStart = clampedStart
                linkDialogEnd = clampedEnd
                linkDialogSelectedText = currentText.substring(clampedStart, clampedEnd)
                linkDialogUrlInput = existingLink.url
                linkDialogUrlError = null
                isEditingExistingLink = true
                showLinkDialog = true
            } else {
                errorMessage = "Select the text you want to attach a link to."
            }
        } else {
            val start = selection.min.coerceIn(0, currentText.length)
            val end = selection.max.coerceIn(0, currentText.length)
            val selectedText = currentText.substring(start, end).trim()

            if (selectedText.isEmpty()) {
                errorMessage = "Selected text cannot be empty or whitespace only."
                return
            }

            // Check if this exact range or an overlapping range already has a link
            val existingLink = hyperlinks.firstOrNull { it.startIndex < end && it.endIndex > start }
            if (existingLink != null) {
                linkDialogStart = start
                linkDialogEnd = end
                linkDialogSelectedText = currentText.substring(start, end)
                linkDialogUrlInput = existingLink.url
                linkDialogUrlError = null
                isEditingExistingLink = true
            } else {
                linkDialogStart = start
                linkDialogEnd = end
                linkDialogSelectedText = currentText.substring(start, end)
                linkDialogUrlInput = ""
                linkDialogUrlError = null
                isEditingExistingLink = false
            }
            errorMessage = null
            showLinkDialog = true
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "EDIT MOMENT" else "ADD MOMENT",
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("add_moment_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            // 1. Moment Type Category Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        SectionHeader(title = "Moment Category", emoji = "🏷️")

                        val categories = MomentCategory.entries
                        for (i in categories.indices step 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                CategoryOptionCard(
                                    category = categories[i],
                                    isSelected = selectedCategory == categories[i],
                                    onClick = { selectedCategoryName = categories[i].name },
                                    modifier = Modifier.weight(1f)
                                )
                                if (i + 1 < categories.size) {
                                    CategoryOptionCard(
                                        category = categories[i + 1],
                                        isSelected = selectedCategory == categories[i + 1],
                                        onClick = { selectedCategoryName = categories[i + 1].name },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // 2. Photo Attachment Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        SectionHeader(title = "Expedition Photo", emoji = "📸")

                        if (attachedImageUriString != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(attachedImageUriString)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Selected photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                IconButton(
                                    onClick = { attachedImageUriString = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(Spacing.sm)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(enabled = !isCameraLaunching) {
                                            val hasPermission = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED

                                            if (hasPermission) {
                                                cameraPermissionDeniedNotice = false
                                                isPermanentlyDenied = false
                                                triggerCamera()
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        }
                                        .testTag("take_photo_button")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(Spacing.lg),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                        Spacer(modifier = Modifier.height(Spacing.xs))
                                        Text(
                                            text = "TAKE PHOTO",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            letterSpacing = 0.6.sp
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                        .testTag("choose_gallery_button")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(Spacing.lg),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            tint = Terracotta,
                                            modifier = Modifier.size(26.dp)
                                        )
                                        Spacer(modifier = Modifier.height(Spacing.xs))
                                        Text(
                                            text = "CHOOSE GALLERY",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            letterSpacing = 0.6.sp
                                        )
                                    }
                                }
                            }

                            if (cameraPermissionDeniedNotice) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(Spacing.md),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(Spacing.sm))
                                            Text(
                                                text = if (isPermanentlyDenied) {
                                                    "Camera permission is disabled in system settings. Enable it to take photos directly."
                                                } else {
                                                    "Camera permission is required to capture photos directly."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            if (isPermanentlyDenied) {
                                                OutlinedButton(
                                                    onClick = {
                                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                            data = Uri.fromParts("package", context.packageName, null)
                                                        }
                                                        context.startActivity(intent)
                                                    },
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Settings,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Open Settings", fontSize = 12.sp)
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary
                                                    )
                                                ) {
                                                    Text("Allow Camera", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Field: Note Text with Hyperlink Toolbar & Attached Link Chips
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader(title = "Moment Note", emoji = "📝")

                            // Hyperlink action button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .clickable { onLinkActionClick() }
                                    .testTag("toolbar_link_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = "Attach web link to selected text",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "LINK",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.6.sp
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = noteTextFieldValue,
                            onValueChange = { newValue ->
                                val oldText = noteTextFieldValue.text
                                val newText = newValue.text

                                // Range integrity: automatically adjust hyperlink spans when typing or editing
                                if (oldText != newText && hyperlinks.isNotEmpty()) {
                                    val adjusted = HyperlinkUtils.adjustHyperlinksOnTextChange(
                                        oldText = oldText,
                                        newText = newText,
                                        existingLinks = hyperlinks
                                    )
                                    updateHyperlinks(adjusted)
                                }

                                noteTextFieldValue = newValue
                                errorMessage = null
                            },
                            placeholder = { Text("e.g. Reached the stone ridge stairs right as morning fog lifted. Coordinates on Google Maps.") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("moment_note_input"),
                            minLines = 3,
                            maxLines = 8,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        // Attached Hyperlinks Chips list
                        if (hyperlinks.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "ATTACHED LINKS (${hyperlinks.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.5.sp
                                )

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    hyperlinks.forEachIndexed { index, link ->
                                        val clampedStart = link.startIndex.coerceIn(0, noteTextFieldValue.text.length)
                                        val clampedEnd = link.endIndex.coerceIn(0, noteTextFieldValue.text.length)
                                        val labelText = if (clampedStart < clampedEnd) {
                                            noteTextFieldValue.text.substring(clampedStart, clampedEnd)
                                        } else {
                                            "Link #${index + 1}"
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier
                                                .clickable {
                                                    linkDialogStart = clampedStart
                                                    linkDialogEnd = clampedEnd
                                                    linkDialogSelectedText = labelText
                                                    linkDialogUrlInput = link.url
                                                    linkDialogUrlError = null
                                                    isEditingExistingLink = true
                                                    showLinkDialog = true
                                                }
                                                .testTag("hyperlink_chip_$index")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Link,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = labelText,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 140.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = {
                                                        val updated = hyperlinks.filterIndexed { i, _ -> i != index }
                                                        updateHyperlinks(updated)
                                                    },
                                                    modifier = Modifier.size(20.dp).testTag("delete_hyperlink_chip_$index")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove link",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Error Banner if validation fails
            if (errorMessage != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("add_moment_error_banner"),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        )
                    }
                }
            }

            // Primary Action: SAVE / UPDATE MOMENT
            item {
                TravelPrimaryButton(
                    text = if (isEditMode) "UPDATE MOMENT" else "SAVE MOMENT",
                    onClick = {
                        val fullNoteText = noteTextFieldValue.text.trim()
                        if (fullNoteText.isBlank() && attachedImageUriString.isNullOrBlank()) {
                            errorMessage = "Please enter a note or attach a photo."
                            return@TravelPrimaryButton
                        }

                        val cleanedSpans = HyperlinkUtils.cleanupAndDeduplicateSpans(
                            hyperlinks,
                            fullNoteText.length
                        )

                        if (isEditMode) {
                            viewModel.updateMoment(
                                momentId = momentId!!,
                                tripId = tripId,
                                category = selectedCategory,
                                note = fullNoteText,
                                hyperlinks = cleanedSpans,
                                imageUri = attachedImageUriString,
                                onSaved = { onNavigateBack() }
                            )
                        } else {
                            viewModel.addMoment(
                                tripId = tripId,
                                category = selectedCategory,
                                note = fullNoteText,
                                hyperlinks = cleanedSpans,
                                imageUri = attachedImageUriString,
                                onSaved = { onNavigateBack() }
                            )
                        }
                    },
                    testTag = "submit_add_moment_button"
                )
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.lg))
            }
        }
    }

    // Add / Edit / Remove Link Dialog
    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = {
                Text(
                    text = if (isEditingExistingLink) "Edit Hyperlink" else "Add Hyperlink",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // Selected text being linked
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(Spacing.sm)) {
                            Text(
                                text = "TEXT TO LINK",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = linkDialogSelectedText.ifBlank { "Selected text" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedTextField(
                        value = linkDialogUrlInput,
                        onValueChange = {
                            linkDialogUrlInput = it
                            linkDialogUrlError = null
                        },
                        label = { Text("Web URL (HTTP / HTTPS)") },
                        placeholder = { Text("https://maps.app.goo.gl/...") },
                        isError = linkDialogUrlError != null,
                        supportingText = {
                            if (linkDialogUrlError != null) {
                                Text(
                                    text = linkDialogUrlError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text(
                                    text = "Supported: http:// and https:// links",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            autoCorrectEnabled = false
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hyperlink_url_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val normalized = HyperlinkUtils.normalizeUrl(linkDialogUrlInput)
                        if (normalized == null) {
                            linkDialogUrlError = "Enter a valid web link (http:// or https://)."
                            return@Button
                        }

                        val newLink = MomentHyperlink(
                            startIndex = linkDialogStart,
                            endIndex = linkDialogEnd,
                            url = normalized
                        )

                        // Filter out any existing spans that overlap with this range
                        val remaining = hyperlinks.filterNot { existing ->
                            existing.startIndex < linkDialogEnd && existing.endIndex > linkDialogStart
                        }

                        val updated = (remaining + newLink).sortedBy { it.startIndex }
                        updateHyperlinks(updated)
                        showLinkDialog = false
                    },
                    modifier = Modifier.testTag("save_hyperlink_button")
                ) {
                    Text(if (isEditingExistingLink) "Update Link" else "Attach Link")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isEditingExistingLink) {
                        TextButton(
                            onClick = {
                                // Remove hyperlink metadata without deleting the visible text
                                val updated = hyperlinks.filterNot { existing ->
                                    existing.startIndex < linkDialogEnd && existing.endIndex > linkDialogStart
                                }
                                updateHyperlinks(updated)
                                showLinkDialog = false
                            },
                            modifier = Modifier.testTag("remove_hyperlink_button")
                        ) {
                            Text("Remove Link", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(
                        onClick = { showLinkDialog = false },
                        modifier = Modifier.testTag("cancel_hyperlink_button")
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
private fun CategoryOptionCard(
    category: MomentCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = category.emoji, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = category.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
