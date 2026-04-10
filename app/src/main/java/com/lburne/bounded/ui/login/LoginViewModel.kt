package com.lburne.bounded.ui.login

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(application)

    // Replace this with the Web Client ID from your google-services.json
    private val WEB_CLIENT_ID = "277007756222-o2sm59nvq58guod9vo4so65d66lbkgqu.apps.googleusercontent.com"

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun checkCurrentUser() {
        if (auth.currentUser != null) {
            _loginState.value = LoginState.Success
        }
    }

    fun signInWithGoogle(context: Context) {
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            try {
                val googleIdOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                // Unwrap the Context into an Activity to satisfy Google Play Services UI requirements
                var activityContext: Context = context
                while (activityContext is ContextWrapper) {
                    if (activityContext is Activity) break
                    activityContext = activityContext.baseContext
                }

                // WORKAROUND: Samsung Android 14 devices frequently throw TransactionTooLargeException
                // and crash the CredentialSelectorActivity if the calling Activity's Intent contains 
                // massive Navigation backstack bundles. We temporarily strip the extras to pass IPC limits.
                val activity = activityContext as? Activity
                val oldExtras = activity?.intent?.extras?.let { android.os.Bundle(it) }
                activity?.intent?.replaceExtras(android.os.Bundle())

                // Give the user 30 seconds to log in/type a new password
                val result = kotlinx.coroutines.withTimeoutOrNull(30000) {
                    credentialManager.getCredential(
                        request = request,
                        context = activityContext
                    )
                }

                // Restore the original Intent extras so Jetpack Navigation continues functioning
                activity?.intent?.replaceExtras(oldExtras ?: android.os.Bundle())

                if (result == null) {
                    _loginState.value = LoginState.Error("Google Play Services is taking too long to respond.")
                    return@launch
                }

                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken

                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                _loginState.value = LoginState.Success
                            } else {
                                _loginState.value = LoginState.Error(task.exception?.message ?: "Sign-in failed")
                            }
                        }
                } else {
                    _loginState.value = LoginState.Error("Unexpected credential type: ${credential.type}")
                }
            } catch (e: Throwable) {
                Log.e("LoginViewModel", "Sign-in Error", e)
                _loginState.value = LoginState.Error(e.localizedMessage ?: "Sign-in threw a fatal error.")
            }
        }
    }
}
