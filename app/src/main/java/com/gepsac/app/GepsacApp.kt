package com.gepsac.app

import android.app.Application
import com.gepsac.app.di.AppContainer
import com.gepsac.app.view.common.Tema

class GepsacApp : Application() {
    val contenedor: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Tema.aplicar(this)   // tema claro, oscuro o automático según lo que eligió el usuario
    }
}
