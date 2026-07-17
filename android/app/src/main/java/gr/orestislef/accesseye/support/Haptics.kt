//
//  Haptics.kt
//  AccessEye
//
//  Small wrapper for tactile feedback. For a user who can't see the screen,
//  haptics confirm that a tap registered and that work started/finished.
//  Uses View.performHapticFeedback so it works on budget devices and respects
//  the system haptics setting. (README §7.)
//

package gr.orestislef.accesseye.support

import android.view.HapticFeedbackConstants
import android.view.View

object Haptics {
    // CONFIRM/REJECT require API 30; minSdk is 31, so no runtime guards needed.

    /** A capture/tap was registered. */
    fun tap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Something completed successfully (model ready, description done). */
    fun success(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    /** Something went wrong. */
    fun error(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    /** A gentle cue, e.g. when a spoken description finishes. */
    fun soft(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
