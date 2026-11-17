package com.nuvio.app.features.membership

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Ink = Color(0xFF0A0B0D)
private val Panel = Color(0xFF12151A)
private val Line = Color(0xFF23262C)
private val Accent = Color(0xFFE03049)
private val AccentSoft = Color(0xFF2A1116)
private val TextPrimary = Color(0xFFE9ECEF)
private val TextDim = Color(0xFF9AA1AB)

/**
 * Pintu masuk setelah login: katalog dan stream di bridge hanya dilayani untuk
 * langganan aktif, jadi layar ini yang memberi tahu status dan menerima kupon.
 *
 * Status diambil dari server tiap kali layar dibuka — bukan dari cache lokal —
 * supaya akun yang baru menukar kupon langsung masuk tanpa menunggu.
 */
@Composable
internal fun MembershipGateScreen(
    onUnlocked: () -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<MembershipGateway.Status?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var redeeming by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    suspend fun refresh(silent: Boolean = false) {
        if (!silent) loading = true
        failure = ""
        when (val outcome = MembershipGateway.fetch()) {
            is MembershipGateway.Outcome.Resolved -> {
                status = outcome.status
                if (outcome.status.active) onUnlocked()
            }
            is MembershipGateway.Outcome.Rejected -> failure = outcome.message
            MembershipGateway.Outcome.Unreachable ->
                failure = "Tidak bisa menghubungi server. Periksa koneksi lalu coba lagi."
        }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Box(
        modifier = Modifier.fillMaxSize().background(Ink),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "HOFLIX",
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(14.dp))

            when {
                loading -> {
                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("Memeriksa akses…", color = TextDim, fontSize = 14.sp)
                }

                status?.active == true -> {
                    Text("Membuka HOFLIX…", color = TextDim, fontSize = 14.sp)
                }

                else -> {
                    Text(
                        text = if (status != null) "Langganan tidak aktif" else "Akses terkunci",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (status?.daysLeft == 0 && status?.expiresAt?.isNotEmpty() == true) {
                            "Masa aktif sebelumnya sudah berakhir. Masukkan kupon untuk membuka kembali."
                        } else {
                            "Katalog dan pemutaran hanya untuk anggota. Masukkan kode kupon untuk mulai."
                        },
                        color = TextDim,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )

                    Spacer(Modifier.height(22.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel, RoundedCornerShape(8.dp))
                            .border(1.dp, Line, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                    ) {
                        Text("Kode kupon", color = TextDim, fontSize = 12.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = code,
                            onValueChange = { value -> code = value.take(64) },
                            singleLine = true,
                            enabled = !redeeming,
                            placeholder = { Text("mis. HOFLIX-OKT-01", color = TextDim, fontSize = 14.sp) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .background(
                                    if (redeeming || code.isBlank()) Line else Accent,
                                    RoundedCornerShape(6.dp),
                                )
                                .then(
                                    if (redeeming || code.isBlank()) Modifier
                                    else Modifier.border(0.dp, Accent, RoundedCornerShape(6.dp))
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(
                                enabled = !redeeming && code.isNotBlank(),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.fillMaxSize(),
                                onClick = {
                                    redeeming = true
                                    message = ""
                                    scope.launch {
                                        MembershipGateway.redeem(code.trim())
                                            .onSuccess { fresh ->
                                                status = fresh
                                                message = "Kupon diterima. ${fresh.daysLeft} hari aktif."
                                                if (fresh.active) onUnlocked()
                                            }
                                            .onFailure { error ->
                                                message = error.message ?: "Kupon tidak bisa dipakai."
                                            }
                                        redeeming = false
                                    }
                                },
                            ) {
                                if (redeeming) {
                                    CircularProgressIndicator(
                                        color = TextPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp),
                                    )
                                } else {
                                    Text(
                                        if (code.isBlank()) "Masukkan kode" else "Pakai kupon",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                        if (message.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(message, color = TextDim, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }

                    if (failure.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            failure,
                            color = Accent,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { scope.launch { refresh() } }) {
                            Text("Muat ulang status", color = TextPrimary, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("·", color = TextDim)
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = onSignOut) {
                            Text("Keluar akun", color = TextDim, fontSize = 13.sp)
                        }
                    }

                    status?.devices?.takeIf { it.isNotEmpty() }?.let { devices ->
                        Spacer(Modifier.height(20.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "PERANGKAT",
                                color = TextDim,
                                fontSize = 11.sp,
                                letterSpacing = 1.4.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            devices.forEach { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            device.name.ifBlank { "Perangkat" },
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                        )
                                        Text(
                                            device.platform.ifBlank { "—" },
                                            color = TextDim,
                                            fontSize = 11.sp,
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                MembershipGateway.revoke(device.installationId)
                                                    .onFailure { message = it.message ?: "Gagal mencabut." }
                                                refresh(silent = true)
                                            }
                                        },
                                    ) {
                                        Text("Cabut", color = Accent, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    val priceIdr = status?.priceIdr ?: 0
                    Text(
                        text = if (priceIdr > 0) {
                            "Plus 30 hari · Rp$priceIdr — pembayaran otomatis belum aktif, minta kupon ke admin."
                        } else {
                            "Pembayaran otomatis belum aktif. Minta kode kupon ke admin."
                        },
                        color = TextDim,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}
