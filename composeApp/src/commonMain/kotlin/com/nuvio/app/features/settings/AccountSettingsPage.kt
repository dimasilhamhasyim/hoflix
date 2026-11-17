package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.auth.AuthStorage
import com.nuvio.app.features.auth.AccountGateway
import com.nuvio.app.core.sync.SyncClientIdentity
import com.nuvio.app.features.membership.MembershipGateway
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.account.AccountLink
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.compose_settings_page_account
import nuvio.composeapp.generated.resources.auth_account_deletion_failed
import nuvio.composeapp.generated.resources.settings_account_delete_account
import nuvio.composeapp.generated.resources.settings_account_delete_account_description
import nuvio.composeapp.generated.resources.settings_account_delete_confirm_message
import nuvio.composeapp.generated.resources.settings_account_delete_confirm_title
import nuvio.composeapp.generated.resources.settings_account_email
import nuvio.composeapp.generated.resources.settings_account_not_signed_in
import nuvio.composeapp.generated.resources.settings_account_sign_out
import nuvio.composeapp.generated.resources.settings_account_sign_out_confirm_message
import nuvio.composeapp.generated.resources.settings_account_sign_out_confirm_title
import nuvio.composeapp.generated.resources.settings_account_session_current
import nuvio.composeapp.generated.resources.settings_account_session_days_ago
import nuvio.composeapp.generated.resources.settings_account_session_hours_ago
import nuvio.composeapp.generated.resources.settings_account_session_just_now
import nuvio.composeapp.generated.resources.settings_account_session_last_used
import nuvio.composeapp.generated.resources.settings_account_session_minutes_ago
import nuvio.composeapp.generated.resources.settings_account_session_other
import nuvio.composeapp.generated.resources.settings_account_session_revoke
import nuvio.composeapp.generated.resources.settings_account_sessions_description
import nuvio.composeapp.generated.resources.settings_account_sessions_failed
import nuvio.composeapp.generated.resources.settings_account_sessions_loading
import nuvio.composeapp.generated.resources.settings_account_sessions_logout_others
import nuvio.composeapp.generated.resources.settings_account_sessions_title
import nuvio.composeapp.generated.resources.settings_account_status
import nuvio.composeapp.generated.resources.settings_account_status_anonymous
import nuvio.composeapp.generated.resources.settings_account_status_signed_in
import nuvio.composeapp.generated.resources.settings_account_membership_title

import nuvio.composeapp.generated.resources.settings_account_membership_active

import nuvio.composeapp.generated.resources.settings_account_membership_inactive

import nuvio.composeapp.generated.resources.settings_account_membership_days_left

import nuvio.composeapp.generated.resources.settings_account_membership_expires

import nuvio.composeapp.generated.resources.settings_account_membership_devices_title

import nuvio.composeapp.generated.resources.settings_account_membership_devices_description

import nuvio.composeapp.generated.resources.settings_account_membership_revoke

import nuvio.composeapp.generated.resources.settings_account_membership_loading

import nuvio.composeapp.generated.resources.settings_account_membership_failed

import nuvio.composeapp.generated.resources.settings_account_membership_current

import nuvio.composeapp.generated.resources.settings_account_membership_inactive_hint
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.accountSettingsContent(
    isTablet: Boolean,
) {
    item {
        AccountSettingsBody(isTablet = isTablet)
    }
}

@Composable
private fun AccountSettingsBody(
    isTablet: Boolean,
) {
    val authState by AuthRepository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }
    var showLinkForm by remember { mutableStateOf(false) }
    var linkEmail by remember { mutableStateOf("") }
    var linkPassword by remember { mutableStateOf("") }
    var linkIsSignUp by remember { mutableStateOf(false) }
    var linkBusy by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
    val accountState = authState
    val deleteAccountFallbackMessage = stringResource(Res.string.auth_account_deletion_failed)
    val canDeleteAccount = AppFeaturePolicy.accountDeletionEnabled && authState is AuthState.Authenticated

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NuvioSurfaceCard {
            Text(
                text = stringResource(Res.string.compose_settings_page_account),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))

            when (val state = authState) {
                is AuthState.Authenticated -> {
                    AccountInfoRow(
                        label = stringResource(Res.string.settings_account_status),
                        value = if (state.isAnonymous) {
                            stringResource(Res.string.settings_account_status_anonymous)
                        } else {
                            stringResource(Res.string.settings_account_status_signed_in)
                        },
                        valueColor = MaterialTheme.colorScheme.primary,
                    )
                    state.email?.takeUnless { state.isAnonymous }?.let { email ->
                        Spacer(modifier = Modifier.height(8.dp))
                        AccountInfoRow(
                            label = stringResource(Res.string.settings_account_email),
                            value = email,
                        )
                    }
                }
                else -> {
                    Text(
                        text = stringResource(Res.string.settings_account_not_signed_in),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        NuvioPrimaryButton(
            text = stringResource(Res.string.settings_account_sign_out),
            onClick = { showSignOutConfirm = true },
        )

        if (accountState is AuthState.Authenticated && accountState.isAnonymous) {
            AccountLinkCard(
                showForm = showLinkForm,
                email = linkEmail,
                password = linkPassword,
                isSignUp = linkIsSignUp,
                isBusy = linkBusy,
                errorMessage = linkError,
                onEmailChange = { linkEmail = it; linkError = null },
                onPasswordChange = { linkPassword = it; linkError = null },
                onToggleMode = { linkIsSignUp = !linkIsSignUp; linkError = null },
                onOpenForm = { showLinkForm = true; linkError = null },
                onCancel = { showLinkForm = false; linkError = null },
                onSubmit = {
                    if (linkBusy) return@AccountLinkCard
                    if (linkEmail.isBlank() || linkPassword.length < 6) {
                        linkError = "Email wajib diisi dan password minimal 6 karakter."
                        return@AccountLinkCard
                    }
                    linkBusy = true
                    linkError = null
                    scope.launch {
                        // Pendaftaran akun baru hanya lewat halaman masuk
                        // (yang memakai captcha); di sini hanya masuk.
                        val authResult = AuthRepository.signInWithEmail(linkEmail.trim(), linkPassword)
                        val result = authResult.mapCatching { AccountLink.afterSignIn().getOrThrow() }
                        linkBusy = false
                        if (result.isSuccess) {
                            showLinkForm = false
                            linkEmail = ""
                            linkPassword = ""
                        } else {
                            linkError = AuthRepository.error.value
                                ?: result.exceptionOrNull()?.message
                                ?: "Tidak bisa masuk. Coba lagi."
                        }
                    }
                },
            )
        }

        AccountSessionsCard()

        AccountMembershipCard()

        if (canDeleteAccount) {
            DeleteAccountCard(
                errorMessage = deleteErrorMessage,
                onDeleteClick = {
                    deleteErrorMessage = null
                    showDeleteConfirm = true
                },
            )
        }
    }

    NuvioStatusModal(
        title = stringResource(Res.string.settings_account_sign_out_confirm_title),
        message = stringResource(Res.string.settings_account_sign_out_confirm_message),
        isVisible = showSignOutConfirm,
        confirmText = stringResource(Res.string.settings_account_sign_out),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            showSignOutConfirm = false
            scope.launch { AuthRepository.signOut() }
        },
        onDismiss = { showSignOutConfirm = false },
    )

    NuvioStatusModal(
        title = stringResource(Res.string.settings_account_delete_confirm_title),
        message = stringResource(Res.string.settings_account_delete_confirm_message),
        isVisible = showDeleteConfirm,
        isBusy = isDeletingAccount,
        destructive = true,
        confirmText = stringResource(Res.string.settings_account_delete_account),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            if (isDeletingAccount) return@NuvioStatusModal
            isDeletingAccount = true
            scope.launch {
                val result = AuthRepository.deleteAccount()
                isDeletingAccount = false
                showDeleteConfirm = false
                deleteErrorMessage = if (result.isSuccess) {
                    null
                } else {
                    AuthRepository.error.value
                        ?: result.exceptionOrNull()?.message
                        ?: deleteAccountFallbackMessage
                }
            }
        },
        onDismiss = {
            if (!isDeletingAccount) {
                showDeleteConfirm = false
            }
        },
    )
}

@Composable
private fun AccountMembershipCard() {
    val tokens = MaterialTheme.nuvio
    val scope = rememberCoroutineScope()
    val token = remember { runCatching { AuthStorage.loadIdentityToken() }.getOrNull().orEmpty() }
    if (token.isBlank()) return

    var status by remember { mutableStateOf<MembershipGateway.Status?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var busyDevice by remember { mutableStateOf<String?>(null) }
    val currentDevice = remember {
        runCatching { SyncClientIdentity.currentClientId() }.getOrNull().orEmpty()
    }

    suspend fun refresh() {
        loading = true
        when (val outcome = MembershipGateway.fetch()) {
            is MembershipGateway.Outcome.Resolved -> {
                status = outcome.status
                failed = false
            }

            else -> failed = true
        }
        loading = false
    }

    LaunchedEffect(token) { refresh() }

    NuvioSurfaceCard {
        Text(
            text = stringResource(Res.string.settings_account_membership_title),
            style = MaterialTheme.typography.titleMedium,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))

        when {
            loading -> Text(
                text = stringResource(Res.string.settings_account_membership_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
            )

            failed || status == null -> Text(
                text = stringResource(Res.string.settings_account_membership_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
            )

            else -> {
                val current = status ?: return@NuvioSurfaceCard
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (current.active) {
                            stringResource(Res.string.settings_account_membership_active)
                        } else {
                            stringResource(Res.string.settings_account_membership_inactive)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (current.active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            tokens.colors.textMuted
                        },
                    )
                    if (current.active && current.daysLeft > 0) {
                        Text(
                            text = stringResource(
                                Res.string.settings_account_membership_days_left,
                                current.daysLeft,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.colors.textMuted,
                        )
                    }
                }

                val planLabel = current.planName.ifBlank { "HOFLIX" }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (current.expiresAt.isBlank()) {
                        planLabel
                    } else {
                        "$planLabel · " + stringResource(
                            Res.string.settings_account_membership_expires,
                            current.expiresAt.take(10),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                )

                if (!current.active) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(Res.string.settings_account_membership_inactive_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(Res.string.settings_account_membership_devices_title) +
                        " · ${current.deviceSlots}",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.settings_account_membership_devices_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                )
                Spacer(modifier = Modifier.height(8.dp))

                current.devices.forEach { device ->
                    val isCurrent = device.installationId == currentDevice
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isCurrent) {
                                    device.name + " · " + stringResource(
                                        Res.string.settings_account_membership_current,
                                    )
                                } else {
                                    device.name
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.colors.textPrimary,
                            )
                            Text(
                                text = listOf(device.platform, device.lastSeen)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!isCurrent) {
                            OutlinedButton(
                                onClick = {
                                    if (busyDevice != null) return@OutlinedButton
                                    busyDevice = device.installationId
                                    scope.launch {
                                        MembershipGateway.revoke(device.installationId)
                                        busyDevice = null
                                        refresh()
                                    }
                                },
                                enabled = busyDevice == null,
                            ) {
                                Text(stringResource(Res.string.settings_account_membership_revoke))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSessionsCard() {
    val tokens = MaterialTheme.nuvio
    val scope = rememberCoroutineScope()
    val token = remember { AuthStorage.loadIdentityToken().orEmpty() }
    if (token.isBlank()) return

    var sessions by remember { mutableStateOf<List<AccountGateway.AccountSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        AccountGateway.listSessions(token)
            .onSuccess { sessions = it; failed = false }
            .onFailure { failed = true }
        loading = false
    }

    LaunchedEffect(token) { refresh() }

    NuvioSurfaceCard {
        Text(
            text = stringResource(Res.string.settings_account_sessions_title),
            style = MaterialTheme.typography.titleMedium,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.settings_account_sessions_description),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
        )
        Spacer(modifier = Modifier.height(12.dp))
        when {
            loading -> Text(
                text = stringResource(Res.string.settings_account_sessions_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
            )

            failed -> Text(
                text = stringResource(Res.string.settings_account_sessions_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
            )

            else -> {
                sessions.forEach { session ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (session.current) {
                                    stringResource(Res.string.settings_account_session_current)
                                } else {
                                    stringResource(Res.string.settings_account_session_other)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = tokens.colors.textPrimary,
                            )
                            Text(
                                text = stringResource(
                                    Res.string.settings_account_session_last_used,
                                    sessionAgeLabel(session.lastUsed),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.colors.textMuted,
                            )
                        }
                        if (!session.current) {
                            OutlinedButton(
                                onClick = {
                                    if (busy) return@OutlinedButton
                                    busy = true
                                    scope.launch {
                                        AccountGateway.revokeSession(token, session.id)
                                            .onSuccess { sessions = it }
                                            .onFailure { failed = true }
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                shape = tokens.shapes.button,
                            ) {
                                Text(
                                    text = stringResource(Res.string.settings_account_session_revoke),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (sessions.count { !it.current } > 0) {
                    OutlinedButton(
                        onClick = {
                            if (busy) return@OutlinedButton
                            busy = true
                            scope.launch {
                                AccountGateway.logoutOtherSessions(token)
                                refresh()
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        shape = tokens.shapes.button,
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_account_sessions_logout_others),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun sessionAgeLabel(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return stringResource(Res.string.settings_account_session_just_now)
    // Server mengirim detik epoch. Selisih dihitung dari jam perangkat supaya
    // label tetap masuk akal walau zona waktunya berbeda.
    val nowSeconds = kotlin.time.Clock.System.now().epochSeconds
    val age = (nowSeconds - epochSeconds).coerceAtLeast(0L)
    return when {
        age < 60L -> stringResource(Res.string.settings_account_session_just_now)
        age < 3_600L -> stringResource(Res.string.settings_account_session_minutes_ago, (age / 60L).toInt())
        age < 86_400L -> stringResource(Res.string.settings_account_session_hours_ago, (age / 3_600L).toInt())
        else -> stringResource(Res.string.settings_account_session_days_ago, (age / 86_400L).toInt())
    }
}

@Composable
private fun DeleteAccountCard(
    errorMessage: String?,
    onDeleteClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio

    NuvioSurfaceCard {
        Text(
            text = stringResource(Res.string.settings_account_delete_account),
            style = MaterialTheme.typography.titleMedium,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.settings_account_delete_account_description),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
        )
        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.danger,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onDeleteClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(NuvioTokens.Space.s48 + NuvioTokens.Space.s4),
            shape = tokens.shapes.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.colors.danger,
                contentColor = tokens.colors.textInverse,
            ),
        ) {
            Text(
                text = stringResource(Res.string.settings_account_delete_account),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AccountInfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Pintu masuk akun. Sesi lokal (anonymous) tetap berjalan; kartu ini hanya
 * menghubungkannya ke akun Supabase supaya profil, setting, riwayat nonton,
 * dan library ikut tersimpan per akun.
 */
@Composable
private fun AccountLinkCard(
    showForm: Boolean,
    email: String,
    password: String,
    isSignUp: Boolean,
    isBusy: Boolean,
    errorMessage: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleMode: () -> Unit,
    onOpenForm: () -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio

    NuvioSurfaceCard {
        Text(
            text = "Akun HOFLIX",
            style = MaterialTheme.typography.titleMedium,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Sekarang data tersimpan di perangkat ini saja. Masuk atau daftar supaya profil, " +
                "pengaturan, riwayat nonton, dan daftar tontonan tersimpan di akun dan bisa dibuka di perangkat lain.",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
        )

        if (!showForm) {
            Spacer(modifier = Modifier.height(14.dp))
            NuvioPrimaryButton(
                text = "Masuk atau daftar akun",
                onClick = onOpenForm,
            )
        } else {
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true,
            enabled = !isBusy,
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password (minimal 6 karakter)") },
            singleLine = true,
            enabled = !isBusy,
            visualTransformation = PasswordVisualTransformation(),
        )
        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.danger,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        NuvioPrimaryButton(
            text = when {
                isBusy -> "Memproses…"
                isSignUp -> "Daftar akun"
                else -> "Masuk"
            },
            onClick = onSubmit,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onToggleMode,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = tokens.colors.textPrimary,
                ),
            ) {
                Text(
                    text = if (isSignUp) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onCancel,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = tokens.colors.textMuted,
                ),
            ) {
                Text(text = "Batal", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            text = "Dengan mendaftar, kamu membuat akun HOFLIX untuk menyimpan data. " +
                "Data lokal yang sudah ada akan dipindahkan ke akun ini.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
            textAlign = TextAlign.Start,
        )
        }
    }
}
