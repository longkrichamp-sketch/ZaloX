package dev.longdo.zalox.hooks

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import dev.longdo.zalox.ZaloXEntry.Companion.CLASS_BATCH_UNDO_HANDLER
import dev.longdo.zalox.ZaloXEntry.Companion.CLASS_MESSAGE_MODEL
import dev.longdo.zalox.ZaloXEntry.Companion.CLASS_SINGLE_UNDO_HANDLER
import dev.longdo.zalox.ZaloXEntry.Companion.CLASS_UNDO_DB
import dev.longdo.zalox.ZaloXEntry.Companion.CLASS_UNDO_INFO
import dev.longdo.zalox.ZaloXEntry.Companion.CLASS_UNDO_PROCESSOR
import dev.longdo.zalox.ZaloXEntry.Companion.TAG
import dev.longdo.zalox.ZaloXPrefs

/**
 * Keeps the original message when Zalo receives a `chat.undo` action.
 *
 * Zalo 26.09.01 processes a recall through this chain:
 *
 *  k20.g.e(f20.s)       parses the incoming chat.undo action
 *    -> dq1.w0.G(Object) applies a single-message recall
 *    -> dq1.h.I(Object) applies batch/group events (event type UNDO)
 *    -> l60.d.g0(...)    persists message type 0x24 (recalled)
 *
 * The old implementation searched for the English words "revoke" and
 * "recall" in unrelated obfuscated classes. Zalo's actual protocol names the
 * operation "undo", and the important value (message type 0x24) is numeric.
 */
class RecallHook(
    private val lpparam: LoadPackageParam,
    private val prefs: ZaloXPrefs
) {

    fun hook() {
        // This is the earliest and safest interception point. Blocking here
        // leaves the in-memory model, attachment metadata and database intact.
        hookVoidMethod(
            className = CLASS_UNDO_PROCESSOR,
            methodName = "e",
            parameterTypes = arrayOf(CLASS_MESSAGE_MODEL),
            logLabel = "incoming chat.undo"
        )

        // Defensive hooks cover recalls dispatched from a queued/synchronised
        // path that does not pass through the special-message processor.
        hookVoidMethod(
            className = CLASS_SINGLE_UNDO_HANDLER,
            methodName = "G",
            parameterTypes = arrayOf(Any::class.java),
            logLabel = "single-message undo"
        )
        hookBatchUndoHandler()

        // Last line of defence: never persist the recalled message type. This
        // also keeps the original visible after Zalo is restarted.
        hookVoidMethod(
            className = CLASS_UNDO_DB,
            methodName = "g0",
            parameterTypes = arrayOf(CLASS_MESSAGE_MODEL, Boolean::class.javaPrimitiveType!!, CLASS_UNDO_INFO),
            logLabel = "undo database update"
        )
    }

    private fun hookBatchUndoHandler() {
        installHook(CLASS_BATCH_UNDO_HANDLER, "I", arrayOf(Any::class.java), object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!prefs.antiRecall) return

                // dq1.f.c is dq1.e. Only UNDO represents a recalled message;
                // DFE and the other event types must keep their normal flow.
                val eventType = runCatching {
                    XposedHelpers.getObjectField(param.args[0], "c") as? Enum<*>
                }.getOrNull()

                if (eventType?.name == "UNDO") {
                    param.result = null
                    XposedBridge.log("$TAG: [RecallHook] blocked batch/group undo")
                }
            }
        })
    }

    private fun hookVoidMethod(
        className: String,
        methodName: String,
        parameterTypes: Array<out Any>,
        logLabel: String
    ) {
        installHook(className, methodName, parameterTypes, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!prefs.antiRecall) return
                param.result = null
                XposedBridge.log("$TAG: [RecallHook] blocked $logLabel")
            }
        })
    }

    private fun installHook(
        className: String,
        methodName: String,
        parameterTypes: Array<out Any>,
        callback: XC_MethodHook
    ) {
        try {
            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                methodName,
                *parameterTypes,
                callback
            )
            XposedBridge.log("$TAG: [RecallHook] hooked $className.$methodName")
        } catch (t: Throwable) {
            // One changed obfuscated fallback must not disable the primary hook.
            XposedBridge.log(
                "$TAG: [RecallHook] could not hook $className.$methodName: " +
                    "${t.javaClass.simpleName}: ${t.message}"
            )
        }
    }
}
