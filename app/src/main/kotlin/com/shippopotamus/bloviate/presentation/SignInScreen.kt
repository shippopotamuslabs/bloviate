package com.shippopotamus.bloviate.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shippopotamus.bloviate.R

/**
 * The launcher gate's signed-out surface (ENC-197): shown instead of
 * the app shell until a Google session exists. Owns its own `Scaffold`
 * + `TopAppBar` like every other screen; the only action is the
 * Google sign-in flow, with its loading and error states inline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    isSigningIn: Boolean,
    signInError: String?,
    onSignInClick: () -> Unit,
    onErrorDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name)) },
            )
        },
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(BloviateSpacing.s24),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.signin_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(BloviateSpacing.s8))
            Text(
                text = stringResource(R.string.signin_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(BloviateSpacing.s24))
            Button(
                onClick = onSignInClick,
                enabled = !isSigningIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(PROGRESS_SIZE),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                    )
                }
                Text(
                    text = stringResource(R.string.signin_button),
                    modifier = Modifier.padding(start = BloviateSpacing.s8),
                )
            }
            if (signInError != null) {
                Spacer(modifier = Modifier.height(BloviateSpacing.s16))
                Text(
                    text = signInError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onErrorDismiss) {
                    Text(text = stringResource(R.string.signin_error_dismiss))
                }
            }
        }
    }
}

private val PROGRESS_SIZE: Dp = 20.dp

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "SignIn - Idle", showBackground = true)
@Composable
private fun SignInScreenIdlePreview() {
    PreviewTheme {
        SignInScreen(
            isSigningIn = false,
            signInError = null,
            onSignInClick = {},
            onErrorDismiss = {},
        )
    }
}

@Suppress("UnusedPrivateMember") // Invoked by the IDE preview renderer, not from code.
@Preview(name = "SignIn - Error", showBackground = true)
@Composable
private fun SignInScreenErrorPreview() {
    PreviewTheme {
        SignInScreen(
            isSigningIn = false,
            signInError = MSG_NETWORK,
            onSignInClick = {},
            onErrorDismiss = {},
        )
    }
}
