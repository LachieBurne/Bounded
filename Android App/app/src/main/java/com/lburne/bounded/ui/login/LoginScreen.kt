package com.lburne.bounded.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lburne.bounded.R
import com.lburne.bounded.ui.common.RiftLabelText
import com.lburne.bounded.ui.theme.ChamferShape
import com.lburne.bounded.ui.theme.ChamferSmall
import com.lburne.bounded.ui.theme.Rift
import com.lburne.bounded.ui.theme.RiftLabel

/** Card art behind the sign-in panel, the same one the web app's sign-in page uses. */
private const val BACKDROP_URL = "https://lachieburne.github.io/Bounded/card_images/OGN-205.webp"
private const val WEB_APP_URL = "https://bounded.web.app"

/**
 * Sign-in, in the Gilded Rift style and matching the web app's sign-in page: blurred card art
 * fading into ink, and a chamfered panel with the gem, the wordmark and the Google button.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val loginState by viewModel.loginState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        viewModel.checkCurrentUser()
    }

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            onLoginSuccess()
        }
    }

    Box(Modifier.fillMaxSize().background(Rift.Ink)) {
        Backdrop(Modifier.fillMaxSize())

        // Scrolls on short landscape screens, centred otherwise; phone width on tablets.
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            val viewportHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 440.dp)
                        .fillMaxWidth()
                        .clip(ChamferShape)
                        .background(Rift.Surface.copy(alpha = 0.94f))
                        .border(1.dp, Rift.Line, ChamferShape)
                        .padding(horizontal = 28.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // A soft gold light behind the gem.
                        Box(
                            Modifier
                                .size(150.dp)
                                .background(
                                    Brush.radialGradient(listOf(Rift.Gold.copy(alpha = 0.28f), Color.Transparent)),
                                    CircleShape
                                )
                        )
                        Image(
                            painter = painterResource(id = R.drawable.ic_gem),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(width = 72.dp, height = 84.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    RiftLabelText(
                        "Bounded",
                        style = RiftLabel.copy(fontSize = 40.sp, letterSpacing = 0.14.em)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Your Riftbound collection and decks, on the web and on your phone.",
                        color = Rift.Parchment,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(32.dp))

                    when (val state = loginState) {
                        is LoginState.Loading -> CircularProgressIndicator(color = Rift.Gold)
                        is LoginState.Error -> {
                            Text(
                                state.message,
                                color = Rift.Banned,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            GoogleSignInButton(onClick = { viewModel.signInWithGoogle(context) })
                        }
                        else -> GoogleSignInButton(onClick = { viewModel.signInWithGoogle(context) })
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FooterLink("Privacy Policy") { uriHandler.openUri("$WEB_APP_URL/privacy") }
                    FooterLink("Terms of Service") { uriHandler.openUri("$WEB_APP_URL/terms") }
                }
            }
        }
    }
}

/** Card art, zoomed and blurred, fading to ink. Unblurred under Android 12, behind a darker veil. */
@Composable
private fun Backdrop(modifier: Modifier) {
    val context = LocalContext.current
    Box(modifier.clip(RectangleShape)) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(BACKDROP_URL).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.5f,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.25f
                    scaleY = 1.25f
                }
                .blur(40.dp)
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Rift.Ink.copy(alpha = 0.4f),
                        0.5f to Rift.Ink.copy(alpha = 0.75f),
                        1f to Rift.Ink
                    )
                )
        )
    }
}

@Composable
fun GoogleSignInButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ChamferSmall,
        color = Rift.Ivory,
        border = BorderStroke(1.dp, Rift.Ivory),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google),
                contentDescription = null,
                tint = Color.Unspecified, // Keep Google's own colours.
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Continue with Google",
                color = Rift.InkText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FooterLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = Rift.Muted, fontSize = 14.sp)
    }
}
