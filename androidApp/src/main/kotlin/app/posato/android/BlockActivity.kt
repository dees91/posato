package app.posato.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Covers an app chosen for the running pause; leaving it goes to the home screen, never back to that app. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) { goHome() }
        setContent {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(BACKGROUND)).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Paused by Posato", fontSize = 28.sp, color = Color(TEXT))
                Text("This app is part of your pause. It is available again when the pause ends.", color = Color(TEXT))
                Button(onClick = ::goHome, colors = ButtonDefaults.buttonColors(containerColor = Color(ACCENT))) { Text("Go to the home screen") }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private companion object {
        const val BACKGROUND = 0xFFFDFCF8
        const val TEXT = 0xFF1C2B26
        const val ACCENT = 0xFF2E5E50
    }
}
