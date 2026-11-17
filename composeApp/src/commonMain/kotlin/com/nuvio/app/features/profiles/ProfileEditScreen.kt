package com.nuvio.app.features.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.core.ui.ProfileImagePickResult
import com.nuvio.app.core.ui.decodeImageBitmap
import com.nuvio.app.core.ui.rememberProfileImagePicker
import com.nuvio.app.core.ui.themePalette
import com.nuvio.app.features.auth.AccountGateway
import com.nuvio.app.features.membership.CosmeticEntitlement
import com.nuvio.app.features.membership.MemberAccessRepository
import com.nuvio.app.features.membership.ProfileBackgroundRepository
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileEditScreen(
    profile: NuvioProfile? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isNew = profile == null
    val scope = rememberCoroutineScope()
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val currentProfile = remember(profile?.profileIndex, profileState.profiles, profile) {
        profile?.let { snapshot ->
            profileState.profiles.find { it.profileIndex == snapshot.profileIndex } ?: snapshot
        }
    }
    val fallbackColorHex = currentProfile?.avatarColorHex ?: PROFILE_COLORS.first()
    // Avatar asli Nuvio: lingkaran warna + huruf pertama nama profil.
    var monogramColorHex by rememberSaveable(currentProfile?.profileIndex) {
        mutableStateOf(currentProfile?.avatarColorHex ?: PROFILE_COLORS.first())
    }
    // URL avatar yang tersimpan sebelum layar ini dibuka, dipakai untuk menghapus
    // file unggahan lama di server setelah profil baru tersimpan.
    val originalAvatarUrl = remember(currentProfile?.profileIndex) {
        currentProfile?.avatarUrl.orEmpty()
    }

    var name by rememberSaveable { mutableStateOf(currentProfile?.name ?: "") }
    var selectedAvatarId by rememberSaveable { mutableStateOf(currentProfile?.avatarId) }
    var avatarUrl by rememberSaveable { mutableStateOf(currentProfile?.avatarUrl.orEmpty()) }
    var selectedBackgroundId by rememberSaveable { mutableStateOf(currentProfile?.profileBackgroundId) }
    var selectedBackgroundUrl by rememberSaveable { mutableStateOf(currentProfile?.profileBackgroundUrl) }
    var usesPrimaryAddons by rememberSaveable { mutableStateOf(currentProfile?.usesPrimaryAddons ?: false) }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showPinClear by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var avatarUploadFailed by remember { mutableStateOf(false) }
    var avatarUploadError by remember { mutableStateOf<String?>(null) }
    var avatarPickFailed by remember { mutableStateOf(false) }
    var pendingAvatarSource by remember { mutableStateOf<ByteArray?>(null) }
    var localAvatarPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    val pickAvatarPhoto = rememberProfileImagePicker { result ->
        when (result) {
            is ProfileImagePickResult.Picked -> {
                avatarPickFailed = false
                avatarUploadFailed = false
                avatarUploadError = null
                pendingAvatarSource = result.bytes
            }
            ProfileImagePickResult.Failed -> avatarPickFailed = true
            ProfileImagePickResult.Cancelled -> Unit
        }
    }
    val memberAccess by remember {
        MemberAccessRepository.ensureStarted()
        MemberAccessRepository.access
    }.collectAsStateWithLifecycle()
    val backgroundCatalog by ProfileBackgroundRepository.catalog.collectAsStateWithLifecycle()
    val canChooseBackground = !isNew && memberAccess.entitlements.includes(CosmeticEntitlement.PROFILE_BACKGROUNDS)

    val avatars by AvatarRepository.avatars.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        AvatarRepository.refreshAvatars()
    }
    LaunchedEffect(canChooseBackground) {
        if (canChooseBackground) ProfileBackgroundRepository.preloadLandscapeImages()
    }
    LaunchedEffect(isNew, avatars, selectedAvatarId, avatarUrl, currentProfile?.profileIndex) {
        // Profil baru dan profil lama yang belum pernah memilih avatar sama-sama
        // diisi preset bawaan (empat smiley), jadi tidak ada profil yang tampil
        // sebagai monogram kosong.
        if (avatarUrl.isBlank() && selectedAvatarId == null && avatars.isNotEmpty()) {
            val profileIndex = currentProfile?.profileIndex
                ?: ProfileRepository.nextAvailableProfileIndex()
                ?: 1
            selectedAvatarId = defaultProfileAvatarId(
                catalog = avatars,
                preferredId = AvatarRepository.preferredDefaultAvatarId(),
                profileIndex = profileIndex,
            )
        }
    }

    val customAvatarUrl = remember(avatarUrl) { normalizedAvatarUrl(avatarUrl) }
    val avatarUrlIsInvalid = avatarUrl.isNotBlank() && customAvatarUrl == null
    val selectedAvatarItem = remember(selectedAvatarId, avatars) {
        selectedAvatarId?.let { id -> avatars.find { it.id == id } }
    }
    val visibleAvatarItem = if (customAvatarUrl == null) selectedAvatarItem else null
    val previewAccent = remember(visibleAvatarItem, fallbackColorHex) {
        parseHexColor(visibleAvatarItem?.bgColor ?: fallbackColorHex)
    }

    NuvioScreen(modifier = modifier) {
        stickyHeader {
            NuvioScreenHeader(
                title = if (isNew) {
                    stringResource(Res.string.profile_edit_add_title)
                } else {
                    stringResource(Res.string.profile_edit_edit_title)
                },
                onBack = onBack,
            )
        }

        item {
            ProfileIdentityCard(
                name = name,
                isNew = isNew,
                profileIndex = currentProfile?.profileIndex,
                usesPrimaryAddons = usesPrimaryAddons,
                onNameChange = { name = it },
                onUsesPrimaryAddonsChange = { usesPrimaryAddons = it },
                selectedAvatar = visibleAvatarItem,
                customAvatarUrl = customAvatarUrl,
                localAvatarPreview = localAvatarPreview,
                accentColor = previewAccent,
                hasAvatarChoices = avatars.isNotEmpty(),
            )
        }

        item {
            NuvioSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(Res.string.profile_avatar_photo_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.profile_avatar_photo_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (avatarPickFailed) {
                        Text(
                            text = stringResource(Res.string.profile_avatar_pick_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (avatarUploadFailed) {
                        Text(
                            text = avatarUploadError?.takeIf { it.isNotBlank() }?.let { detail ->
                                stringResource(Res.string.profile_avatar_upload_failed_detail, detail)
                            } ?: stringResource(Res.string.profile_avatar_upload_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    NuvioPrimaryButton(
                        text = if (isUploadingAvatar) {
                            stringResource(Res.string.profile_avatar_uploading)
                        } else {
                            stringResource(Res.string.profile_avatar_upload_action)
                        },
                        enabled = !isUploadingAvatar,
                        onClick = { pickAvatarPhoto() },
                    )
                    if (avatarUrl.isNotBlank()) {
                        Text(
                            text = stringResource(Res.string.profile_avatar_upload_remove),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    avatarUrl = ""
                                    avatarUploadFailed = false
                                    avatarUploadError = null
                                    avatarPickFailed = false
                                    localAvatarPreview = null
                                }
                                .padding(vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        if (canChooseBackground) {
            item {
                NuvioSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = stringResource(Res.string.profile_choose_background),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(Res.string.profile_background_member_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ProfileBackgroundPicker(
                            backgrounds = backgroundCatalog,
                            selectedBackgroundId = selectedBackgroundId,
                            selectedBackgroundUrl = selectedBackgroundUrl,
                            customBackgroundUrl = currentProfile?.profileBackgroundUrl,
                            standardBackgroundColor = previewAccent,
                            onSelectionChange = { id, url ->
                                selectedBackgroundId = id
                                selectedBackgroundUrl = url
                            },
                        )
                    }
                }
            }
        }

        item {
            NuvioSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = stringResource(Res.string.profile_choose_avatar),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = selectedAvatarItem?.displayName
                            ?: if (avatars.isEmpty()) "" else stringResource(Res.string.profile_select_avatar),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (avatars.isNotEmpty()) {
                        AvatarPicker(
                            avatars = avatars,
                            selectedAvatarId = selectedAvatarId.takeIf { customAvatarUrl == null },
                            onAvatarSelected = { avatar ->
                                avatarUrl = ""
                                selectedAvatarId = avatar.id
                            },
                        )
                    } else {
                        // Nuvio asli tidak menyimpan katalog gambar avatar: yang
                        // tampil adalah lingkaran warna + huruf pertama nama.
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(monogramColorHex)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = name.trim().take(1).uppercase().ifBlank { "?" },
                                style = MaterialTheme.typography.headlineLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            PROFILE_COLORS.forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(hex))
                                        .then(
                                            if (hex.equals(monogramColorHex, ignoreCase = true)) {
                                                Modifier.border(2.dp, Color.White, CircleShape)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .clickable {
                                            monogramColorHex = hex
                                            avatarUrl = ""
                                            selectedAvatarId = null
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!isNew) {
            item {
                NuvioSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = stringResource(Res.string.profile_security),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (currentProfile?.pinEnabled == true) {
                                stringResource(Res.string.profile_security_pin_enabled)
                            } else {
                                stringResource(Res.string.profile_security_pin_disabled)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (currentProfile?.pinEnabled == true) {
                            NuvioPrimaryButton(
                                text = stringResource(Res.string.profile_remove_pin_lock),
                                onClick = { showPinClear = true },
                            )
                        } else {
                            NuvioPrimaryButton(
                                text = stringResource(Res.string.profile_set_pin_lock),
                                onClick = { showPinSetup = true },
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            NuvioPrimaryButton(
                text = if (isSaving) {
                    stringResource(Res.string.profile_saving)
                } else if (isNew) {
                    stringResource(Res.string.profile_create_profile)
                } else {
                    stringResource(Res.string.collections_editor_save_changes)
                },
                enabled = name.isNotBlank() && !avatarUrlIsInvalid && !isSaving,
                onClick = {
                    isSaving = true
                    scope.launch {
                        val avatarColorHex = visibleAvatarItem?.bgColor ?: monogramColorHex ?: fallbackColorHex
                        if (isNew) {
                            ProfileRepository.createProfile(
                                name = name,
                                avatarColorHex = avatarColorHex,
                                avatarId = if (customAvatarUrl == null) selectedAvatarId else null,
                                avatarUrl = customAvatarUrl,
                                usesPrimaryAddons = usesPrimaryAddons,
                            )
                        } else {
                            ProfileRepository.updateProfile(
                                profileIndex = currentProfile!!.profileIndex,
                                name = name,
                                avatarColorHex = avatarColorHex,
                                avatarId = if (customAvatarUrl == null) selectedAvatarId else null,
                                avatarUrl = customAvatarUrl,
                                profileBackgroundId = selectedBackgroundId,
                                profileBackgroundUrl = selectedBackgroundUrl,
                                usesPrimaryAddons = usesPrimaryAddons,
                            )
                        }
                        isSaving = false
                        if (originalAvatarUrl.isNotBlank() && originalAvatarUrl != customAvatarUrl) {
                            // Foto lama sudah tidak dipakai profil ini. Hapus di
                            // server (best effort); kalau gagal, prune harian yang
                            // membersihkan.
                            scope.launch { AccountGateway.deleteAvatar(originalAvatarUrl) }
                        }
                        onSaved()
                    }
                },
            )
        }

        if (!isNew && (currentProfile?.profileIndex ?: 0) > 1) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(
                        text = stringResource(Res.string.profile_delete_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    NuvioStatusModal(
        title = stringResource(Res.string.profile_delete_title),
        message = stringResource(
            Res.string.profile_delete_confirm_message,
            currentProfile?.name.orEmpty(),
        ),
        isVisible = showDeleteConfirm,
        destructive = true,
        confirmText = stringResource(Res.string.action_delete),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            showDeleteConfirm = false
            scope.launch {
                currentProfile?.let { ProfileRepository.deleteProfile(it.profileIndex) }
                onBack()
            }
        },
        onDismiss = { showDeleteConfirm = false },
    )

    if (showPinSetup && currentProfile != null) {
        PinSetupDialog(
            profileIndex = currentProfile.profileIndex,
            hasExistingPin = currentProfile.pinEnabled,
            onDone = {
                showPinSetup = false
            },
            onDismiss = { showPinSetup = false },
        )
    }

    if (showPinClear && currentProfile != null) {
        PinEntryDialog(
            profileName = stringResource(Res.string.profile_remove_pin_for, currentProfile.name),
            onVerify = { pin -> ProfileRepository.clearPin(currentProfile.profileIndex, pin) },
            onVerified = {
                showPinClear = false
            },
            onDismiss = {
                showPinClear = false
            },
        )
    }

    pendingAvatarSource?.let { source ->
        AvatarCropDialog(
            source = source,
            onCancel = { pendingAvatarSource = null },
            onConfirm = { cropped ->
                pendingAvatarSource = null
                localAvatarPreview = decodeImageBitmap(cropped)
                isUploadingAvatar = true
                avatarUploadFailed = false
                avatarUploadError = null
                scope.launch {
                    AccountGateway.uploadAvatar(cropped)
                        .onSuccess { url ->
                            avatarUrl = url
                            selectedAvatarId = null
                        }
                        .onFailure { failure ->
                            avatarUploadFailed = true
                            avatarUploadError = failure.message.orEmpty()
                        }
                    isUploadingAvatar = false
                }
            },
        )
    }
}

@Composable
private fun ProfileIdentityCard(
    name: String,
    isNew: Boolean,
    profileIndex: Int?,
    usesPrimaryAddons: Boolean,
    onNameChange: (String) -> Unit,
    onUsesPrimaryAddonsChange: (Boolean) -> Unit,
    selectedAvatar: AvatarCatalogItem?,
    customAvatarUrl: String?,
    localAvatarPreview: ImageBitmap?,
    accentColor: Color,
    hasAvatarChoices: Boolean,
) {
    NuvioSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            if (selectedAvatar != null || customAvatarUrl != null || localAvatarPreview != null) {
                                accentColor
                            } else {
                                accentColor.copy(alpha = 0.18f)
                            },
                        )
                        .border(
                            width = 2.dp,
                            color = if (selectedAvatar == null && customAvatarUrl == null && localAvatarPreview == null) {
                                accentColor.copy(alpha = 0.35f)
                            } else {
                                Color.Transparent
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (localAvatarPreview != null) {
                        Image(
                            bitmap = localAvatarPreview,
                            contentDescription = name,
                            modifier = Modifier.size(88.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else if (customAvatarUrl != null) {
                        AsyncImage(
                            model = customAvatarUrl,
                            contentDescription = name,
                            modifier = Modifier.size(88.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else if (selectedAvatar != null) {
                        AsyncImage(
                            model = avatarImageUrl(selectedAvatar),
                            contentDescription = selectedAvatar.displayName,
                            modifier = Modifier.size(88.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else if (name.isNotBlank()) {
                        Text(
                            text = name.take(1).uppercase(),
                            style = MaterialTheme.typography.displayLarge,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = name.ifBlank {
                            if (isNew) stringResource(Res.string.profile_new)
                            else stringResource(Res.string.profile_unnamed)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = listOf(
                            if (isNew) {
                                stringResource(Res.string.profile_new)
                            } else {
                                profileIndex?.let { stringResource(Res.string.profile_label_number, it) }
                                    ?: stringResource(Res.string.profile_unnamed)
                            },
                            if (usesPrimaryAddons) {
                                stringResource(Res.string.profile_primary_addons_on)
                            } else {
                                stringResource(Res.string.profile_primary_addons_off)
                            },
                        ).joinToString("  |  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = when {
                            customAvatarUrl != null -> stringResource(Res.string.profile_custom_avatar_selected)
                            selectedAvatar != null -> stringResource(
                                Res.string.profile_avatar_selected,
                                selectedAvatar.displayName,
                            )
                            hasAvatarChoices -> stringResource(Res.string.profile_choose_avatar_below)
                            else -> stringResource(Res.string.profile_avatar_options_pending)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            NuvioInputField(
                value = name,
                onValueChange = onNameChange,
                placeholder = stringResource(Res.string.profile_name_placeholder),
            )

        }
    }
}


@Composable
private fun ProfileOptionRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}

@Composable
fun PinSetupDialog(
    profileIndex: Int,
    hasExistingPin: Boolean,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(if (hasExistingPin) "current" else "new") }
    var currentPin by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    when (step) {
        "current" -> PinEntryDialog(
            profileName = stringResource(Res.string.profile_enter_current_pin),
            onVerify = { pin -> ProfileRepository.verifyPin(profileIndex, pin) },
            onVerified = { pin ->
                currentPin = pin
                step = "new"
            },
            onDismiss = onDismiss,
        )

        "new" -> PinEntryDialog(
            profileName = stringResource(Res.string.profile_enter_new_pin),
            onVerify = { pin ->
                ProfileRepository.setPin(
                    profileIndex = profileIndex,
                    pin = pin,
                    currentPin = currentPin.ifEmpty { null },
                )
            },
            onVerified = {
                onDone()
            },
            onDismiss = onDismiss,
        )
    }
}
