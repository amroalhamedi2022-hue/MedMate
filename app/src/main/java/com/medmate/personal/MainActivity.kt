package com.medmate.personal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.medmate.personal.data.ChapterStore
import com.medmate.personal.ui.MedMateApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ChapterStore(applicationContext)
        setContent { MedMateApp(store) }
    }
}
