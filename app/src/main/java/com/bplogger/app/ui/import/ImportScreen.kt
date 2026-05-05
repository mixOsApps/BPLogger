package com.bplogger.app.ui.import

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.DatePicker
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bplogger.app.ViewModelFactory
import com.bplogger.app.util.OcrBpRow
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.bplogger.app.util.parseOcrVisionText
import com.google.mlkit.vision.text.Text
import com.bplogger.app.util.parseOcrText


@Composable
fun ImportScreen(factory: ViewModelFactory) {
    val viewModel: ImportViewModel = viewModel(factory = factory)
    var manualImport by remember { mutableStateOf(false) }
    var cameraImport by remember { mutableStateOf(false) }
    var showCameraReview by remember { mutableStateOf(false) }
    var recognizedRecords by remember { mutableStateOf<List<BloodPressureRecord>>(emptyList()) }
    var ocrRows by remember { mutableStateOf<List<OcrBpRow>>(emptyList()) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!manualImport && !cameraImport && !showCameraReview) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { manualImport = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Manual Import")
                    Text("Manual Import")
                }
                Button(onClick = { cameraImport = true }) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Camera Import")
                    Text("Camera Import")
                }
            }
        } else if (manualImport) {
            ManualImport(
                initialRecords = recognizedRecords,
                onSave = {
                    viewModel.saveRecords(it)
                    Toast.makeText(context, "Records saved", Toast.LENGTH_SHORT).show()
                    manualImport = false
                    cameraImport = false
                    recognizedRecords = emptyList()
                }
            )
        } else if (showCameraReview) {
            CameraReviewScreen(
                ocrRows = ocrRows,
                viewModel = viewModel,
                onDone = {
                    showCameraReview = false
                    ocrRows = emptyList()
                },
                onCancel = {
                    showCameraReview = false
                    ocrRows = emptyList()
                }
            )
        } else {
            CameraImport { rows ->
                ocrRows = rows
                cameraImport = false
                showCameraReview = true
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Camera Review Screen — Year Prompt → Editable Rows → Save
// ═══════════════════════════════════════════════════════════════

@Composable
fun CameraReviewScreen(
    ocrRows: List<OcrBpRow>,
    viewModel: ImportViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    var showYearDialog by remember { mutableStateOf(true) }
    var selectedYear by remember { mutableIntStateOf(currentYear) }
    var rows by remember { mutableStateOf<List<CameraImportRow>>(emptyList()) }
    var isSaving by remember { mutableStateOf(false) }

    // Year Selection Dialog — shown immediately
    if (showYearDialog) {
        YearPickerDialog(
            currentYear = currentYear,
            onYearSelected = { year ->
                selectedYear = year
                // Convert OcrBpRows → CameraImportRows with the selected year
                rows = ocrRows.map { ocr ->
                    CameraImportRow(
                        date = ocr.date,
                        time = ocr.time,
                        systolic = ocr.systolic,
                        diastolic = ocr.diastolic,
                        pulse = ocr.pulse,
                        year = year.toString()
                    )
                }
                showYearDialog = false
            },
            onCancel = onCancel
        )
        return
    }

    if (rows.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No valid records found from capture.", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCancel) {
                Text("Back")
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Text(
            text = "Review Captured Records (${rows.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Year: $selectedYear — Edit fields as needed, then Save.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Column headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Date", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            Text("Time", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            Text("Sys", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            Text("Dia", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            Text("Pulse", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.size(36.dp)) // space for X button
        }

        HorizontalDivider()

        // Editable rows
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            itemsIndexed(rows, key = { index, _ -> index }) { index, row ->
                CameraRecordRow(
                    row = row,
                    onRowChange = { updatedRow ->
                        rows = rows.toMutableList().apply { this[index] = updatedRow }
                    },
                    onRemove = {
                        rows = rows.toMutableList().apply { removeAt(index) }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Save / Cancel buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                enabled = !isSaving
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    if (rows.isEmpty()) {
                        Toast.makeText(context, "No records to save", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    // Validate all rows
                    val hasEmpty = rows.any {
                        it.date.isBlank() || it.time.isBlank() ||
                                it.systolic.isBlank() || it.diastolic.isBlank() || it.pulse.isBlank()
                    }
                    if (hasEmpty) {
                        Toast.makeText(context, "All fields are required for each row", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSaving = true
                    viewModel.saveCameraRecords(rows) { success, message ->
                        isSaving = false
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        if (success) onDone()
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.Default.Save, contentDescription = "Save")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save (${rows.size})")
            }
        }
    }
}

@Composable
fun CameraRecordRow(
    row: CameraImportRow,
    onRowChange: (CameraImportRow) -> Unit,
    onRemove: () -> Unit
) {
    val compactTextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date field (MMdd)
        OutlinedTextField(
            value = row.date,
            onValueChange = { if (it.length <= 4) onRowChange(row.copy(date = it.filter { c -> c.isDigit() })) },
            placeholder = { Text("MMdd", fontSize = 10.sp) },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = compactTextStyle
        )

        // Time field (HHmm)
        OutlinedTextField(
            value = row.time,
            onValueChange = { if (it.length <= 4) onRowChange(row.copy(time = it.filter { c -> c.isDigit() })) },
            placeholder = { Text("HHmm", fontSize = 10.sp) },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = compactTextStyle
        )

        // Systolic
        OutlinedTextField(
            value = row.systolic,
            onValueChange = { if (it.length <= 3) onRowChange(row.copy(systolic = it.filter { c -> c.isDigit() })) },
            placeholder = { Text("Sys", fontSize = 10.sp) },
            modifier = Modifier
                .weight(0.8f)
                .height(48.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = compactTextStyle
        )

        // Diastolic
        OutlinedTextField(
            value = row.diastolic,
            onValueChange = { if (it.length <= 3) onRowChange(row.copy(diastolic = it.filter { c -> c.isDigit() })) },
            placeholder = { Text("Dia", fontSize = 10.sp) },
            modifier = Modifier
                .weight(0.8f)
                .height(48.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = compactTextStyle
        )

        // Pulse
        OutlinedTextField(
            value = row.pulse,
            onValueChange = { if (it.length <= 3) onRowChange(row.copy(pulse = it.filter { c -> c.isDigit() })) },
            placeholder = { Text("Pul", fontSize = 10.sp) },
            modifier = Modifier
                .weight(0.8f)
                .height(48.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = compactTextStyle
        )

        // X Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove row",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Year Picker Dialog
// ═══════════════════════════════════════════════════════════════

@Composable
fun YearPickerDialog(
    currentYear: Int,
    onYearSelected: (Int) -> Unit,
    onCancel: () -> Unit
) {
    var selectedYear by remember { mutableIntStateOf(currentYear) }
    val yearOptions = ((currentYear - 5)..currentYear).toList().reversed()

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text("Set Year for Captured Records")
        },
        text = {
            Column {
                Text(
                    "All records in this batch will use the selected year.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Year selection buttons
                yearOptions.forEach { year ->
                    val isSelected = year == selectedYear
                    Button(
                        onClick = { selectedYear = year },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        colors = if (isSelected) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    ) {
                        Text(
                            text = year.toString(),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onYearSelected(selectedYear) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════
// Manual Import (existing — unchanged logic)
// ═══════════════════════════════════════════════════════════════

@Composable
fun ManualImport(
    initialRecords: List<BloodPressureRecord> = emptyList(),
    onSave: (List<BloodPressureRecord>) -> Unit
) {
    val defaultRecord = BloodPressureRecord(
        dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
        systolic = "",
        diastolic = "",
        pulse = ""
    )
    var records by remember { mutableStateOf(initialRecords.ifEmpty { listOf(defaultRecord) }) }
    var errors by remember { mutableStateOf<Map<Int, Set<String>>>(emptyMap()) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        records.forEachIndexed { index, record ->
            RecordInputRow(
                record = record,
                onRecordChange = { updatedRecord ->
                    val newRecords = records.toMutableList()
                    newRecords[index] = updatedRecord
                    records = newRecords

                    val recordErrors = errors[index]?.toMutableSet() ?: mutableSetOf()
                    if (updatedRecord.systolic.isNotBlank()) recordErrors.remove("systolic")
                    if (updatedRecord.diastolic.isNotBlank()) recordErrors.remove("diastolic")
                    if (updatedRecord.pulse.isNotBlank()) recordErrors.remove("pulse")
                    errors = errors + (index to recordErrors)
                },
                errors = errors.getOrElse(index) { emptySet() }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val newErrors = mutableMapOf<Int, MutableSet<String>>()
                    var hasErrors = false
                    records.forEachIndexed { index, record ->
                        val recordErrors = mutableSetOf<String>()
                        if (record.systolic.isBlank()) {
                            recordErrors.add("systolic")
                            hasErrors = true
                        }
                        if (record.diastolic.isBlank()) {
                            recordErrors.add("diastolic")
                            hasErrors = true
                        }
                        if (record.pulse.isBlank()) {
                            recordErrors.add("pulse")
                            hasErrors = true
                        }

                        if (recordErrors.isNotEmpty()) {
                            newErrors[index] = recordErrors
                        }
                    }
                    errors = newErrors
                    if (!hasErrors) {
                        onSave(records)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save records")
                Text("Save")
            }
            if (records.size < 10) {
                Button(
                    onClick = { records = records + defaultRecord },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add another record")
                    Text("Add")
                }
            }
        }
    }
}

@Composable
fun RecordInputRow(
    record: BloodPressureRecord,
    onRecordChange: (BloodPressureRecord) -> Unit,
    errors: Set<String>
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        calendar.time = sdf.parse(record.dateTime) ?: Date()
    } catch (_: Exception) {
    }

    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            calendar.set(year, month, dayOfMonth)
            val timePickerDialog = TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    calendar.set(Calendar.MINUTE, minute)
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    val newDateTime = sdf.format(calendar.time)
                    onRecordChange(record.copy(dateTime = newDateTime))
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false
            )
            timePickerDialog.show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier
            .weight(2.5f)
            .clickable { datePickerDialog.show() }) {
            OutlinedTextField(
                value = record.dateTime,
                onValueChange = { },
                label = { Text("Date Time") },
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        OutlinedTextField(
            value = record.systolic,
            onValueChange = { onRecordChange(record.copy(systolic = it)) },
            label = { Text("Sys") },
            modifier = Modifier.weight(1f),
            isError = "systolic" in errors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = record.diastolic,
            onValueChange = { onRecordChange(record.copy(diastolic = it)) },
            label = { Text("Dia") },
            modifier = Modifier.weight(1f),
            isError = "diastolic" in errors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = record.pulse,
            onValueChange = { onRecordChange(record.copy(pulse = it)) },
            label = { Text("Pul") },
            modifier = Modifier.weight(1f),
            isError = "pulse" in errors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Camera Capture (CameraX + ML Kit OCR)
// ═══════════════════════════════════════════════════════════════
@Composable
fun CameraImport(onTextRecognized: (List<OcrBpRow>) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var hasCamPermission by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCamPermission = granted
        }
    )
    LaunchedEffect(key1 = true) {
        launcher.launch(Manifest.permission.CAMERA)
    }

    val imageCapture = remember { ImageCapture.Builder().build() }

    if (hasCamPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Instruction text
            Text(
                text = "Capture printed BP records",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )

            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = {
                    if (!isProcessing) {
                        isProcessing = true
                        takePictureWithVision(context, imageCapture) { visionText ->
                            if (visionText != null) {
                                Log.d("CameraImport", "OCR Raw Text:\n${visionText.text}")
                                val rows = parseOcrVisionText(visionText)
                                Log.d("CameraImport", "Parsed ${rows.size} rows")
                                isProcessing = false
                                if (rows.isEmpty()) {
                                    Toast.makeText(context, "No records found. Try again.", Toast.LENGTH_SHORT).show()
                                } else {
                                    onTextRecognized(rows)
                                }
                            } else {
                                isProcessing = false
                                Toast.makeText(context, "Failed to capture. Try again.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                enabled = !isProcessing
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "Capture")
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isProcessing) "Processing..." else "Capture")
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Camera permission is required to scan records.")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant Permission")
            }
        }
    }
}
// ═══════════════════════════════════════════════════════════════
// Take Picture + ML Kit OCR
// ═══════════════════════════════════════════════════════════════

private fun takePictureWithVision(
    context: Context,
    imageCapture: ImageCapture,
    onResult: (Text?) -> Unit
) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val photoFile = File(
        context.cacheDir,
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis()) + ".jpg"
    )
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val uri = output.savedUri ?: Uri.fromFile(photoFile)
                    val image = InputImage.fromFilePath(context, uri)
                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            onResult(visionText)
                            photoFile.delete()
                        }
                        .addOnFailureListener { e ->
                            Log.e("CameraImport", "Text recognition failed", e)
                            onResult(null)
                            photoFile.delete()
                        }
                } catch (e: Exception) {
                    Log.e("CameraImport", "Failed to process image", e)
                    onResult(null)
                    photoFile.delete()
                }
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraImport", "Photo capture failed: ${exc.message}", exc)
                onResult(null)
            }
        }
    )
}