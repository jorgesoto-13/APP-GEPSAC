package com.gepsac.app

import android.app.Application
import com.gepsac.app.di.AppContainer

class GepsacApp : Application() {
    val contenedor: AppContainer by lazy { AppContainer() }
}
