package dev.longdo.zalox.hooks

import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import dev.longdo.zalox.ZaloXEntry.Companion.TAG
import dev.longdo.zalox.ZaloXPrefs

/**
 * Anti-Typing: Prevents "đang soạn tin..." (typing indicator) from reaching others.
 *
 * ─── Findings ─────────────────────────────────────────────────────────────
 * No direct "typing" / "isTyping" string was found in Zalo 26.09.01 smali.
 * Zalo likely uses a numeric event code or binary socket frame for typing signals.
 *
 * Strategy (best-effort):
 *  1. Hook EditText.addTextChangedListener inside Zalo's process.
 *  2. Wrap every registered TextWatcher with a proxy that suppresses
 *     afterTextChanged callbacks ONLY for watchers declared in Zalo's own
 *     packages (com.zing.zalo.*).
 *     → The UI still updates normally (cursor, character count, etc.) because
 *       framework-level watchers are NOT suppressed.
 *  3. Most typing-indicator senders fire from afterTextChanged with a short
 *     debounce (300-500ms). Silencing this callback prevents the network call.
 *
 * TODO (post-JADX):
 *  Once JADX finishes decompiling, search for classes that schedule a
 *  Coroutine/Handler with ~300ms delay inside afterTextChanged. Hook that
 *  specific class directly for a more precise, zero-false-positive solution.
 * ─────────────────────────────────────────────────────────────────────────
 */
class TypingHook(
    private val lpparam: LoadPackageParam,
    private val prefs: ZaloXPrefs
) {

    fun hook() {
        // addTextChangedListener is defined on TextView (not EditText).
        // Hooking EditText causes NoSuchMethodError#exact because Xposed only
        // finds methods declared directly on that class, not inherited ones.
        XposedHelpers.findAndHookMethod(
            TextView::class.java,
            "addTextChangedListener",
            TextWatcher::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!prefs.antiTyping) return
                    val original = param.args[0] as? TextWatcher ?: return

                    // Only suppress watchers from Zalo's own code
                    val watcherPkg = original.javaClass.name
                    val isZaloWatcher = watcherPkg.startsWith("com.zing.zalo") ||
                                        watcherPkg.startsWith("com.zing.v4")   ||
                                        // Obfuscated packages often start with short names
                                        watcherPkg.length <= 10

                    if (!isZaloWatcher) return   // Leave framework/library watchers intact

                    param.args[0] = TypingBlockerProxy(original)
                    XposedBridge.log("$TAG: [TypingHook] wrapped watcher: $watcherPkg")
                }
            }
        )
    }

    /**
     * Proxy TextWatcher that passes through beforeTextChanged and onTextChanged
     * (needed for UI cursor logic) but silences afterTextChanged — which is where
     * typing-signal coroutines/handlers are typically scheduled.
     */
    private class TypingBlockerProxy(private val delegate: TextWatcher) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            delegate.beforeTextChanged(s, start, count, after)
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            delegate.onTextChanged(s, start, before, count)
        }

        override fun afterTextChanged(s: Editable?) {
            // Intentionally suppressed — this prevents the typing-indicator
            // network call while leaving the text input UI fully functional.
        }
    }
}
