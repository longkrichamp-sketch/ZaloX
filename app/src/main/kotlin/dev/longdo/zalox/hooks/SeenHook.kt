package dev.longdo.zalox.hooks

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import dev.longdo.zalox.ZaloXEntry.Companion.CLASS_EVENT
import dev.longdo.zalox.ZaloXEntry.Companion.CLASS_EVENT_BUS
import dev.longdo.zalox.ZaloXEntry.Companion.TAG
import dev.longdo.zalox.ZaloXPrefs

/**
 * Anti-Seen: Blocks read receipts ("đã xem") from being sent.
 *
 * ─── How Zalo sends seen receipts (26.09.01) ─────────────────────────────
 * 1. User opens a chat → as0.ob (ViewModel) creates an event:
 *      new as0.e("mark_read", arrayListOf(msgId1, msgId2, ...))
 * 2. The event is dispatched via:
 *      as0.g.j(event)   ← THIS is where we intercept
 * 3. The dispatcher serialises the event and sends it to the server.
 *
 * Hook point: as0.g  →  method j(as0.e)
 * We read the event's first String field (the event name). If it equals
 * "mark_read" and anti-seen is enabled, we return early and skip dispatch.
 *
 * Also hooks: xr0.ab (second location that creates mark_read events).
 *
 * ─── Update guide after Zalo update ──────────────────────────────────────
 * Search all smali files for: const-string "mark_read"
 * Update CLASS_EVENT and CLASS_EVENT_BUS in ZaloXEntry accordingly.
 * ─────────────────────────────────────────────────────────────────────────
 */
class SeenHook(
    private val lpparam: LoadPackageParam,
    private val prefs: ZaloXPrefs
) {

    fun hook() {
        val eventClass = XposedHelpers.findClass(CLASS_EVENT, lpparam.classLoader)
        val busClass   = XposedHelpers.findClass(CLASS_EVENT_BUS, lpparam.classLoader)

        // Find the dispatch method: the one that takes exactly one as0.e argument
        val dispatchMethod = busClass.declaredMethods.firstOrNull { m ->
            m.parameterTypes.size == 1 && m.parameterTypes[0] == eventClass
        } ?: run {
            XposedBridge.log("$TAG: [SeenHook] dispatch method not found in $CLASS_EVENT_BUS")
            return
        }

        XposedBridge.hookMethod(dispatchMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!prefs.antiSeen) return
                val event = param.args[0] ?: return
                val name  = getFirstStringField(event) ?: return
                if (name == "mark_read") {
                    param.result = null          // Skip the entire dispatch call
                    XposedBridge.log("$TAG: [SeenHook] mark_read blocked")
                }
            }
        })
    }

    /**
     * Reflectively reads the first String field of the event object.
     * In as0.e the constructor signature is (String name, Object data),
     * so the first String field is always the event name.
     */
    private fun getFirstStringField(obj: Any): String? = try {
        obj.javaClass.declaredFields
            .firstOrNull { it.type == String::class.java }
            ?.also { it.isAccessible = true }
            ?.get(obj) as? String
    } catch (_: Throwable) { null }
}
