package com.hh.whatsappbridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

private const val KEY_ALIAS = "whatsbridge_local_account_key"
private const val PREFS = "whatsbridge_secure"
private const val PHONE = "encrypted_phone"
private const val IV = "encrypted_phone_iv"

class MainActivity : ComponentActivity() {

    private val secureStore by lazy { SecureAccountStore(this) }

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
                WhatsBridgeScreen(
                    savedNumber = secureStore.getPhone(),
                    onSave = { saveAccount(it) },
                    onOpen = { openWhatsApp(it) },
                    onClear = {
                        secureStore.clear()
                    }
                )
            }
        }
    }

    private fun saveAccount(raw: String) {
        val number = raw.filter(Char::isDigit)
        if (number.length < 8) {
            Toast.makeText(this, "הזן מספר מלא עם קידומת מדינה", Toast.LENGTH_SHORT).show()
            return
        }
        secureStore.savePhone(number)
        Toast.makeText(this, "החשבון נשמר בצורה מוצפנת במכשיר", Toast.LENGTH_SHORT).show()
    }

    private fun openWhatsApp(raw: String) {
        val number = raw.filter(Char::isDigit)
        if (number.length < 8) {
            Toast.makeText(this, "הזן מספר מלא עם קידומת מדינה", Toast.LENGTH_SHORT).show()
            return
        }

        val whatsappIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("whatsapp://send?phone=$number")
        )
        try {
            startActivity(whatsappIntent)
        } catch (_: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$number")
            )
            try {
                startActivity(webIntent)
            } catch (_: Exception) {
                Toast.makeText(this, "לא ניתן לפתוח את WhatsApp במכשיר הזה", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun WhatsBridgeScreen(
    savedNumber: String?,
    onSave: (String) -> Unit,
    onOpen: (String) -> Unit,
    onClear: () -> Unit
) {
    var number by remember(savedNumber) { mutableStateOf(savedNumber.orEmpty()) }

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
            Text(
                "גישה מאומתת ובטוחה",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "חשבון נשמר + פתיחה מהירה של WhatsApp",
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
                    Text("מספר החשבון שלך", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("972501234567") },
                        supportingText = {
                            Text("כולל קידומת מדינה, ללא + או רווחים")
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { onOpen(number) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            "פתח WhatsApp",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { onSave(number) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("שמור חשבון במכשיר")
                    }

                    if (savedNumber != null) {
                        Spacer(Modifier.height(10.dp))
                        TextButton(
                            onClick = {
                                onClear()
                                number = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("מחק חשבון שמור")
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "המספר נשמר בהצפנת Android Keystore במכשיר. שמירת המספר אינה עוקפת אימות של WhatsApp ואינה מעניקה גישה להודעות ללא הרשאת WhatsApp.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                if (savedNumber != null) "חשבון שמור ומוכן לפתיחה מהירה"
                else "אין כרגע חשבון שמור",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private class SecureAccountStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        ensureKey()
    }

    fun savePhone(phone: String) {
        val encrypted = encrypt(phone.toByteArray(StandardCharsets.UTF_8))
        prefs.edit()
            .putString(PHONE, Base64.encodeToString(encrypted.first, Base64.NO_WRAP))
            .putString(IV, Base64.encodeToString(encrypted.second, Base64.NO_WRAP))
            .apply()
    }

    fun getPhone(): String? {
        val payload = prefs.getString(PHONE, null) ?: return null
        val iv = prefs.getString(IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(
                cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP)),
                StandardCharsets.UTF_8
            )
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encrypt(data: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        return cipher.doFinal(data) to cipher.iv
    }

    private fun ensureKey() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generator.generateKey()
        }
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (keyStore.getKey(KEY_ALIAS, null) as javax.crypto.SecretKey)
    }
}
