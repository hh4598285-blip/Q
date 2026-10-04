package com.hh.whatsappbridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF25D366),
                    background = androidx.compose.ui.graphics.Color(0xFF08110D),
                    surface = androidx.compose.ui.graphics.Color(0xFF101B16)
                )
            ) {
                WhatsBridgeScreen { number -> openWhatsApp(number) }
            }
        }
    }

    private fun openWhatsApp(raw: String) {
        val number = raw.filter(Char::isDigit)
        if (number.length < 8) {
            Toast.makeText(this, "הזן מספר מלא עם קידומת מדינה", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number"))
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "לא ניתן לפתוח את WhatsApp במכשיר הזה", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun WhatsBridgeScreen(onOpen: (String) -> Unit) {
    var number by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("WhatsBridge", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "גישה מהירה לצ'אט WhatsApp",
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(26.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f)
                )
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text("מספר טלפון", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("972501234567") },
                        supportingText = { Text("כולל קידומת מדינה, ללא + או רווחים") }
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onOpen(number) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("פתח צ'אט ב-WhatsApp", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "הערה: האפליקציה פועלת רק דרך מנגנוני WhatsApp הרשמיים. מספר טלפון לבדו אינו מעניק גישה להודעות או לחשבון של אדם אחר.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Android 10+  •  minSdk 29",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
