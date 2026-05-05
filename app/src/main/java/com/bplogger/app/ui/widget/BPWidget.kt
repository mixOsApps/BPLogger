package com.bplogger.app.ui.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.bplogger.app.MainActivity
import androidx.glance.layout.Column

class BPWidget : GlanceAppWidget() {

    companion object {
        // Day (light) / Night (dark) color providers
        private val ButtonColorProvider = ColorProvider(
            day = Color(0xFFD32F2F),  // BPRed for light
            night = Color(0xFFFBC02D) // BPYellow for dark
        )
        private val TextColorProvider = ColorProvider(
            day = Color.White,          // White text on red
            night = Color(0xFF1A1A1A)   // Dark text on yellow
        )
        private val BackgroundColorProvider = ColorProvider(
            day = Color.Transparent,
            night = Color.Transparent
        )
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Content(context)
        }
    }

    @Composable
    fun Content(context: Context) {
        // Intent to open Add BP Dialog
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.bplogger.app.ACTION_ADD_RECORD"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // Outer Box: fills the entire widget cell, centers the circle
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(BackgroundColorProvider),
            contentAlignment = Alignment.Center
        ) {
            // Circle button — fixed 52x52dp
            Box(
                modifier = GlanceModifier
                    .size(52.dp)
                    .cornerRadius(26.dp)
                    .background(ButtonColorProvider)
                    .clickable(actionStartActivity(intent)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    Text(
                        text = "BP",
                        style = TextStyle(
                            color = TextColorProvider,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    )
                    Text(
                        text = "Logger",
                        style = TextStyle(
                            color = TextColorProvider,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    }
}