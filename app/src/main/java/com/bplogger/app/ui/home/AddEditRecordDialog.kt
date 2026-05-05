package com.bplogger.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bplogger.app.data.db.BpRecord
import com.bplogger.app.ui.theme.accentColor
import com.bplogger.app.ui.theme.BPYellow
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRecordDialog(
    existingRecord: BpRecord? = null,
    onDismiss: () -> Unit,
    onConfirm: (BpRecord) -> Unit
) {
    val isEditing = existingRecord != null
    val now = existingRecord?.timestamp ?: System.currentTimeMillis()

    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    var systolic by remember { mutableStateOf(existingRecord?.systolic?.toString() ?: "") }
    var diastolic by remember { mutableStateOf(existingRecord?.diastolic?.toString() ?: "") }
    var heartRate by remember { mutableStateOf(existingRecord?.heartRate?.toString() ?: "") }
    var weight by remember { mutableStateOf(existingRecord?.weight?.toString() ?: "") }
    var spO2 by remember { mutableStateOf(existingRecord?.spO2?.toString() ?: "") }
    var notes by remember { mutableStateOf(existingRecord?.notes ?: "") }
    var dateText by remember { mutableStateOf(dateFormat.format(Date(now))) }
    var timeText by remember { mutableStateOf(timeFormat.format(Date(now))) }

    var systolicError by remember { mutableStateOf(false) }
    var diastolicError by remember { mutableStateOf(false) }
    var heartRateError by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        systolicError = systolic.toIntOrNull() == null
        diastolicError = diastolic.toIntOrNull() == null
        heartRateError = heartRate.toIntOrNull() == null
        return !systolicError && !diastolicError && !heartRateError
    }

    fun buildTimestamp(): Long {
        return try {
            val combined = "$dateText ${timeText}"
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).parse(combined)?.time
                ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Record" else "Add BP Record",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.accentColor
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Date + Time row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("Date") },
                        placeholder = { Text("dd/MM/yyyy") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = { Text("Time") },
                        placeholder = { Text("HH:mm") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Systolic / Diastolic row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = systolic,
                        onValueChange = { systolic = it; systolicError = false },
                        label = { Text("Systolic") },
                        placeholder = { Text("mmHg") },
                        isError = systolicError,
                        supportingText = if (systolicError) {{ Text("Required") }} else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = diastolic,
                        onValueChange = { diastolic = it; diastolicError = false },
                        label = { Text("Diastolic") },
                        placeholder = { Text("mmHg") },
                        isError = diastolicError,
                        supportingText = if (diastolicError) {{ Text("Required") }} else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Heart Rate
                OutlinedTextField(
                    value = heartRate,
                    onValueChange = { heartRate = it; heartRateError = false },
                    label = { Text("Heart Rate") },
                    placeholder = { Text("bpm") },
                    isError = heartRateError,
                    supportingText = if (heartRateError) {{ Text("Required") }} else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Weight / SpO2 row (optional)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Weight") },
                        placeholder = { Text("kg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = spO2,
                        onValueChange = { spO2 = it },
                        label = { Text("SpO2") },
                        placeholder = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validate()) {
                        val record = BpRecord(
                            id = existingRecord?.id ?: java.util.UUID.randomUUID().toString(),
                            systolic = systolic.toInt(),
                            diastolic = diastolic.toInt(),
                            heartRate = heartRate.toInt(),
                            notes = notes,
                            timestamp = buildTimestamp(),
                            syncedAt = null,
                            source = existingRecord?.source ?: "manual",
                            weight = weight.toFloatOrNull(),
                            spO2 = spO2.toIntOrNull()
                        )
                        onConfirm(record)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.accentColor)
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
