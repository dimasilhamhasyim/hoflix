package com.nuvio.app.features.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.settings.AppBrandWordmark
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_auth_already_have_account
import nuvio.composeapp.generated.resources.compose_auth_continue_without_account
import nuvio.composeapp.generated.resources.compose_auth_create_account
import nuvio.composeapp.generated.resources.compose_auth_dont_have_account
import nuvio.composeapp.generated.resources.compose_auth_email
import nuvio.composeapp.generated.resources.compose_auth_or_separator
import nuvio.composeapp.generated.resources.compose_auth_password
import nuvio.composeapp.generated.resources.compose_auth_sign_in
import nuvio.composeapp.generated.resources.compose_auth_sign_in_subtitle
import nuvio.composeapp.generated.resources.compose_auth_sign_up
import nuvio.composeapp.generated.resources.compose_auth_sign_up_subtitle
import nuvio.composeapp.generated.resources.compose_auth_store_locally
import nuvio.composeapp.generated.resources.compose_auth_tagline
import nuvio.composeapp.generated.resources.compose_auth_terms_link
import nuvio.composeapp.generated.resources.compose_auth_terms_prefix
import nuvio.composeapp.generated.resources.compose_auth_username
import nuvio.composeapp.generated.resources.compose_auth_welcome_back
import nuvio.composeapp.generated.resources.auth_legacy_account_found
import nuvio.composeapp.generated.resources.auth_turnstile_open
import nuvio.composeapp.generated.resources.auth_turnstile_reload
import nuvio.composeapp.generated.resources.auth_turnstile_title
import nuvio.composeapp.generated.resources.auth_turnstile_unavailable
import nuvio.composeapp.generated.resources.auth_turnstile_wait
import nuvio.composeapp.generated.resources.auth_error_email_taken
import nuvio.composeapp.generated.resources.auth_error_rate_limited
import nuvio.composeapp.generated.resources.auth_error_signup_failed
import nuvio.composeapp.generated.resources.auth_error_username_invalid
import nuvio.composeapp.generated.resources.auth_username_available
import nuvio.composeapp.generated.resources.auth_username_checking
import nuvio.composeapp.generated.resources.auth_username_hint
import nuvio.composeapp.generated.resources.auth_username_taken
import org.jetbrains.compose.resources.stringResource

internal val AuthTextPrimary = Color(0xFFF5F7F8)
internal val AuthTextSecondary = Color(0xFF969CA3)
private val AuthTextMuted = Color(0xFF6E7178)
private val AuthPrimaryButtonBackground = Color(0xFFF5F5F5)
private val AuthPrimaryButtonText = Color(0xFF111111)
internal val AuthFieldBackground = Color.White.copy(alpha = 0.04f)
private val AuthFieldBackgroundMobile = Color.White.copy(alpha = 0.035f)
internal val AuthFieldBorder = Color.White.copy(alpha = 0.08f)
private val AuthStatusOk = Color(0xFF4ADE80)
private val AuthStatusBad = Color(0xFFFF7A7A)
private val AuthPaneBackground = Color.White.copy(alpha = 0.022f)
private val AuthPaneBorder = Color.White.copy(alpha = 0.07f)
private val AuthDividerColor = Color.White.copy(alpha = 0.10f)
private val AuthSecondaryButtonBackground = Color.White.copy(alpha = 0.05f)
private val AuthSecondaryButtonBorder = Color.White.copy(alpha = 0.09f)

private data class AuthFormMetrics(
    val fieldHeight: Dp,
    val fieldHorizontalPadding: Dp,
    val iconSize: Dp,
    val passwordIconSize: Dp,
    val primaryTop: Dp,
    val primaryHeight: Dp,
    val toggleTop: Dp,
    val dividerTop: Dp,
    val secondaryTop: Dp,
    val secondaryHeight: Dp,
    val fieldBackground: Color,
)

private val MobileAuthFormMetrics = AuthFormMetrics(
    fieldHeight = 56.dp,
    fieldHorizontalPadding = 16.dp,
    iconSize = 19.dp,
    passwordIconSize = 20.dp,
    primaryTop = 22.dp,
    primaryHeight = 54.dp,
    toggleTop = 18.dp,
    dividerTop = 28.dp,
    secondaryTop = 24.dp,
    secondaryHeight = 54.dp,
    fieldBackground = AuthFieldBackgroundMobile,
)

private val LargeAuthFormMetrics = AuthFormMetrics(
    fieldHeight = 58.dp,
    fieldHorizontalPadding = 18.dp,
    iconSize = 20.dp,
    passwordIconSize = 21.dp,
    primaryTop = 24.dp,
    primaryHeight = 56.dp,
    toggleTop = 18.dp,
    dividerTop = 30.dp,
    secondaryTop = 26.dp,
    secondaryHeight = 56.dp,
    fieldBackground = AuthFieldBackground,
)

private fun largeAuthScale(screenWidth: Dp): Float =
    (screenWidth.value / 1194f).coerceIn(1f, 1.32f)

private fun largeAuthFormMetrics(scale: Float): AuthFormMetrics = LargeAuthFormMetrics.copy(
    fieldHeight = 58.dp * scale,
    fieldHorizontalPadding = 18.dp * scale,
    iconSize = 20.dp * scale,
    passwordIconSize = 21.dp * scale,
    primaryTop = 24.dp * scale,
    primaryHeight = 56.dp * scale,
    toggleTop = 18.dp * scale,
    dividerTop = 30.dp * scale,
    secondaryTop = 26.dp * scale,
    secondaryHeight = 56.dp * scale,
)

/** Halaman daftar resmi IDLIX, dipakai saat Turnstile tidak bisa dimuat di app. */
private const val IDLIX_SIGNUP_URL = "https://z2.idlixku.com/auth/register"

/** Widget verifikasi IDLIX. Dimuat langsung dari domain mereka supaya sitekey
 *  (yang mungkin dibatasi per-host) tetap valid; proxy bridge jadi cadangan. */
private const val TURNSTILE_DIRECT_URL = "https://api.idlixku.com/api/auth/turnstile/challenge?lang=id"
private const val TURNSTILE_PROXY_URL = "https://hoflix-api.siho.my.id/hf/auth/turnstile"

/** IDLIX: huruf, angka, _ atau -, panjang 4–15. Titik dan spasi ditolak. */
private val IdlixUsernamePattern = Regex("^[A-Za-z0-9_-]{4,15}$")
private const val IdlixUsernameMaxLength = 15

/** Status cek ketersediaan username di IDLIX sebelum tombol Daftar ditekan. */
private enum class UsernameCheck { Idle, Checking, Available, Taken, Unknown }

/** Status yang ditampilkan di bawah kolom username, plus penanda tombol. */
private data class UsernameStatus(
    val text: String? = null,
    val color: Color = AuthTextMuted,
    val taken: Boolean = false,
)

/** Buang karakter yang tidak diterima IDLIX saat menebak username dari email. */
private fun sanitizeUsernameInput(raw: String): String =
    raw.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(IdlixUsernameMaxLength)

/** Tebakan username dari bagian lokal email; kosong kalau tidak memenuhi aturan. */
private fun deriveUsername(email: String): String {
    val candidate = sanitizeUsernameInput(email.substringBefore('@'))
    return if (IdlixUsernamePattern.matches(candidate)) candidate else ""
}

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
) {
    val authError by AuthRepository.error.collectAsStateWithLifecycle()
    val serverConnectionState by ServerConnectionController.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    // Username IDLIX: dipakai untuk login juga, jadi user boleh memilih sendiri.
    // Selama belum disentuh manual, nilainya mengikuti email.
    var username by rememberSaveable { mutableStateOf("") }
    var usernameEdited by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var emailFieldBounds by remember { mutableStateOf<Rect?>(null) }
    var passwordFieldBounds by remember { mutableStateOf<Rect?>(null) }
    var showServerSheet by rememberSaveable { mutableStateOf(false) }
    var showOfficialServerDialog by rememberSaveable { mutableStateOf(false) }
    // Verifikasi Turnstile IDLIX dirender di dalam form; tokennya sekali pakai.
    var turnstileToken by remember { mutableStateOf("") }
    var turnstileState by remember { mutableStateOf("loading") }
    var turnstileReload by remember { mutableStateOf(0) }
    var turnstileUrl by remember { mutableStateOf(TURNSTILE_DIRECT_URL) }
    var authNotice by remember { mutableStateOf<String?>(null) }
    // Akun HOFLIX lama yang menempel pada email yang sedang diketik (mode daftar).
    var legacyAccount by remember { mutableStateOf<AccountGateway.LegacyAccount?>(null) }
    // Hasil cek username ke IDLIX; dipakai untuk mematikan tombol Daftar.
    var usernameCheck by remember { mutableStateOf(UsernameCheck.Idle) }
    // Dinaikkan saat daftar gagal, supaya username diperiksa ulang (mis. baru
    // saja dipakai orang lain di antara cek dan tombol ditekan).
    var usernameRecheck by remember { mutableStateOf(0) }

    val emailTakenText = stringResource(Res.string.auth_error_email_taken)
    val rateLimitedText = stringResource(Res.string.auth_error_rate_limited)
    val signupFailedText = stringResource(Res.string.auth_error_signup_failed)
    val turnstileWaitText = stringResource(Res.string.auth_turnstile_wait)
    val usernameInvalidText = stringResource(Res.string.auth_error_username_invalid)
    val usernameTakenText = stringResource(Res.string.auth_username_taken)

    // Deteksi awal sebelum menekan Daftar: email ini sudah punya akun HOFLIX
    // lama? Diberitahukan lebih dulu supaya user tidak mengira datanya hilang.
    LaunchedEffect(email, isSignUp) {
        legacyAccount = null
        if (!isSignUp) return@LaunchedEffect
        val candidate = email.trim()
        val domain = candidate.substringAfter('@', "")
        if (domain.length < 3 || !domain.contains('.')) return@LaunchedEffect
        delay(700)
        legacyAccount = AccountGateway.lookupLegacyAccount(candidate).getOrNull()
    }

    val detectedLegacy = legacyAccount
    val legacyNotice: String? = if (detectedLegacy != null) {
        stringResource(Res.string.auth_legacy_account_found)
    } else {
        null
    }

    // Cek ketersediaan username sebelum Daftar ditekan: satu request per jeda
    // ketik, jadi user tidak perlu bolak-balik mengisi nama yang sudah dipakai.
    LaunchedEffect(username, isSignUp, usernameRecheck) {
        if (!isSignUp) {
            usernameCheck = UsernameCheck.Idle
            return@LaunchedEffect
        }
        val candidate = username.trim()
        if (!IdlixUsernamePattern.matches(candidate)) {
            usernameCheck = UsernameCheck.Idle
            return@LaunchedEffect
        }
        usernameCheck = UsernameCheck.Checking
        delay(450)
        val result = AccountGateway.checkUsername(candidate)
        usernameCheck = result.fold(
            onSuccess = {
                if (it == AccountGateway.UsernameAvailability.TAKEN) UsernameCheck.Taken
                else UsernameCheck.Available
            },
            onFailure = { UsernameCheck.Unknown },
        )
    }

    val usernameStatusText: String? = when (usernameCheck) {
        UsernameCheck.Checking -> stringResource(Res.string.auth_username_checking)
        UsernameCheck.Available -> stringResource(Res.string.auth_username_available)
        UsernameCheck.Taken -> stringResource(Res.string.auth_username_taken)
        UsernameCheck.Idle, UsernameCheck.Unknown -> null
    }
    val usernameStatusColor = when (usernameCheck) {
        UsernameCheck.Available -> AuthStatusOk
        UsernameCheck.Taken -> AuthStatusBad
        else -> AuthTextMuted
    }
    val usernameStatus = UsernameStatus(
        text = usernameStatusText,
        color = usernameStatusColor,
        taken = usernameCheck == UsernameCheck.Taken,
    )

    val turnstileUnavailable = turnstileState == "unavailable" ||
        turnstileState == "disabled" ||
        turnstileState == "error"

    fun reloadTurnstile() {
        turnstileToken = ""
        turnstileState = "loading"
        turnstileReload += 1
    }

    fun handleTurnstileMessage(message: TurnstileMessage) {
        if (message.type == "token") {
            turnstileToken = message.token
            turnstileState = "token"
            return
        }
        // Gagal memuat langsung dari domain IDLIX: coba sekali lewat proxy
        // bridge sebelum menyerahkan user ke jalur browser.
        val loadFailure = message.type == "unavailable" ||
            message.type == "error" ||
            message.type == "disabled"
        if (loadFailure && turnstileUrl == TURNSTILE_DIRECT_URL) {
            turnstileUrl = TURNSTILE_PROXY_URL
            reloadTurnstile()
            return
        }
        turnstileToken = ""
        turnstileState = message.type
    }

    fun handleEmailChange(value: String) {
        email = value
        // Selama username belum dipilih sendiri, ikuti email supaya user tidak
        // perlu mengisi dua kali. Begitu diedit manual, itu tidak ditimpa lagi.
        if (isSignUp && !usernameEdited) username = deriveUsername(value)
        AuthRepository.clearError()
    }

    fun handleUsernameChange(value: String) {
        username = sanitizeUsernameInput(value)
        usernameEdited = true
        AuthRepository.clearError()
    }

    fun submitAuth() {
        if (email.isBlank() || password.length < 6 || isLoading) return
        val cleanUsername = username.trim()
        if (isSignUp && cleanUsername.isNotEmpty() && !IdlixUsernamePattern.matches(cleanUsername)) {
            authNotice = usernameInvalidText
            return
        }
        if (isSignUp && usernameCheck == UsernameCheck.Taken) {
            authNotice = usernameTakenText
            return
        }
        if (isSignUp && turnstileToken.isBlank() && !turnstileUnavailable) {
            authNotice = turnstileWaitText
            return
        }
        isLoading = true
        focusManager.clearFocus(force = true)
        authNotice = null
        scope.launch {
            if (isSignUp) {
                val result = AuthRepository.signUpWithEmail(
                    email.trim(),
                    password,
                    cleanUsername,
                    turnstileToken,
                )
                // Token Turnstile sekali pakai: setelah percobaan apa pun,
                // verifikasi baru diminta supaya tidak mengirim token basi.
                if (result.isFailure) {
                    reloadTurnstile()
                    usernameRecheck += 1
                }
            } else {
                AuthRepository.signInWithEmail(email.trim(), password)
            }
            isLoading = false
        }
    }

    fun toggleAuthMode() {
        isSignUp = !isSignUp
        authNotice = null
        turnstileToken = ""
        if (isSignUp && username.isBlank()) username = deriveUsername(email)
        AuthRepository.clearError()
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(serverConnectionState.activeServer.backendUrl) {
        if (!serverConnectionState.activeServer.isCustom) {
            showOfficialServerDialog = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(emailFieldBounds, passwordFieldBounds) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val tappedTextField = listOfNotNull(emailFieldBounds, passwordFieldBounds)
                        .any { bounds -> bounds.contains(down.position) }
                    if (!tappedTextField) {
                        focusManager.clearFocus(force = true)
                    }
                }
            },
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val screenWidth = maxWidth
            val largeScreen = screenWidth >= 900.dp
            val compactLargeScreen = screenWidth < 1100.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .authGradientBackground(largeScreen = largeScreen),
            ) {
                if (largeScreen) {
                    val largeScale = largeAuthScale(screenWidth)
                    val formPaneWidth = (screenWidth * 0.30f).coerceIn(460.dp * largeScale, 720.dp * largeScale)
                    val brandHorizontalPadding = (screenWidth * 0.055f).coerceIn(56.dp * largeScale, 128.dp * largeScale)
                    val formHorizontalPadding = (formPaneWidth * 0.14f).coerceIn(48.dp, 96.dp)
                    val formMetrics = largeAuthFormMetrics(largeScale)

                    AuthLargeLayout(
                        modifier = Modifier.fillMaxSize(),
                        isSignUp = isSignUp,
                        email = email,
                        username = username,
                        password = password,
                        passwordVisible = passwordVisible,
                        isLoading = isLoading,
                        authError = authNotice ?: authError,
                        legacyNotice = legacyNotice,
                        usernameStatus = usernameStatus,
                        turnstileToken = turnstileToken,
                        turnstileState = turnstileState,
                        turnstileReload = turnstileReload,
                        turnstileUrl = turnstileUrl,
                        onTurnstileMessage = ::handleTurnstileMessage,
                        onTurnstileReload = ::reloadTurnstile,
                        onOpenSignupPage = { runCatching { uriHandler.openUri(IDLIX_SIGNUP_URL) } },
                        formPaneWidth = if (compactLargeScreen) 460.dp else formPaneWidth,
                        brandHorizontalPadding = brandHorizontalPadding,
                        formHorizontalPadding = formHorizontalPadding,
                        formMetrics = formMetrics,
                        scale = largeScale,
                        onEmailChange = ::handleEmailChange,
                        onUsernameChange = ::handleUsernameChange,
                        onPasswordChange = {
                            password = it
                            AuthRepository.clearError()
                        },
                        onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
                        onSubmit = ::submitAuth,
                        onToggleAuthMode = ::toggleAuthMode,
                        onEmailBoundsChange = { emailFieldBounds = it },
                        onPasswordBoundsChange = { passwordFieldBounds = it },
                    )
                } else {
                    AuthMobileLayout(
                        isSignUp = isSignUp,
                        email = email,
                        username = username,
                        password = password,
                        passwordVisible = passwordVisible,
                        isLoading = isLoading,
                        authError = authNotice ?: authError,
                        legacyNotice = legacyNotice,
                        usernameStatus = usernameStatus,
                        turnstileToken = turnstileToken,
                        turnstileState = turnstileState,
                        turnstileReload = turnstileReload,
                        turnstileUrl = turnstileUrl,
                        onTurnstileMessage = ::handleTurnstileMessage,
                        onTurnstileReload = ::reloadTurnstile,
                        onOpenSignupPage = { runCatching { uriHandler.openUri(IDLIX_SIGNUP_URL) } },
                        statusBarTop = statusBarTop,
                        onEmailChange = ::handleEmailChange,
                        onUsernameChange = ::handleUsernameChange,
                        onPasswordChange = {
                            password = it
                            AuthRepository.clearError()
                        },
                        onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
                        onSubmit = ::submitAuth,
                        onToggleAuthMode = ::toggleAuthMode,
                        onEmailBoundsChange = { emailFieldBounds = it },
                        onPasswordBoundsChange = { passwordFieldBounds = it },
                    )
                }
            }
        }

        if (AppFeaturePolicy.customServerConnectionsEnabled) {
            ServerConnectionMenu(
                activeServer = serverConnectionState.activeServer,
                onUseOfficial = {
                    ServerConnectionController.resetDiscovery()
                    showOfficialServerDialog = true
                },
                onConnectCustom = {
                    ServerConnectionController.resetDiscovery()
                    showServerSheet = true
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = statusBarTop + 4.dp),
            )
        }
    }

    if (
        AppFeaturePolicy.customServerConnectionsEnabled &&
        showServerSheet &&
        serverConnectionState.discoveredServer == null
    ) {
        ServerConnectionSheet(
            state = serverConnectionState,
            onDiscover = ServerConnectionController::discover,
            onDismiss = {
                showServerSheet = false
                ServerConnectionController.resetDiscovery()
            },
        )
    }

    serverConnectionState.discoveredServer?.let { server ->
        if (AppFeaturePolicy.customServerConnectionsEnabled) {
            ServerTrustDialog(
                server = server,
                isSwitching = serverConnectionState.isSwitching,
                switchFailure = serverConnectionState.switchFailure,
                onConfirm = ServerConnectionController::connectDiscovered,
                onDismiss = {
                    showServerSheet = false
                    ServerConnectionController.resetDiscovery()
                },
            )
        }
    }

    if (AppFeaturePolicy.customServerConnectionsEnabled && showOfficialServerDialog) {
        OfficialServerDialog(
            isSwitching = serverConnectionState.isSwitching,
            switchFailure = serverConnectionState.switchFailure,
            onConfirm = ServerConnectionController::useOfficial,
            onDismiss = {
                showOfficialServerDialog = false
                ServerConnectionController.resetDiscovery()
            },
        )
    }
}

@Composable
private fun AuthMobileLayout(
    isSignUp: Boolean,
    email: String,
    username: String,
    password: String,
    passwordVisible: Boolean,
    isLoading: Boolean,
    authError: String?,
    legacyNotice: String?,
    usernameStatus: UsernameStatus = UsernameStatus(),
    turnstileToken: String,
    turnstileState: String,
    turnstileReload: Int,
    turnstileUrl: String,
    onTurnstileMessage: (TurnstileMessage) -> Unit,
    onTurnstileReload: () -> Unit,
    onOpenSignupPage: () -> Unit,
    statusBarTop: Dp,
    onEmailChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onSubmit: () -> Unit,
    onToggleAuthMode: () -> Unit,
    onEmailBoundsChange: (Rect) -> Unit,
    onPasswordBoundsChange: (Rect) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 30.dp,
                top = statusBarTop + 48.dp,
                end = 30.dp,
                bottom = 40.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 342.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuthBrandLockup(logoHeight = 38.dp)

            Spacer(modifier = Modifier.height(64.dp))

            AuthHeading(
                isSignUp = isSignUp,
                headingStyle = MaterialTheme.typography.headlineLarge.copy(
                    color = AuthTextPrimary,
                    fontSize = 28.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                subtitleStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = AuthTextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )

            Spacer(modifier = Modifier.height(28.dp))

            AuthForm(
                isSignUp = isSignUp,
                email = email,
                username = username,
                password = password,
                passwordVisible = passwordVisible,
                isLoading = isLoading,
                authError = authError,
                legacyNotice = legacyNotice,
                usernameStatus = usernameStatus,
                turnstileToken = turnstileToken,
                turnstileState = turnstileState,
                turnstileReload = turnstileReload,
                turnstileUrl = turnstileUrl,
                onTurnstileMessage = onTurnstileMessage,
                onTurnstileReload = onTurnstileReload,
                onOpenSignupPage = onOpenSignupPage,
                metrics = MobileAuthFormMetrics,
                onEmailChange = onEmailChange,
                onUsernameChange = onUsernameChange,
                onPasswordChange = onPasswordChange,
                onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                onSubmit = onSubmit,
                onToggleAuthMode = onToggleAuthMode,
                onEmailBoundsChange = onEmailBoundsChange,
                onPasswordBoundsChange = onPasswordBoundsChange,
            )
        }
    }
}

@Composable
private fun AuthLargeLayout(
    modifier: Modifier = Modifier,
    isSignUp: Boolean,
    email: String,
    username: String,
    password: String,
    passwordVisible: Boolean,
    isLoading: Boolean,
    authError: String?,
    legacyNotice: String?,
    usernameStatus: UsernameStatus = UsernameStatus(),
    turnstileToken: String,
    turnstileState: String,
    turnstileReload: Int,
    turnstileUrl: String,
    onTurnstileMessage: (TurnstileMessage) -> Unit,
    onTurnstileReload: () -> Unit,
    onOpenSignupPage: () -> Unit,
    formPaneWidth: Dp,
    brandHorizontalPadding: Dp,
    formHorizontalPadding: Dp,
    formMetrics: AuthFormMetrics,
    scale: Float,
    onEmailChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onSubmit: () -> Unit,
    onToggleAuthMode: () -> Unit,
    onEmailBoundsChange: (Rect) -> Unit,
    onPasswordBoundsChange: (Rect) -> Unit,
) {
    Row(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = brandHorizontalPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            AppBrandWordmark(
                contentDescription = null,
                modifier = Modifier.height(60.dp * scale),
            )
            Spacer(modifier = Modifier.height(32.dp * scale))
            Text(
                text = stringResource(Res.string.compose_auth_tagline),
                modifier = Modifier.widthIn(max = 440.dp * scale),
                style = MaterialTheme.typography.displayLarge.copy(
                    color = AuthTextPrimary,
                    fontSize = (40f * scale).sp,
                    lineHeight = (45f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(modifier = Modifier.height(18.dp * scale))
            Text(
                text = stringResource(Res.string.compose_auth_sign_up_subtitle),
                modifier = Modifier.widthIn(max = 400.dp * scale),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = AuthTextSecondary,
                    fontSize = (17f * scale).sp,
                    lineHeight = (26f * scale).sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }

        Box(
            modifier = Modifier
                .width(formPaneWidth)
                .fillMaxHeight()
                .background(AuthPaneBackground)
                .drawBehind {
                    drawLine(
                        color = AuthPaneBorder,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = formHorizontalPadding),
            ) {
                AuthHeading(
                    isSignUp = isSignUp,
                    headingStyle = MaterialTheme.typography.headlineLarge.copy(
                        color = AuthTextPrimary,
                        fontSize = (30f * scale).sp,
                        lineHeight = (33f * scale).sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    subtitleStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = AuthTextSecondary,
                        fontSize = (15f * scale).sp,
                        lineHeight = (21f * scale).sp,
                        fontWeight = FontWeight.Normal,
                    ),
                )

                Spacer(modifier = Modifier.height(32.dp * scale))

                AuthForm(
                    isSignUp = isSignUp,
                    email = email,
                    username = username,
                    password = password,
                    passwordVisible = passwordVisible,
                    isLoading = isLoading,
                    authError = authError,
                    legacyNotice = legacyNotice,
                    usernameStatus = usernameStatus,
                    turnstileToken = turnstileToken,
                    turnstileState = turnstileState,
                    turnstileReload = turnstileReload,
                    turnstileUrl = turnstileUrl,
                    onTurnstileMessage = onTurnstileMessage,
                    onTurnstileReload = onTurnstileReload,
                    onOpenSignupPage = onOpenSignupPage,
                    metrics = formMetrics,
                    scale = scale,
                    onEmailChange = onEmailChange,
                    onUsernameChange = onUsernameChange,
                    onPasswordChange = onPasswordChange,
                    onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                    onSubmit = onSubmit,
                    onToggleAuthMode = onToggleAuthMode,
                    onEmailBoundsChange = onEmailBoundsChange,
                    onPasswordBoundsChange = onPasswordBoundsChange,
                )
            }
        }
    }
}

@Composable
private fun AuthBrandLockup(
    logoHeight: Dp,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBrandWordmark(
            contentDescription = null,
            modifier = Modifier.height(logoHeight),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(Res.string.compose_auth_tagline),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AuthTextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun AuthHeading(
    isSignUp: Boolean,
    headingStyle: TextStyle,
    subtitleStyle: TextStyle,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedContent(
            targetState = isSignUp,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "authHeading",
        ) { signUp ->
            Text(
                text = if (signUp) stringResource(Res.string.compose_auth_create_account)
                else stringResource(Res.string.compose_auth_welcome_back),
                style = headingStyle,
                color = AuthTextPrimary,
            )
        }
        AnimatedContent(
            targetState = isSignUp,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "authSubtitle",
        ) { signUp ->
            Text(
                text = if (signUp) stringResource(Res.string.compose_auth_sign_up_subtitle)
                else stringResource(Res.string.compose_auth_sign_in_subtitle),
                style = subtitleStyle,
                color = AuthTextSecondary,
            )
        }
    }
}

@Composable
private fun AuthForm(
    isSignUp: Boolean,
    email: String,
    username: String,
    password: String,
    passwordVisible: Boolean,
    isLoading: Boolean,
    authError: String?,
    legacyNotice: String?,
    usernameStatus: UsernameStatus = UsernameStatus(),
    turnstileToken: String,
    turnstileState: String,
    turnstileReload: Int,
    turnstileUrl: String,
    onTurnstileMessage: (TurnstileMessage) -> Unit,
    onTurnstileReload: () -> Unit,
    onOpenSignupPage: () -> Unit,
    metrics: AuthFormMetrics,
    scale: Float = 1f,
    onEmailChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onSubmit: () -> Unit,
    onToggleAuthMode: () -> Unit,
    onEmailBoundsChange: (Rect) -> Unit,
    onPasswordBoundsChange: (Rect) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        AuthTextField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = stringResource(Res.string.compose_auth_email),
            icon = Icons.Rounded.Email,
            metrics = metrics,
            scale = scale,
            modifier = Modifier.onGloballyPositioned { coordinates ->
                onEmailBoundsChange(coordinates.boundsInRoot())
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )

        legacyNotice?.let { notice ->
            Spacer(modifier = Modifier.height(10.dp * scale))
            Text(
                text = notice,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AuthTextSecondary,
                    fontSize = (12.5f * scale).sp,
                    lineHeight = (17f * scale).sp,
                ),
            )
        }

        // Username hanya untuk pendaftaran: IDLIX memakainya sebagai salah satu
        // cara login, jadi user perlu melihat dan boleh mengubahnya.
        if (isSignUp) {
            Spacer(modifier = Modifier.height(14.dp))
            AuthTextField(
                value = username,
                onValueChange = onUsernameChange,
                placeholder = stringResource(Res.string.compose_auth_username),
                icon = Icons.Rounded.Person,
                metrics = metrics,
                scale = scale,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
            )
            Spacer(modifier = Modifier.height(6.dp * scale))
            val statusText = usernameStatus.text
            if (statusText != null) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = usernameStatus.color,
                        fontSize = (12f * scale).sp,
                        lineHeight = (16f * scale).sp,
                    ),
                )
            } else {
                Text(
                    text = stringResource(Res.string.auth_username_hint),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AuthTextMuted,
                        fontSize = (12f * scale).sp,
                        lineHeight = (16f * scale).sp,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        AuthTextField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = stringResource(Res.string.compose_auth_password),
            icon = Icons.Rounded.Lock,
            metrics = metrics,
            scale = scale,
            isPassword = true,
            passwordVisible = passwordVisible,
            onPasswordVisibilityToggle = onPasswordVisibilityToggle,
            modifier = Modifier.onGloballyPositioned { coordinates ->
                onPasswordBoundsChange(coordinates.boundsInRoot())
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSubmit() },
            ),
        )

        if (isSignUp) {
            Spacer(modifier = Modifier.height(14.dp))
            AuthTurnstileField(
                token = turnstileToken,
                url = turnstileUrl,
                state = turnstileState,
                reloadKey = turnstileReload,
                scale = scale,
                metrics = metrics,
                onMessage = onTurnstileMessage,
                onReload = onTurnstileReload,
                onOpenSignupPage = onOpenSignupPage,
            )
        }

        authError?.let { errorText ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.error,
                    fontSize = (13f * scale).sp,
                    lineHeight = (18f * scale).sp,
                ),
            )
        }

        if (isSignUp) {
            Spacer(modifier = Modifier.height(14.dp * scale))
            AuthTermsAcknowledgement(scale = scale)
        }

        Spacer(modifier = Modifier.height(metrics.primaryTop))

        AuthPrimaryButton(
            text = if (isSignUp) stringResource(Res.string.compose_auth_create_account)
            else stringResource(Res.string.compose_auth_sign_in),
            isLoading = isLoading,
            // Username yang sudah terbukti dipakai tidak boleh dikirim: percuma
            // menghabiskan satu token Turnstile dan satu putaran isi ulang.
            enabled = !isLoading && !(isSignUp && usernameStatus.taken),
            height = metrics.primaryHeight,
            scale = scale,
            onClick = onSubmit,
        )

        Spacer(modifier = Modifier.height(metrics.toggleTop))

        AuthModeToggle(
            isSignUp = isSignUp,
            scale = scale,
            onToggleAuthMode = onToggleAuthMode,
        )

    }
}

@Composable
private fun AuthTurnstileField(
    token: String,
    url: String,
    state: String,
    reloadKey: Int,
    scale: Float,
    metrics: AuthFormMetrics,
    onMessage: (TurnstileMessage) -> Unit,
    onReload: () -> Unit,
    onOpenSignupPage: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val blocked = state == "unavailable" || state == "disabled" || state == "error"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(metrics.fieldBackground)
                .border(1.dp, AuthFieldBorder, shape),
        ) {
            // Token Turnstile sekali pakai; WebView dibangun ulang setiap kali
            // verifikasi perlu diminta lagi.
            key(reloadKey) {
                IdlixTurnstileBox(
                    url = url,
                    modifier = Modifier.fillMaxWidth(),
                    onMessage = onMessage,
                )
            }
        }

        if (blocked) {
            Text(
                text = stringResource(Res.string.auth_turnstile_unavailable),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AuthTextSecondary,
                    fontSize = (13f * scale).sp,
                    lineHeight = (18f * scale).sp,
                ),
            )
            Text(
                text = stringResource(Res.string.auth_turnstile_open),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenSignupPage() }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AuthTextPrimary,
                    fontSize = (13f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        } else {
            // Widget sudah menampilkan tanda centang saat token terbit; label
            // di bawahnya hanya pengantar sebelum verifikasi selesai.
            if (token.isBlank()) {
                Text(
                    text = if (state == "expired") {
                        stringResource(Res.string.auth_turnstile_reload)
                    } else {
                        stringResource(Res.string.auth_turnstile_title)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = state == "expired") { onReload() }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AuthTextSecondary,
                        fontSize = (13f * scale).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AuthTermsAcknowledgement(
    scale: Float,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.compose_auth_terms_prefix),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AuthTextSecondary,
                fontSize = (13f * scale).sp,
                lineHeight = (18f * scale).sp,
                fontWeight = FontWeight.Normal,
            ),
        )
        Spacer(modifier = Modifier.width(4.dp * scale))
        Text(
            text = stringResource(Res.string.compose_auth_terms_link),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AuthTextPrimary,
                fontSize = (13f * scale).sp,
                lineHeight = (18f * scale).sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    metrics: AuthFormMetrics,
    scale: Float,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordVisibilityToggle: () -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.fieldHeight)
            .background(metrics.fieldBackground, shape)
            .border(1.dp, AuthFieldBorder, shape)
            .padding(horizontal = metrics.fieldHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(metrics.iconSize),
            tint = AuthTextMuted,
        )
        Spacer(modifier = Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = AuthTextPrimary,
                fontSize = (16f * scale).sp,
                lineHeight = (22f * scale).sp,
                fontWeight = FontWeight.Normal,
            ),
            cursorBrush = SolidColor(AuthTextPrimary),
            visualTransformation = if (isPassword && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = AuthTextMuted,
                                fontSize = (16f * scale).sp,
                                lineHeight = (22f * scale).sp,
                                fontWeight = FontWeight.Normal,
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (isPassword) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .clickable(onClick = onPasswordVisibilityToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff
                    else Icons.Rounded.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(metrics.passwordIconSize),
                    tint = AuthTextSecondary,
                )
            }
        }
    }
}

@Composable
internal fun AuthPrimaryButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    height: Dp,
    scale: Float,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AuthPrimaryButtonBackground,
            contentColor = AuthPrimaryButtonText,
            disabledContainerColor = if (isLoading) {
                AuthPrimaryButtonBackground
            } else {
                AuthPrimaryButtonBackground.copy(alpha = 0.45f)
            },
            disabledContentColor = AuthPrimaryButtonText.copy(alpha = 0.55f),
        ),
    ) {
        if (isLoading) {
            NuvioLoadingIndicator(
                modifier = Modifier.size(20.dp * scale),
                color = AuthPrimaryButtonText,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = (16f * scale).sp,
                    lineHeight = (22f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

@Composable
private fun AuthModeToggle(
    isSignUp: Boolean,
    scale: Float,
    onToggleAuthMode: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = isSignUp,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "authTogglePrompt",
        ) { signUp ->
            Text(
                text = if (signUp) {
                    stringResource(Res.string.compose_auth_already_have_account).trimEnd()
                } else {
                    stringResource(Res.string.compose_auth_dont_have_account).trimEnd()
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AuthTextSecondary,
                    fontSize = (14f * scale).sp,
                    lineHeight = (20f * scale).sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        AnimatedContent(
            targetState = isSignUp,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "authToggleAction",
        ) { signUp ->
            Text(
                text = if (signUp) stringResource(Res.string.compose_auth_sign_in)
                else stringResource(Res.string.compose_auth_sign_up),
                modifier = Modifier.clickable(onClick = onToggleAuthMode),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AuthTextPrimary,
                    fontSize = (14f * scale).sp,
                    lineHeight = (20f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

@Composable
private fun AuthDivider(scale: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AuthDividerColor),
        )
        Text(
            text = stringResource(Res.string.compose_auth_or_separator).trim(),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AuthTextMuted,
                fontSize = (13f * scale).sp,
                lineHeight = (18f * scale).sp,
                fontWeight = FontWeight.Normal,
            ),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AuthDividerColor),
        )
    }
}

@Composable
internal fun AuthSecondaryButton(
    text: String,
    enabled: Boolean,
    height: Dp,
    scale: Float,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AuthSecondaryButtonBorder),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AuthSecondaryButtonBackground,
            contentColor = AuthTextPrimary,
            disabledContainerColor = AuthSecondaryButtonBackground.copy(alpha = 0.45f),
            disabledContentColor = AuthTextPrimary.copy(alpha = 0.55f),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = (15f * scale).sp,
                lineHeight = (21f * scale).sp,
                fontWeight = FontWeight.Medium,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

private fun Modifier.authGradientBackground(largeScreen: Boolean): Modifier = drawWithCache {
    val angleDegrees = if (largeScreen) 122.0 else 148.0
    val angleRadians = angleDegrees * PI / 180.0
    val directionX = sin(angleRadians).toFloat()
    val directionY = (-cos(angleRadians)).toFloat()
    val halfLength = (abs(size.width * directionX) + abs(size.height * directionY)) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val start = Offset(
        x = center.x - directionX * halfLength,
        y = center.y - directionY * halfLength,
    )
    val end = Offset(
        x = center.x + directionX * halfLength,
        y = center.y + directionY * halfLength,
    )
    val colorStops = if (largeScreen) {
        arrayOf(
            0f to Color(0xFF21113B),
            0.14f to Color(0xFF21113B),
            0.26f to Color(0xFF1A0E2F),
            0.36f to Color(0xFF130A23),
            0.48f to Color(0xFF0A060F),
            0.60f to Color(0xFF050408),
            0.70f to Color.Black,
            1f to Color.Black,
        )
    } else {
        arrayOf(
            0f to Color(0xFF21113B),
            0.12f to Color(0xFF21113B),
            0.24f to Color(0xFF1A0E2F),
            0.34f to Color(0xFF130A23),
            0.44f to Color(0xFF0A060F),
            0.58f to Color(0xFF050408),
            0.64f to Color.Black,
            1f to Color.Black,
        )
    }
    val brush = Brush.linearGradient(
        colorStops = colorStops,
        start = start,
        end = end,
    )
    onDrawBehind {
        drawRect(brush = brush)
    }
}

private fun LayoutCoordinates.boundsInRoot(): Rect {
    val position = positionInRoot()
    return Rect(
        left = position.x,
        top = position.y,
        right = position.x + size.width,
        bottom = position.y + size.height,
    )
}
