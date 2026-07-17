//
//  SplashScreen.kt
//  AccessEye
//
//  Port of Views/SplashView.swift. Briefly shown while we check whether the
//  model is present. Kept simple and high-contrast: black screen, the app's
//  eye artwork, a spinner. To TalkBack the whole screen is ONE element that
//  reads "AccessEye is starting" (localized) — mirrors the iOS
//  .accessibilityElement(children: .ignore) + label.
//

package gr.orestislef.accesseye.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import gr.orestislef.accesseye.R
import gr.orestislef.accesseye.support.UiText

@Composable
fun SplashScreen(t: UiText) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // One a11y node for the whole splash; children are decorative.
            .clearAndSetSemantics { contentDescription = t.appStarting },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_eye_splash),
                contentDescription = null,
                modifier = Modifier.size(160.dp),
            )
            CircularProgressIndicator(color = Color.White)
        }
    }
}
