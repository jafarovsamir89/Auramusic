package az.simplesoft.aura

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import android.util.Log
import az.simplesoft.aura.assistant.AssistantLanguage
import az.simplesoft.aura.assistant.AuraSpeechSynthesizer
import az.simplesoft.aura.ui.AuraApp
import az.simplesoft.aura.ui.theme.AuraTheme

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        getPreferences(MODE_PRIVATE).edit { putBoolean("media_permissions_prompted", true) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("aura_verify_russian_voice", false)) {
            Log.i("AuraDeviceCheck", "Starting Russian Silero verification")
            AuraSpeechSynthesizer(this).speak("Привет! Я АУРА, рада тебе.", AssistantLanguage.RUSSIAN)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val permissionPrompted = getPreferences(MODE_PRIVATE)
            .getBoolean("media_permissions_prompted", false)
        if (!permissionPrompted) {
            val requested = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) add(Manifest.permission.POST_NOTIFICATIONS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) add(Manifest.permission.READ_MEDIA_AUDIO)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (requested.isNotEmpty()) permissions.launch(requested.toTypedArray())
            else getPreferences(MODE_PRIVATE).edit { putBoolean("media_permissions_prompted", true) }
        }
        setContent {
            AuraTheme {
                AuraApp(
                    initialCommand = if (BuildConfig.DEBUG) intent.getStringExtra("aura_command") else null,
                    forceLocalInitialCommand = BuildConfig.DEBUG && intent.getBooleanExtra("aura_force_local", false),
                    speakInitialCommand = BuildConfig.DEBUG && intent.getBooleanExtra("aura_speak", false),
                    initialVoicePreview = if (BuildConfig.DEBUG) intent.getStringExtra("aura_voice_preview") else null,
                    initialVoicePreviewLanguage = if (BuildConfig.DEBUG) intent.getStringExtra("aura_voice_preview_language") else null
                )
            }
        }
    }
}
