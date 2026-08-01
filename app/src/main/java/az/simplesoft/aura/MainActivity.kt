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
import az.simplesoft.aura.ui.AuraApp
import az.simplesoft.aura.ui.theme.AuraTheme

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        permissions.launch(
            buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                    add(Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
        )
        setContent {
            AuraTheme {
                AuraApp(
                    initialCommand = if (BuildConfig.DEBUG) intent.getStringExtra("aura_command") else null,
                    pluginCoreEnabled = BuildConfig.DEBUG &&
                        intent.getBooleanExtra("aura_plugin_core", false),
                    youtubePluginEnabled = BuildConfig.DEBUG &&
                        intent.getBooleanExtra("aura_youtube_plugin", false)
                )
            }
        }
    }
}
