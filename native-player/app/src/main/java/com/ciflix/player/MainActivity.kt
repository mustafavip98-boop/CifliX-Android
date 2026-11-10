package com.ciflix.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ciflix.player.ui.CifliXApp
import com.ciflix.player.ui.theme.CifliXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CifliXTheme { CifliXApp() } }
    }
}
