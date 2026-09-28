package dev.longdo.zalox.hooks

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import dev.longdo.zalox.ZaloXEntry.Companion.CLASS_PRESENCE_MGR
import dev.longdo.zalox.ZaloXEntry.Companion.CLASS_USER_MODEL
import dev.longdo.zalox.ZaloXEntry.Companion.TAG
import dev.longdo.zalox.ZaloXPrefs

/**
 * Ghost Mode: Hide own online / "vừa truy cập" status from others,
 * while preserving ability to see others' status.
 *
 * ─── Architecture (Zalo 26.09.01) ────────────────────────────────────────
 *  l60.h0  – Singleton presence manager (static field `a: l60.h0`).
 *             Responsible for updating/broadcasting own online presence.
 *             Void methods with boolean/int params likely set the online flag
 *             or trigger a presence ping to the server.
 *
 *  w00.u2  – Per-user model parsed from server JSON:
 *             field T:I  = isOnline / visibility (1 = online)
 *             field U:J  = lastActive timestamp
 *
 * Strategy:
 *  1. Hook l60.h0 void-methods that accept boolean/int args and block them
 *     when those args indicate "going online" → prevents outgoing pings.
 *  2. Do NOT modify incoming w00.u2 data (that would break seeing others).
 *
 * Limitation:
 *  Full ghost mode requires preventing ALL outgoing presence heartbeats.
 *  Zalo may use a persistent socket (not HTTP) for real-time presence, so
 *  some pings may still leak through. For a perfect implementation, revisit
 *  this hook once JADX finishes and the exact socket-send class is identified.
 *
 * ─── Update guide ─────────────────────────────────────────────────────────
 * Search smali for: const-string "isOnline"  and  const-string "lastActive"
 * ─────────────────────────────────────────────────────────────────────────
 */
class GhostHook(
    private val lpparam: LoadPackageParam,
    private val prefs: ZaloXPrefs
) {

    fun hook() {
        hookPresenceManager()
    }

    private fun hookPresenceManager() {
        val cls = XposedHelpers.findClass(CLASS_PRESENCE_MGR, lpparam.classLoader)

        // Target void methods with only primitive (boolean/int/long) parameters.
        // These are the candidates for "setOnline(true/false)" style methods.
        // Widen search to ALL void methods — filter by args at call time.
        // The 0-methods result means l60.h0's setOnline() may take other param types
        // (e.g. String, Context) in addition to booleans.
        val candidates = cls.declaredMethods.filter { m ->
            m.returnType == Void.TYPE && m.parameterTypes.isNotEmpty()
        }

        candidates.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!prefs.ghostMode) return

                    // Block calls that signal "online" (boolean=true or int=1)
                    val goingOnline = param.args.any { arg ->
                        (arg is Boolean && arg) || (arg is Int && arg == 1)
                    }
                    if (goingOnline) {
                        param.result = null
                        XposedBridge.log(
                            "$TAG: [GhostHook] blocked presence update in " +
                            "${cls.simpleName}.${method.name}"
                        )
                    }
                }
            })
        }

        XposedBridge.log("$TAG: [GhostHook] hooked ${candidates.size} candidate methods in $CLASS_PRESENCE_MGR")
    }
}
