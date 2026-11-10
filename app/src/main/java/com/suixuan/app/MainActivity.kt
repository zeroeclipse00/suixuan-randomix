package com.suixuan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.suixuan.app.ui.AppViewModel
import com.suixuan.app.ui.SuixuanApp
import com.suixuan.app.ui.theme.SuixuanTheme

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels {
        AppViewModel.factory((application as SuixuanApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SuixuanTheme { SuixuanApp(model) } }
    }
}
