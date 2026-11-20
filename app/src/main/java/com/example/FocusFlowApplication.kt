package com.example

import android.app.Application
import com.example.di.AppContainer

class FocusFlowApplication : Application() {
  val appContainer: AppContainer by lazy { AppContainer(this) }
}
