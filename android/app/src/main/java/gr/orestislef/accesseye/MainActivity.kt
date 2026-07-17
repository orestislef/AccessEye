//
//  MainActivity.kt
//  AccessEye
//
//  Single Compose activity. Port of AccessEyeApp.swift + Views/RootView.swift:
//  RootScreen decides which top-level screen to show based on whether the AI
//  model is available:
//    • Checking      → SplashScreen
//    • Available     → ContentScreen (the camera/describe screen)
//    • anything else → OnboardingScreen (download the model)
//
//  Deleting the model from Settings flips the state back to Missing, which
//  brings the user here to re-download — no app restart needed.
//
//  The ModelManager comes from the app-level container so the activity, the
//  ViewModel and the ModelDownloadService all observe the SAME instance.
//

package gr.orestislef.accesseye

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import gr.orestislef.accesseye.ai.ModelManager
import gr.orestislef.accesseye.support.LocalizedUI
import gr.orestislef.accesseye.ui.ContentScreen
import gr.orestislef.accesseye.ui.OnboardingScreen
import gr.orestislef.accesseye.ui.SplashScreen
import gr.orestislef.accesseye.ui.theme.AccessEyeTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Shared instance — the download service reports progress into it too.
        val modelManager = (application as AccessEyeApp).container.modelManager

        setContent {
            AccessEyeTheme {
                RootScreen(vm = vm, modelManager = modelManager)
            }
        }
    }
}

/**
 * Top-level screen switcher (port of RootView.swift). Also owns two app-wide
 * concerns that must live above every screen:
 *  • layout direction — the WHOLE UI mirrors when the chosen language is
 *    right-to-left (Arabic), independent of the device locale;
 *  • the TalkBack live-region announcer, so status announcements are heard on
 *    every screen (splash, onboarding and content alike).
 */
@Composable
fun RootScreen(vm: AppViewModel, modelManager: ModelManager) {
    val language by vm.language.collectAsStateWithLifecycle()
    val modelState by modelManager.state.collectAsStateWithLifecycle()
    val t = remember(language) { LocalizedUI.textFor(language) }

    // Mirror of RootView.onAppear { modelManager.refresh() }. Also hand the
    // root view to the ViewModel so pre-gesture haptics (model ready/failed on
    // first launch) have something to buzz through.
    val rootView = LocalView.current
    LaunchedEffect(Unit) {
        vm.attachView(rootView)
        modelManager.refresh()
    }

    val direction = if (language.isRightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when (modelState) {
                is ModelManager.State.Checking -> SplashScreen(t = t)
                is ModelManager.State.Available -> ContentScreen(vm = vm, modelManager = modelManager)
                else -> OnboardingScreen(modelManager = modelManager, t = t)
            }

            AccessibilityAnnouncer(vm)
        }
    }
}

/**
 * Invisible polite live region. `View.announceForAccessibility` is deprecated;
 * the supported way to make TalkBack speak a status update is to change the
 * text of a node marked as a live region. The node is 1dp and transparent-ink
 * (NOT zero-alpha — fully transparent layers can be pruned from the
 * accessibility tree) so it never interferes with the visual layout.
 */
@Composable
private fun AccessibilityAnnouncer(vm: AppViewModel) {
    val announcement by vm.accessibilityAnnouncement.collectAsStateWithLifecycle()

    // Let TalkBack deliver the message, then empty the node so a user swiping
    // through the screen can't land on a stale invisible status later.
    LaunchedEffect(announcement) {
        if (!announcement.isNullOrEmpty()) {
            delay(6_000)
            vm.clearAnnouncement()
        }
    }

    Text(
        text = announcement.orEmpty(),
        color = Color.Transparent,
        fontSize = 1.sp,
        maxLines = 1,
        modifier = Modifier
            .size(1.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
