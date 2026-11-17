package com.nuvio.app.features.appupdate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.PlatformBackHandler
import kotlinx.coroutines.launch

private val Ink = Color(0xFF0A0B0D)
private val Panel = Color(0xFF12151A)
private val Line = Color(0xFF23262C)
private val Accent = Color(0xFFE03049)
private val TextPrimary = Color(0xFFE9ECEF)
private val TextDim = Color(0xFF9AA1AB)
private val TextMute = Color(0xFF6F757E)
private val Danger = Color(0xFFF0445C)

/**
 * Pintu yang tidak bisa dilewati: versi aplikasi lebih tua dari `minCode`.
 *
 * Tidak ada tombol tutup dan tidak ada jalan kembali — server juga menolak
 * semua permintaan isi dari versi ini (426), jadi meloloskan layar ini hanya
 * akan menghasilkan aplikasi kosong. Satu-satunya jalan keluar adalah
 * memasang versi baru.
 */
@Composable
internal fun UpdateRequiredScreen(
    config: AppUpdateConfig,
    installedName: String,
    installedCode: Int,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var downloaded by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    PlatformBackHandler(enabled = true) { /* sengaja tidak melakukan apa pun */ }

    fun start() {
        if (busy) return
        if (!AppUpdateInstaller.canInstall()) {
            status = "Izinkan pemasangan dari sumber tidak dikenal untuk Hoflix, lalu tekan Perbarui lagi."
            AppUpdateInstaller.openPermissionSettings()
            return
        }
        busy = true
        error = ""
        status = "Mengunduh pembaruan…"
        progress = 0f
        downloaded = 0L
        total = config.sizeBytes.takeIf { it > 0 }
        scope.launch {
            val result = AppUpdateInstaller.download(config) { done, size ->
                downloaded = done
                if (size != null && size > 0) {
                    total = size
                    progress = (done.toFloat() / size.toFloat()).coerceIn(0f, 1f)
                }
            }
            result
                .onSuccess { path ->
                    status = "Memasang…"
                    val install = AppUpdateInstaller.install(path)
                    if (install.isFailure) {
                        busy = false
                        progress = null
                        error = install.exceptionOrNull()?.message ?: "Installer tidak bisa dibuka."
                    }
                }
                .onFailure { failure ->
                    busy = false
                    progress = null
                    error = failure.message ?: "Unduhan gagal."
                    status = ""
                }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "HOFLIX",
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.6.sp,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Perbarui Hoflix",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Versi yang terpasang sudah tidak didukung. Perbarui untuk melanjutkan menonton.",
                color = TextDim,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Line, RoundedCornerShape(10.dp))
                    .background(Panel, RoundedCornerShape(10.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VersionRow("Terpasang", "$installedName ($installedCode)")
                VersionRow(
                    "Wajib",
                    if (config.latest.isNotBlank()) "${config.latest} (${config.minCode})" else "${config.minCode}",
                )
                if (config.sizeBytes > 0) {
                    VersionRow("Ukuran berkas", formatBytes(config.sizeBytes))
                }
            }

            if (config.notes.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Yang baru",
                    color = TextMute,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(text = config.notes, color = TextDim, fontSize = 13.sp, lineHeight = 20.sp)
            }

            Spacer(Modifier.height(24.dp))

            if (progress != null) {
                LinearProgressIndicator(
                    progress = progress ?: 0f,
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = Line,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (status.isNotBlank()) {
                Text(text = status, color = TextDim, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
            }

            if (progress != null) {
                val totalBytes = total
                val label = if (totalBytes != null && totalBytes > 0) {
                    "${formatBytes(downloaded)} / ${formatBytes(totalBytes)}"
                } else {
                    formatBytes(downloaded)
                }
                Text(text = label, color = TextMute, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
            }

            if (error.isNotBlank()) {
                Text(text = error, color = Danger, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = { start() },
                enabled = !busy && config.downloadable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color.White,
                    disabledContainerColor = Line,
                    disabledContentColor = TextMute,
                ),
            ) {
                Text(
                    text = when {
                        busy && progress != null -> "Mengunduh…"
                        busy -> "Menyiapkan…"
                        !config.downloadable -> "Unduhan belum tersedia"
                        else -> "Perbarui sekarang"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = if (AppUpdateInstaller.isSupported()) {
                    "Berkas diperiksa sidik SHA-256 dan tanda tangannya sebelum dipasang."
                } else {
                    "Pasang versi terbaru dari sumber unduhan resmi Hoflix."
                },
                color = TextMute,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun VersionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = TextMute, fontSize = 12.sp)
        Text(text = value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index += 1
    }
    return if (index == 0) "${value.toLong()} ${units[index]}" else {
        val rounded = kotlin.math.round(value * 10) / 10
        "$rounded ${units[index]}"
    }
}
