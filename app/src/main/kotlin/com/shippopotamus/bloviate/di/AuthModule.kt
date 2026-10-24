package com.shippopotamus.bloviate.di

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.data.GoogleAuthSession
import com.shippopotamus.bloviate.data.GoogleServerClientId
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the Google session (ENC-197): Credential Manager plus
 * Firebase behind [AuthSessionPort]. The server client id resolves
 * defensively — this repo's `google-services.json` currently carries
 * an empty `oauth_client` list, so the generated
 * `default_web_client_id` string may not exist. A static `R` reference
 * would not compile then; a blank id instead fails the sign-in attempt
 * with a user-facing "not configured" message, never a crash.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    abstract fun bindAuthSessionPort(session: GoogleAuthSession): AuthSessionPort

    companion object {

        @Provides
        @Singleton
        fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

        @Provides
        @Singleton
        fun provideCredentialManager(
            @ApplicationContext context: Context,
        ): CredentialManager = CredentialManager.create(context)

        @Provides
        @Singleton
        @GoogleServerClientId
        fun provideGoogleServerClientId(
            @ApplicationContext context: Context,
        ): String {
            val id =
                context.resources.getIdentifier(
                    "default_web_client_id",
                    "string",
                    context.packageName,
                )
            return if (id != 0) context.getString(id) else ""
        }
    }
}
