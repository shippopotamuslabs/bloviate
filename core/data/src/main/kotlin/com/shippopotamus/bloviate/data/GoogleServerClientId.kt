package com.shippopotamus.bloviate.data

import javax.inject.Qualifier

/**
 * OAuth 2.0 **Web** client id used as the Credential Manager /
 * Google Sign-In `serverClientId` (ENC-197, adapted from Stratum's
 * `GoogleServerClientId`). Provided defensively: this repo's
 * `google-services.json` currently carries an empty `oauth_client`
 * list, so the generated `default_web_client_id` string may not exist.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GoogleServerClientId
