package dev.longdo.zalox.hooks

import android.view.Window
import android.view.WindowManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import dev.longdo.zalox.ZaloXEntry.Companion.TAG
import dev.longdo.zalox.ZaloXPrefs

/**
 * Screenshot Bypass: Allows screenshots and screen recording in Zalo.
 *
 * Zalo sets FLAG_SECURE on most Activity windows, which prevents:
 *   - Screenshots via Power+Volume
 *   - Screen recording
 *   - Recent apps preview (shows blank)
 *
 * This hook intercepts Window.setFlags() and strips FLAG_SECURE (0x2000)
 * before it's applied. It uses Android framework classes so it's stable
 * across Zalo version updates.
 */
class ScreenshotHook(
    private val lpparam: LoadPackageParam,
    private val prefs: ZaloXPrefs
) {

    companion object {
        private const val FLAG_SECURE = WindowManager.LayoutParams.FLAG_SECURE
    }

    fun hook() {
        // Hook Window.setFlags(int flags, int mask)
        XposedHelpers.findAndHookMethod(
            Window::class.java, "setFlags",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!prefs.screenshotBypass) return
                    val flags = param.args[0] as Int
                    if (flags and FLAG_SECURE != 0) {
                        param.args[0] = flags and FLAG_SECURE.inv()
                        XposedBridge.log("$TAG: [ScreenshotHook] FLAG_SECURE stripped")
                    }
                }
            }
        )

        // Also hook addFlags for completeness
        XposedHelpers.findAndHookMethod(
            Window::class.java, "addFlags",
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!prefs.screenshotBypass) return
                    val flags = param.args[0] as Int
                    if (flags and FLAG_SECURE != 0) {
                        val newFlags = flags and FLAG_SECURE.inv()
                        if (newFlags == 0) {
                            param.result = null   // nothing left to add
                        } else {
                            param.args[0] = newFlags
                        }
                        XposedBridge.log("$TAG: [ScreenshotHook] FLAG_SECURE stripped from addFlags")
                    }
                }
            }
        )
    }
}
