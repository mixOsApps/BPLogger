package com.bplogger.app.ui.trends

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bplogger.app.ViewModelFactory
import com.bplogger.app.ui.theme.*
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(factory: ViewModelFactory) {
    val viewModel: TrendsViewModel = viewModel(factory = factory)
    val records by viewModel.records.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val dateRange by viewModel.dateRange.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val modelProducer = remember { CartesianChartModelProducer() }
    val sortedRecords = records.sortedBy { it.timestamp }

    val accent = MaterialTheme.accentColor

    val hasHighAlert = remember(records, settings) {
        records.takeLast(5).any {
            it.systolic >= (settings?.systolicAlertThreshold ?: 140) ||
                    it.diastolic >= (settings?.diastolicAlertThreshold ?: 90)
        }
    }

    LaunchedEffect(sortedRecords) {
        if (sortedRecords.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                modelProducer.runTransaction {
                    lineSeries {
                        series(sortedRecords.map { it.systolic.toFloat() })
                        series(sortedRecords.map { it.diastolic.toFloat() })
                        series(sortedRecords.map { it.heartRate.toFloat() })
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "BP Trends",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = accent
        )

        // High Alert Banner
        if (hasHighAlert) {
            Card(
                colors = CardDefaults.cardColors(containerColor = BPRed.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, tint = BPRed)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "High Blood Pressure Alert",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BPRed
                        )
                        Text(
                            "Recent readings exceed your alert thresholds. Please consult your doctor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BPRed.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Date Range Presets
        DateRangeSelector(
            currentRange = dateRange,
            onPreset = viewModel::setPreset,
            onCustomRange = viewModel::setDateRange
        )

        // Chart
        if (sortedRecords.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Legend
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendItem(BPRed, "Systolic")
                        LegendItem(BPYellow, "Diastolic")
                        LegendItem(BPGreen, "Heart Rate")
                    }
                    Spacer(Modifier.height(12.dp))

                    // Build line objects (not @Composable, so construct directly)
                    val systolicLine = LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(fill(BPRed))
                    )
                    val diastolicLine = LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(fill(BPYellow))
                    )
                    val heartRateLine = LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(fill(BPGreen))
                    )

                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberLineCartesianLayer(
                                lineProvider = LineCartesianLayer.LineProvider.series(
                                    systolicLine,
                                    diastolicLine,
                                    heartRateLine
                                )
                            ),
                            startAxis = VerticalAxis.rememberStart(),
                            bottomAxis = HorizontalAxis.rememberBottom()
                        ),
                        modelProducer = modelProducer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ShowChart,
                            null,
                            tint = accent.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "No data for selected range",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        // Stats Summary
        stats?.let { s ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Summary (${s.totalRecords} records)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("Avg SYS", "${s.avgSystolic.toInt()}", BPRed)
                        StatItem("Avg DIA", "${s.avgDiastolic.toInt()}", BPYellow)
                        StatItem("Avg HR", "${s.avgHeartRate.toInt()}", BPGreen)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("Max SYS", "${s.maxSystolic}", BPRed.copy(alpha = 0.7f))
                        StatItem("Min SYS", "${s.minSystolic}", BPGreen.copy(alpha = 0.7f))
                        StatItem("High Alerts", "${s.highAlertCount}", BPRed)
                    }
                    if (s.avgWeight != null || s.avgSpO2 != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            s.avgWeight?.let { StatItem("Avg Weight", "${"%.1f".format(it)} kg", accent) }
                            s.avgSpO2?.let { StatItem("Avg SpO2", "${it.toInt()}%", accent) }
                        }
                    }
                }
            }
        }

        // Alert threshold info
        settings?.let { s ->
            Card(
                colors = CardDefaults.cardColors(containerColor = BPYellow.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.NotificationsActive, null, tint = BPYellow)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Alert thresholds: SYS \u2265 ${s.systolicAlertThreshold} or DIA \u2265 ${s.diastolicAlertThreshold} mmHg. Configure in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(12.dp),
            shape = CircleShape,
            color = color
        ) {}
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeSelector(
    currentRange: DateRange,
    onPreset: (Int) -> Unit,
    onCustomRange: (Long, Long) -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yy", Locale.getDefault())
    val accent = MaterialTheme.accentColor

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Date Range",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to "7D", 30 to "30D", 90 to "90D", 365 to "1Y").forEach { (days, label) ->
                    FilterChip(
                        selected = false,
                        onClick = { onPreset(days) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = accent.copy(alpha = 0.1f),
                            labelColor = accent
                        )
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${dateFormat.format(Date(currentRange.startMs))} \u2192 ${dateFormat.format(Date(currentRange.endMs))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}