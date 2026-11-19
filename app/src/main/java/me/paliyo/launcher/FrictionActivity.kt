package me.paliyo.launcher

import android.content.ComponentName
import android.content.pm.LauncherApps
import android.graphics.Color
import android.os.Bundle
import android.os.UserHandle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as C
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ponytail: countdown only; usage minutes, intent chips and the attempt log arrive with phase 4.
class FrictionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        val component = intent.getParcelableExtra("component", ComponentName::class.java)
        val user = intent.getParcelableExtra("user", UserHandle::class.java)
        if (component == null || user == null) return finish()
        val label = intent.getStringExtra("label") ?: component.packageName

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = C.Black, primary = C(0xFFFF6B4A), onPrimary = C.Black)) {
                var left by remember { mutableIntStateOf(10) }
                LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- } }
                Column(
                    Modifier.fillMaxSize().background(C.Black).systemBarsPadding().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Pause.", fontSize = 30.sp, fontWeight = FontWeight.Medium, color = C.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Do you really need $label right now?", fontSize = 18.sp, color = C(0xFF8A8A8A))
                    Spacer(Modifier.height(48.dp))
                    Button({ finish() }, Modifier.fillMaxWidth()) { Text("Back to focus") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        {
                            getSystemService(LauncherApps::class.java).startMainActivity(component, user, null, null)
                            finish()
                        },
                        Modifier.fillMaxWidth(),
                        enabled = left == 0,
                    ) { Text(if (left > 0) "Open $label in ${left}s" else "Open $label") }
                }
            }
        }
    }
}
