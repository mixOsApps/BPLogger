package com.bplogger.app.ui.medication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bplogger.app.data.db.Medication
import com.bplogger.app.ui.theme.accentColor
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMedicationDialog(
    existingMedication: Medication? = null,
    onDismiss: () -> Unit,
    onConfirm: (Medication) -> Unit
) {
    val isEditing = existingMedication != null
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    var name by remember { mutableStateOf(existingMedication?.name ?: "") }
    var dosage by remember { mutableStateOf(existingMedication?.dosage ?: "") }
    var frequency by remember { mutableStateOf(existingMedication?.frequency ?: "daily") }
    var startDateText by remember {
        mutableStateOf(dateFormat.format(Date(existingMedication?.startDate ?: System.currentTimeMillis())))
    }
    var endDateText by remember {
        mutableStateOf(existingMedication?.endDate?.let { dateFormat.format(Date(it)) } ?: "")
    }
    var notes by remember { mutableStateOf(existingMedication?.notes ?: "") }

    var nameError by remember { mutableStateOf(false) }
    var dosageError by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        nameError = name.isBlank()
        dosageError = dosage.isBlank()
        return !nameError && !dosageError
    }

    fun parseDate(text: String): Long? {
        return try {
            dateFormat.parse(text)?.time
        } catch (e: Exception) {
            null
        }
    }

    val accent = MaterialTheme.accentColor

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Medication" else "Add Medication",
                style = MaterialTheme.typography.titleLarge,
                color = accent
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Medication Name") },
                    placeholder = { Text("e.g. Amlodipine") },
                    isError = nameError,
                    supportingText = if (nameError) {{ Text("Required") }} else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it; dosageError = false },
                    label = { Text("Dosage") },
                    placeholder = { Text("e.g. 5mg") },
                    isError = dosageError,
                    supportingText = if (dosageError) {{ Text("Required") }} else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Frequency selector
                Text(
                    "Frequency",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Medication.FREQUENCIES.forEach { (code, label) ->
                        FilterChip(
                            selected = frequency == code,
                            onClick = { frequency = code },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.15f),
                                selectedLabelColor = accent
                            )
                        )
                    }
                }

                // Start / End date row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = startDateText,
                        onValueChange = { startDateText = it },
                        label = { Text("Start Date") },
                        placeholder = { Text("dd/MM/yyyy") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endDateText,
                        onValueChange = { endDateText = it },
                        label = { Text("End Date") },
                        placeholder = { Text("Optional") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

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
                        val medication = Medication(
                            id = existingMedication?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            dosage = dosage.trim(),
                            frequency = frequency,
                            startDate = parseDate(startDateText) ?: System.currentTimeMillis(),
                            endDate = if (endDateText.isNotBlank()) parseDate(endDateText) else null,
                            notes = notes.trim(),
                            isActive = existingMedication?.isActive ?: true
                        )
                        onConfirm(medication)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
