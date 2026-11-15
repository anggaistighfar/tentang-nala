package com.lembar.journal

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lembar.journal.security.Authenticator
import com.lembar.journal.ui.AppNav
import com.lembar.journal.ui.AppViewModel
import com.lembar.journal.ui.LocalAuthenticator
import com.lembar.journal.ui.LockScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lembar.journal.ui.theme.LembarTheme
import com.lembar.journal.ui.theme.Lembar
import com.lembar.journal.ui.theme.isNight
import kotlinx.coroutines.flow.MutableStateFlow

/** FragmentActivity (not plain ComponentActivity) because BiometricPrompt needs it. */
class MainActivity : FragmentActivity() {

    private val openCheckin = MutableStateFlow(false)
    private val container get() = (application as LembarApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val authenticator = Authenticator(this, container.session)
        val appVm = ViewModelProvider(
            this,
            viewModelFactory { initializer { AppViewModel(container) } },
        )[AppViewModel::class.java]
        // Keep the splash until settings are read, so there's no blank frame or onboarding flash.
        splash.setKeepOnScreenCondition { appVm.settings.value == null }

        setContent {
            val settings by appVm.settings.collectAsStateWithLifecycle()
            val unlocked by container.session.unlocked.collectAsStateWithLifecycle()

            LifecycleEventEffect(Lifecycle.Event.ON_START) {
                container.session.onForeground()
                appVm.refreshToday()
            }
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { container.session.onBackground() }

            val s = settings
            if (s == null) {
                Box(Modifier.fillMaxSize())
                return@setContent
            }
            val night = isNight(s.themeMode)
            DisposableEffect(night) {
                val style = if (night) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            // With app lock on, hide content from the Recents preview and screenshots.
            DisposableEffect(s.appLock) {
                if (s.appLock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                onDispose {}
            }

            LembarTheme(s.themeMode) {
                CompositionLocalProvider(LocalAuthenticator provides authenticator) {
                    Box(Modifier.fillMaxSize().background(Lembar.colors.background)) {
                        AppNav(appVm, s, openCheckin)
                        if (s.appLock && s.onboarded && !unlocked) {
                            LockScreen(onUnlock = {
                                authenticator.authenticate("Buka Tentang Nala", "Jurnalmu terkunci", onSuccess = {})
                            })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_CHECKIN, false) == true) {
            openCheckin.value = true
            intent.removeExtra(EXTRA_OPEN_CHECKIN)
        }
    }

    companion object {
        const val EXTRA_OPEN_CHECKIN = "open_checkin"
    }
}
