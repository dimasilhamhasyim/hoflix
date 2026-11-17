package com.nuvio.app.features.auth

import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Kontrak sesi Supabase dari bridge.
 *
 * Bridge menukar password turunan menjadi sesi GoTrue lalu mengirimkannya apa
 * adanya. Aplikasi mengimpornya lewat `auth.importSession(...)`. Kalau bentuk
 * JSON ini tidak bisa didekode menjadi [UserSession], login berhasil di server
 * tapi sesi sinkron tidak pernah terbentuk — jadi bentuknya dipaku di sini.
 */
class SupabaseSessionDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Bentuk nyata dari `POST /auth/v1/token?grant_type=password`. */
    private val gotrueSession = """
        {
          "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature",
          "token_type": "bearer",
          "expires_in": 3600,
          "expires_at": 1794713000,
          "refresh_token": "abc123def4",
          "user": {
            "id": "4f873d76-1111-2222-3333-444455556666",
            "aud": "authenticated",
            "role": "authenticated",
            "email": "user@example.com",
            "email_confirmed_at": "2026-11-13T10:00:00Z",
            "phone": "",
            "created_at": "2026-11-13T10:00:00Z",
            "updated_at": "2026-11-15T10:00:00Z",
            "app_metadata": {"provider": "email", "providers": ["email"]},
            "user_metadata": {},
            "identities": []
          },
          "weak_password": null
        }
    """.trimIndent()

    @Test
    fun `decodes the session bridge sends`() {
        val session = json.decodeFromString<UserSession>(gotrueSession)
        assertEquals("abc123def4", session.refreshToken)
        assertEquals(3600L, session.expiresIn)
        assertNotNull(session.accessToken)
        assertNotNull(session.user)
        assertEquals("user@example.com", session.user?.email)
    }

    @Test
    fun `grant keeps the session json verbatim`() {
        val grant = AccountGateway.AccountGrant(
            token = "identity",
            supabaseEmail = "user@example.com",
            supabaseSessionJson = gotrueSession,
            displayName = "SiHo101",
        )
        val decoded = json.decodeFromString<UserSession>(grant.supabaseSessionJson)
        assertEquals("abc123def4", decoded.refreshToken)
    }
}
