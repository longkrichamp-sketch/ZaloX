package dev.longdo.zalox.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import dev.longdo.zalox.R
import dev.longdo.zalox.ZaloXPrefs

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // MODE_PRIVATE — XSharedPreferences reads the file directly via filesystem
        // on rooted devices (bypasses Android permission model).
        // MODE_WORLD_READABLE is removed in API 24+ and throws SecurityException.
        prefs = getSharedPreferences(ZaloXPrefs.PREFS_NAME, MODE_PRIVATE)
        fixPermissions()

        bindSwitch(R.id.switchAntiSeen,    ZaloXPrefs.KEY_ANTI_SEEN)
        bindSwitch(R.id.switchAntiTyping,  ZaloXPrefs.KEY_ANTI_TYPING)
        bindSwitch(R.id.switchGhostMode,   ZaloXPrefs.KEY_GHOST_MODE)
        bindSwitch(R.id.switchAntiRecall,  ZaloXPrefs.KEY_ANTI_RECALL)
        bindSwitch(R.id.switchScreenshot,  ZaloXPrefs.KEY_SCREENSHOT)
        bindSwitch(R.id.switchCopy,        ZaloXPrefs.KEY_COPY)

        // Show module active status
        val statusText = findViewById<TextView>(R.id.tvModuleStatus)
        statusText.text = if (isModuleActive()) {
            "✅  Module đang hoạt động"
        } else {
            "⚠️  Module chưa được kích hoạt\nKiểm tra LSPosed Manager"
        }
    }

    private fun bindSwitch(switchId: Int, prefKey: String) {
        val switch = findViewById<SwitchMaterial>(switchId)
        val isSet = prefs.contains(prefKey)
        val initialVal = if (isSet) prefs.getBoolean(prefKey, true) else true
        if (!isSet) {
            prefs.edit().putBoolean(prefKey, true).commit()
        }
        switch.isChecked = initialVal
        switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(prefKey, isChecked).commit()
            fixPermissions()
        }
    }

    private fun fixPermissions() {
        try {
            val dataDir = applicationInfo.dataDir?.let { java.io.File(it) } ?: return
            dataDir.setExecutable(true, false)
            dataDir.setReadable(true, false)

            val prefsDir = java.io.File(dataDir, "shared_prefs")
            if (prefsDir.exists()) {
                prefsDir.setExecutable(true, false)
                prefsDir.setReadable(true, false)
            }

            val prefsFile = java.io.File(prefsDir, "${ZaloXPrefs.PREFS_NAME}.xml")
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false)
            }
        } catch (_: Throwable) {}
    }

    /**
     * This method is hooked by the module when active.
     * If it returns false, the module hasn't been injected yet.
     */
    private fun isModuleActive() = false
}
