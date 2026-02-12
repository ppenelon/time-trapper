package com.ppenelon.timetrapper.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.ppenelon.timetrapper.R
import com.ppenelon.timetrapper.timer.AppTimerManager

/**
 * Écran plein écran affiché quand un timer arrive à expiration.
 */
class BlockedActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    private var blockedPackageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        AppTimerManager.initialize(applicationContext)
        blockedPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)?.trim()

        val packageText = findViewById<TextView>(R.id.textBlockedPackage)
        packageText.text = if (blockedPackageName.isNullOrEmpty()) {
            getString(R.string.blocked_unknown_package)
        } else {
            getString(R.string.blocked_package, blockedPackageName)
        }

        findViewById<Button>(R.id.buttonExtend5).setOnClickListener {
            blockedPackageName?.takeIf { packageName -> packageName.isNotBlank() }?.let { packageName ->
                AppTimerManager.extendSession(packageName, 5)
                Toast.makeText(this, R.string.toast_extension_applied, Toast.LENGTH_SHORT).show()
            }
            returnHome()
        }

        findViewById<Button>(R.id.buttonGoHome).setOnClickListener {
            returnHome()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                returnHome()
            }
        })
    }

    private fun returnHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        finish()
    }
}
