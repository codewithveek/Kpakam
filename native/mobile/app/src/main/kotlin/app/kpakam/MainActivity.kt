package app.kpakam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource
import app.kpakam.navigation.CenteredMessage
import app.kpakam.navigation.KpakamRoot
import app.kpakam.platform.firebase.FirebaseSetup
import app.kpakam.shared.ui.KpakamTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val configured = FirebaseSetup.isConfigured(this)
        setContent {
            KpakamTheme {
                if (configured) {
                    KpakamRoot()
                } else {
                    CenteredMessage(stringResource(R.string.setup_title) + "\n\n" + stringResource(R.string.setup_body))
                }
            }
        }
    }
}
