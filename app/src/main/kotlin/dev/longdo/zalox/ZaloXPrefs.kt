package dev.longdo.zalox

import de.robv.android.xposed.XSharedPreferences

/**
 * Shared preferences wrapper for ZaloX.
 *
 * MainActivity writes prefs with MODE_PRIVATE to the module's own data dir.
 * The hook (running in Zalo's process) reads them via XSharedPreferences which
 * accesses the file directly on disk with LSPosed's elevated privileges.
 */
class ZaloXPrefs {

    // XSharedPreferences reads the module's own prefs file directly from disk.
    // Must use MODULE package name (dev.longdo.zalox), NOT the target app's package.
    private val xprefs = runCatching {
        val p = XSharedPreferences("dev.longdo.zalox", PREFS_NAME)
        p.makeWorldReadable()
        p
    }.getOrNull()

    private fun reload() = xprefs?.reload()

    // ── Feature toggles ────────────────────────────────────────────────────
    /** Block sending read receipts ("seen") to the server. */
    val antiSeen: Boolean
        get() { reload(); return xprefs?.getBoolean(KEY_ANTI_SEEN, true) ?: true }

    /** Block transmitting the "typing" indicator to others. */
    val antiTyping: Boolean
        get() { reload(); return xprefs?.getBoolean(KEY_ANTI_TYPING, true) ?: true }

    /** Appear offline to others while still seeing their online status. */
    val ghostMode: Boolean
        get() { reload(); return xprefs?.getBoolean(KEY_GHOST_MODE, true) ?: true }

    /** Prevent recalled/revoked messages from disappearing. */
    val antiRecall: Boolean
        get() { reload(); return xprefs?.getBoolean(KEY_ANTI_RECALL, true) ?: true }

    /** Allow screenshots in Zalo (removes FLAG_SECURE). */
    val screenshotBypass: Boolean
        get() { reload(); return xprefs?.getBoolean(KEY_SCREENSHOT, true) ?: true }

    /** Allow copying text from copy-protected messages. */
    val copyBypass: Boolean
        get() { reload(); return xprefs?.getBoolean(KEY_COPY, true) ?: true }

    companion object {
        const val PREFS_NAME       = "zalox_prefs"
        const val KEY_ANTI_SEEN    = "anti_seen"
        const val KEY_ANTI_TYPING  = "anti_typing"
        const val KEY_GHOST_MODE   = "ghost_mode"
        const val KEY_ANTI_RECALL  = "anti_recall"
        const val KEY_SCREENSHOT   = "screenshot_bypass"
        const val KEY_COPY         = "copy_bypass"
    }
}
