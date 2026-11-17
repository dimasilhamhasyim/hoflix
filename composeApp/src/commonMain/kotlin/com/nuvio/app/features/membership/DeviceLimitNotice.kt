package com.nuvio.app.features.membership

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.sync.SyncClientIdentity
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.device_limit_body
import nuvio.composeapp.generated.resources.device_limit_body_unknown
import nuvio.composeapp.generated.resources.device_limit_close
import nuvio.composeapp.generated.resources.device_limit_current
import nuvio.composeapp.generated.resources.device_limit_revoke
import nuvio.composeapp.generated.resources.device_limit_revoke_failed
import nuvio.composeapp.generated.resources.device_limit_revoked
import nuvio.composeapp.generated.resources.device_limit_title
import org.jetbrains.compose.resources.stringResource

/**
 * Pemberitahuan `409 device_limit` dari bridge.
 *
 * Bridge menolak perangkat ke-5 saat 4 slot terpakai. Tanpa ini, penolakan itu
 * hanya muncul sebagai "sumber tidak ditemukan" dan pemakai tidak tahu ada
 * perangkat lain yang harus dicabut. State di sini murni tampilan — keputusan
 * tetap di server.
 */
internal object DeviceLimitNotice {
    data class Device(
        val installationId: String,
        val name: String,
        val lastSeen: String,
    )

    data class State(
        val devices: List<Device> = emptyList(),
        val maxDevices: Int = 4,
        val currentDeviceId: String = "",
    )

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    private fun currentDeviceId(): String =
        runCatching { SyncClientIdentity.currentClientId() }.getOrNull().orEmpty()

    fun dismiss() {
        _state.value = null
    }

    /**
     * Dipanggil setelah bridge membalas 409. Daftar perangkat diambil ulang dari
     * `/hf/membership` karena isi balasan 409 tidak terbawa oleh jalur galat.
     */
    suspend fun probeAndShow() {
        if (_state.value != null) return
        val status = when (val outcome = runCatching { MembershipGateway.fetch() }.getOrNull()) {
            is MembershipGateway.Outcome.Resolved -> outcome.status
            else -> null
        }
        _state.value = State(
            devices = status?.devices.orEmpty().map { device ->
                Device(
                    installationId = device.installationId,
                    name = device.name,
                    lastSeen = device.lastSeen,
                )
            },
            maxDevices = status?.maxDevices ?: 4,
            currentDeviceId = currentDeviceId(),
        )
    }

    fun removeDevice(installationId: String) {
        val current = _state.value ?: return
        val remaining = current.devices.filterNot { it.installationId == installationId }
        _state.value = if (remaining.isEmpty()) null else current.copy(devices = remaining)
    }
}

@Composable
internal fun DeviceLimitHost() {
    val state by DeviceLimitNotice.state.collectAsState()
    val notice = state ?: return
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    val tokens = MaterialTheme.nuvio
    val revokedText = stringResource(Res.string.device_limit_revoked)
    val failedText = stringResource(Res.string.device_limit_revoke_failed)

    DialogSurface(
        onDismissRequest = { DeviceLimitNotice.dismiss() },
        title = stringResource(Res.string.device_limit_title),
        message = if (notice.devices.isEmpty()) {
            stringResource(Res.string.device_limit_body_unknown, notice.maxDevices)
        } else {
            stringResource(Res.string.device_limit_body, notice.devices.size, notice.maxDevices)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.listGap),
        ) {
            notice.devices.forEach { device ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.name.ifBlank { device.installationId },
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (device.installationId == notice.currentDeviceId) {
                            Text(
                                text = stringResource(Res.string.device_limit_current),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.colors.accent,
                            )
                        }
                    }
                    DialogButton(
                        text = stringResource(Res.string.device_limit_revoke),
                        onClick = {
                            if (busy != null) return@DialogButton
                            busy = device.installationId
                            scope.launch {
                                val result = MembershipGateway.revoke(device.installationId)
                                busy = null
                                if (result.isSuccess) {
                                    DeviceLimitNotice.removeDevice(device.installationId)
                                    NuvioToastController.show(revokedText)
                                } else {
                                    NuvioToastController.show(failedText)
                                }
                            }
                        },
                        style = DialogButtonStyle.Destructive,
                        loading = busy == device.installationId,
                        enabled = busy == null,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                }
            }
        }
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.device_limit_close),
                onClick = { DeviceLimitNotice.dismiss() },
                style = DialogButtonStyle.Secondary,
            )
        }
    }
}
