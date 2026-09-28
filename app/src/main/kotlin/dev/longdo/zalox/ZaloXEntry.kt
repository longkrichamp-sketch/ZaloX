package dev.longdo.zalox

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import dev.longdo.zalox.hooks.*

/**
 * ZaloX – LSPosed/Xposed module entry point.
 *
 * Registered in: assets/xposed_init
 * Target app:    com.zing.zalo (Zalo 26.09.01)
 *
 * Obfuscated class names below are specific to Zalo 26.09.01.
 * After a Zalo update, re-decompile with JADX and search for the
 * string literals documented in each hook class to find the new names.
 */
class ZaloXEntry : IXposedHookLoadPackage {

    companion object {
        const val TAG = "ZaloX"
        const val ZALO_PKG = "com.zing.zalo"

        // ─── Zalo 26.09.01 obfuscated class map ───────────────────────────
        // Event system (mark_read seen receipt)
        //   Search smali for: const-string "mark_read"
        const val CLASS_EVENT       = "as0.e"        // Event data object
        const val CLASS_EVENT_BUS   = "as0.g"        // Event dispatcher (method j)

        // User / contact models
        //   Search smali for: const-string "isOnline" / "lastActive"
        const val CLASS_USER_MODEL      = "w00.u2"   // field T=isOnline, U=lastActive
        const val CLASS_PRESENCE_MGR    = "l60.h0"   // Singleton presence manager

        // Message models carrying "seen" flag
        //   Search smali for: const-string "seen"
        const val CLASS_MSG_MODEL_A = "w00.e8"   // field s:Z = seen
        const val CLASS_MSG_MODEL_B = "i11.b"    // field d:Z = seen

        // Message recall pipeline (Zalo calls recall "undo")
        //   Search smali for: "chat.undo", "msgActionUndo", "UndoMessage"
        const val CLASS_UNDO_PROCESSOR       = "k20.g"  // e(f20.s): parses chat.undo
        const val CLASS_SINGLE_UNDO_HANDLER = "dq1.w0" // G(Object): single message
        const val CLASS_BATCH_UNDO_HANDLER  = "dq1.h"  // I(Object): group/batch events
        const val CLASS_UNDO_DB             = "l60.d"  // g0(...): persists type 0x24
        const val CLASS_MESSAGE_MODEL       = "f20.s"
        const val CLASS_UNDO_INFO           = "f20.w0"

        // Stable non-obfuscated class (Parcelable contact profile)
        const val CLASS_CONTACT_PROFILE = "com.zing.zalo.control.ContactProfile"
        // ──────────────────────────────────────────────────────────────────
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName == "dev.longdo.zalox") {
            try {
                XposedHelpers.findAndHookMethod(
                    "dev.longdo.zalox.ui.MainActivity",
                    lpparam.classLoader,
                    "isModuleActive",
                    object : de.robv.android.xposed.XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Any {
                            return true
                        }
                    }
                )
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: Failed to hook MainActivity: $t")
            }
            return
        }

        if (lpparam.packageName != ZALO_PKG) return

        XposedBridge.log("$TAG: Injected into ${lpparam.packageName}")

        val prefs = ZaloXPrefs()

        safeHook("SeenHook")       { SeenHook(lpparam, prefs).hook() }
        safeHook("RecallHook")     { RecallHook(lpparam, prefs).hook() }
        safeHook("GhostHook")      { GhostHook(lpparam, prefs).hook() }
        safeHook("TypingHook")     { TypingHook(lpparam, prefs).hook() }
        safeHook("ScreenshotHook") { ScreenshotHook(lpparam, prefs).hook() }
        safeHook("CopyHook")       { CopyHook(lpparam, prefs).hook() }
    }

    /** Wraps each hook so one failure doesn't kill the others. */
    private fun safeHook(name: String, block: () -> Unit) {
        try {
            block()
            XposedBridge.log("$TAG: [$name] applied")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: [$name] FAILED – ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
