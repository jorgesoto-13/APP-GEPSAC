package com.gepsac.app.data.repository

/** Error con un mensaje listo para mostrar al usuario. Los repositorios convierten cualquier fallo a este tipo. */
class ErrorApp(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa)
