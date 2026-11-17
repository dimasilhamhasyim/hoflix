package com.nuvio.app.features.account

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthStorage
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.profiles.ProfilePushPayload
import com.nuvio.app.features.profiles.ProfileRepository
import io.github.jan.supabase.auth.auth

/**
 * Menghubungkan sesi lokal (anonymous) ke akun Supabase sungguhan.
 *
 * Tanpa langkah ini, sesi email yang berhasil dibuat tidak pernah dipakai:
 * [AuthRepository] mengutamakan id anonymous yang tersimpan, sehingga app tetap
 * berjalan lokal dan tidak ada data yang naik ke server.
 */
object AccountLink {
    private val log = Logger.withTag("AccountLink")

    suspend fun afterSignIn(): Result<Unit> = runCatching {
        // Profil lokal diambil sebelum state akun masuk, kalau tidak datanya
        // sudah tertimpa oleh hasil pull server yang masih kosong.
        val localProfiles = ProfileRepository.state.value.profiles

        AuthStorage.clearAnonymousUserId()
        val user = SupabaseProvider.client.auth.currentUserOrNull()
            ?: error("Sesi tidak terbentuk setelah masuk")
        AuthRepository.markSignedInFromSession(userId = user.id, email = user.email)

        ProfileRepository.pullProfiles()
        if (ProfileRepository.state.value.profiles.isEmpty() && localProfiles.isNotEmpty()) {
            log.i { "Akun baru kosong — memindahkan ${localProfiles.size} profil lokal" }
            ProfileRepository.pushProfiles(
                localProfiles.map { profile ->
                    ProfilePushPayload(
                        profileIndex = profile.profileIndex,
                        name = profile.name,
                        avatarColorHex = profile.avatarColorHex,
                        usesPrimaryAddons = profile.usesPrimaryAddons,
                        usesPrimaryPlugins = profile.usesPrimaryPlugins,
                        avatarId = profile.avatarId,
                        avatarUrl = profile.avatarUrl,
                        profileBackgroundId = profile.profileBackgroundId,
                        profileBackgroundUrl = profile.profileBackgroundUrl,
                    )
                },
            )
        }
    }
}
