package com.hh.whatsappbridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WhatsBridgeScreen { number ->
                        val normalized = number.filter { it.isDigit() }
                        if (normalized.isBlank()) {
                            Toast.makeText(this, "הזן מספר טלפון", Toast.LENGTH_SHORT).show()
                            return@WhatsBridgeScreen
                        }
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$normalized")))
                        } catch (_: Exception) {
                            Toast.makeText(this, "WhatsApp אינו מותקן או שהקישור אינו זמין", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WhatsBridgeScreen(onConnect: (String) -> Unit) {
    var number by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("WhatsBridge", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "חיבור מהיר לחשבון WhatsApp שלך",
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("מספר טלפון", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("972501234567") }
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onConnect(number) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("פתח WhatsApp", fontSize = 16.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "המספר משמש לפתיחת WhatsApp בלבד. האפליקציה אינה עוקפת אימות ואינה מקבלת גישה להודעות של חשבון אחר.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
