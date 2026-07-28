package id.bangkumis.dontbroke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import id.bangkumis.dontbroke.presentation.navigation.AppNavGraph
import id.bangkumis.dontbroke.ui.theme.DontBrokeTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DontBrokeTheme { AppNavGraph() } }
    }
}
