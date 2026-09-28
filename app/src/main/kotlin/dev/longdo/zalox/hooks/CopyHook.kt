package dev.longdo.zalox.hooks

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import dev.longdo.zalox.ZaloXEntry.Companion.TAG
import dev.longdo.zalox.ZaloXPrefs

/**
 * Copy Bypass: Allows copying text from copy-protected Zalo messages.
 *
 * Zalo disables long-press text selection on certain message types by
 * installing a no-op ActionMode.Callback via setCustomSelectionActionModeCallback().
 * This hook intercepts that call and replaces Zalo's restrictive callback
 * with a pass-through that re-enables standard text selection and copy.
 *
 * This is a stable hook (uses Android framework TextView class).
 */
class CopyHook(
    private val lpparam: LoadPackageParam,
    private val prefs: ZaloXPrefs
) {

    fun hook() {
        // Hook setCustomSelectionActionModeCallback — fired when Zalo tries
        // to replace the default selection menu (copy/paste) with a restricted one.
        XposedHelpers.findAndHookMethod(
            TextView::class.java,
            "setCustomSelectionActionModeCallback",
            ActionMode.Callback::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!prefs.copyBypass) return
                    val callback = param.args[0] ?: return

                    // Only replace callbacks from Zalo's own package.
                    // Framework callbacks (from EditText internally) are left alone.
                    val name = callback.javaClass.name
                    val isZaloCallback = name.startsWith("com.zing.zalo") ||
                                         name.startsWith("com.zing.v4")   ||
                                         name.length <= 10  // obfuscated

                    if (isZaloCallback) {
                        // Replace with a transparent callback that lets the OS
                        // show its normal Copy / Share / Select All menu.
                        param.args[0] = PassThroughCallback()
                        XposedBridge.log("$TAG: [CopyHook] replaced restrictive callback: $name")
                    }
                }
            }
        )

        // Also hook setCustomInsertionActionModeCallback for completeness
        try {
            XposedHelpers.findAndHookMethod(
                TextView::class.java,
                "setCustomInsertionActionModeCallback",
                ActionMode.Callback::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!prefs.copyBypass) return
                        val name = param.args[0]?.javaClass?.name ?: return
                        if (name.startsWith("com.zing") || name.length <= 10) {
                            param.args[0] = PassThroughCallback()
                        }
                    }
                }
            )
        } catch (_: Throwable) { /* not all Android versions have this method */ }
    }

    /** A no-restriction ActionMode.Callback — lets the system draw the default menu. */
    private class PassThroughCallback : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = true
        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
        override fun onDestroyActionMode(mode: ActionMode?) {}
    }
}
