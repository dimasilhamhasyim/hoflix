package com.nuvio.app.features.profiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.cropAvatarImage
import com.nuvio.app.core.ui.decodeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.profile_avatar_crop_apply
import nuvio.composeapp.generated.resources.profile_avatar_crop_description
import nuvio.composeapp.generated.resources.profile_avatar_crop_failed
import nuvio.composeapp.generated.resources.profile_avatar_crop_reset
import nuvio.composeapp.generated.resources.profile_avatar_crop_title
import nuvio.composeapp.generated.resources.profile_avatar_crop_zoom
import org.jetbrains.compose.resources.stringResource

/**
 * Editor potong avatar: foto bisa digeser dan diperbesar di dalam bingkai
 * lingkaran. Hasilnya selalu persegi, jadi tampilannya sama di semua tempat.
 */
@Composable
internal fun AvatarCropDialog(
    source: ByteArray,
    onCancel: () -> Unit,
    onConfirm: (ByteArray) -> Unit,
) {
    val bitmap = remember(source) { decodeImageBitmap(source) }
    var transform by remember(source) { mutableStateOf(AvatarCropTransform.Default) }
    var isCropping by remember { mutableStateOf(false) }
    var cropFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val viewport = 260.dp

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF2000000))
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.profile_avatar_crop_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.profile_avatar_crop_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (bitmap == null) {
                Text(
                    text = stringResource(Res.string.profile_avatar_crop_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = onCancel) {
                    Text(text = stringResource(Res.string.action_cancel), color = Color.White)
                }
                return@Column
            }

            val imageWidth = bitmap.width
            val imageHeight = bitmap.height

            Box(
                modifier = Modifier
                    .size(viewport)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.28f), shape = CircleShape)
                    .pointerInput(imageWidth, imageHeight, viewport) {
                        val viewportPx = viewport.toPx().coerceAtLeast(1f)
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            transform = clampAvatarCropTransform(
                                imageWidth = imageWidth,
                                imageHeight = imageHeight,
                                zoom = transform.zoom * gestureZoom,
                                offsetX = transform.offsetX + (pan.x / viewportPx),
                                offsetY = transform.offsetY + (pan.y / viewportPx),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = transform.zoom
                            scaleY = transform.zoom
                            translationX = transform.offsetX * size.width
                            translationY = transform.offsetY * size.height
                        },
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.profile_avatar_crop_zoom),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f),
                )
                Slider(
                    value = transform.zoom,
                    onValueChange = { value ->
                        transform = clampAvatarCropTransform(
                            imageWidth = imageWidth,
                            imageHeight = imageHeight,
                            zoom = value,
                            offsetX = transform.offsetX,
                            offsetY = transform.offsetY,
                        )
                    },
                    valueRange = AvatarCropMinZoom..AvatarCropMaxZoom,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                    ),
                )
            }

            TextButton(
                onClick = { transform = AvatarCropTransform.Default },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(Res.string.profile_avatar_crop_reset),
                    color = Color.White.copy(alpha = 0.78f),
                )
            }

            if (cropFailed) {
                Text(
                    text = stringResource(Res.string.profile_avatar_crop_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            NuvioPrimaryButton(
                text = stringResource(Res.string.profile_avatar_crop_apply),
                enabled = !isCropping,
                onClick = {
                    val crop = resolveAvatarCropRect(imageWidth, imageHeight, transform) ?: return@NuvioPrimaryButton
                    isCropping = true
                    cropFailed = false
                    scope.launch {
                        val bytes = withContext(Dispatchers.Default) { cropAvatarImage(source, crop) }
                        isCropping = false
                        if (bytes != null) {
                            onConfirm(bytes)
                        } else {
                            cropFailed = true
                        }
                    }
                },
            )
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.action_cancel), color = Color.White.copy(alpha = 0.78f))
            }
        }
    }
}
