package com.suixuan.app

import android.app.Application
import android.content.Context
import com.suixuan.app.data.SuixuanDatabase
import com.suixuan.app.data.SuixuanRepository
import com.suixuan.app.domain.DrawEngine

class SuixuanApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(context: Context) {
    val repository = SuixuanRepository(SuixuanDatabase.open(context))
    val engine = DrawEngine()
    val preferences = context.getSharedPreferences("suixuan-settings", Context.MODE_PRIVATE)
}
