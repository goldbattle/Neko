package eu.kanade.tachiyomi.ui.setting

import androidx.biometric.BiometricManager
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.updater.UpdaterJob
import eu.kanade.tachiyomi.widget.preference.IntListMatPreference
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsGeneralController : SettingsController() {

    private val isUpdaterEnabled = BuildConfig.INCLUDE_UPDATER

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.pref_category_general

        intListPreference(activity) {
            key = Keys.theme
            titleRes = R.string.pref_theme
            entriesRes = arrayOf(R.string.light_theme, R.string.dark_theme,
                    R.string.amoled_theme)
            entryValues = listOf(1, 2, 3)
            defaultValue = 1

            onChange {
                activity?.recreate()
                true
            }
        }
        intListPreference(activity) {
            key = Keys.startScreen
            titleRes = R.string.pref_start_screen
            entriesRes = arrayOf(R.string.label_library, R.string.label_recent_manga,
                    R.string.label_recent_updates)
            entryValues = listOf(1, 2, 3)
            defaultValue = 1
        }
        switchPreference {
            key = Keys.automaticUpdates
            titleRes = R.string.pref_enable_automatic_updates
            summaryRes = R.string.pref_enable_automatic_updates_summary
            defaultValue = true

            if (isUpdaterEnabled) {
                onChange { newValue ->
                    val checked = newValue as Boolean
                    if (checked) {
                        UpdaterJob.setupTask()
                    } else {
                        UpdaterJob.cancelTask()
                    }
                    true
                }
            } else {
                isVisible = false
            }
        }

        val biometricManager = BiometricManager.from(context)
        if (biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
            var preference: IntListMatPreference? = null
            switchPreference {
                key = Keys.useBiometrics
                titleRes = R.string.lock_with_biometrics
                defaultValue = false

                onChange {
                    preference?.isVisible = it as Boolean
                    true
                }
            }
            preference = intListPreference(activity) {
                key = Keys.lockAfter
                titleRes = R.string.lock_when_idle
                isVisible = preferences.useBiometrics().getOrDefault()
                val values = listOf(0, 2, 5, 10, 20, 30, 60, 90, 120, -1)
                entries = values.mapNotNull {
                    when (it) {
                        0 -> context.getString(R.string.lock_always)
                        -1 -> context.getString(R.string.lock_never)
                        else -> resources?.getQuantityString(R.plurals.lock_after_mins, it.toInt(),
                                it)
                    }
                }
                entryValues = values
                defaultValue = 0
            }
        }
    }
}
