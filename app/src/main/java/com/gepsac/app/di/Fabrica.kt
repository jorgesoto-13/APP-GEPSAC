package com.gepsac.app.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gepsac.app.GepsacApp
import com.gepsac.app.viewmodel.AdminClientesViewModel
import com.gepsac.app.viewmodel.AdminProductosViewModel
import com.gepsac.app.viewmodel.NuevoPedidoViewModel
import com.gepsac.app.viewmodel.AuthViewModel
import com.gepsac.app.viewmodel.CarritoViewModel
import com.gepsac.app.viewmodel.CatalogoViewModel
import com.gepsac.app.viewmodel.PedidosViewModel
import com.gepsac.app.viewmodel.SeguimientoViewModel
import com.gepsac.app.viewmodel.VentasViewModel

/**
 * Cómo se construye cada ViewModel con sus repositorios. Uso en una pantalla:
 *     private val vm: PedidosViewModel by activityViewModels { Fabrica.pedidos }
 */
object Fabrica {
    val auth: ViewModelProvider.Factory = viewModelFactory {
        initializer { AuthViewModel(contenedor().authRepository) }
    }
    val catalogo: ViewModelProvider.Factory = viewModelFactory {
        initializer { CatalogoViewModel(contenedor().productoRepository) }
    }
    val carrito: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val c = contenedor()
            CarritoViewModel(c.pedidoRepository, c.authRepository, c.usuarioRepository, c.carritoLocal)
        }
    }
    val pedidos: ViewModelProvider.Factory = viewModelFactory {
        initializer { PedidosViewModel(contenedor().pedidoRepository, contenedor().authRepository) }
    }
    val seguimiento: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val c = contenedor()
            SeguimientoViewModel(c.pedidoRepository, c.authRepository, c.productoRepository, c.carritoLocal)
        }
    }
    val ventas: ViewModelProvider.Factory = viewModelFactory {
        initializer { VentasViewModel(contenedor().pedidoRepository) }
    }
    val adminProductos: ViewModelProvider.Factory = viewModelFactory {
        initializer { AdminProductosViewModel(contenedor().productoRepository) }
    }
    val adminClientes: ViewModelProvider.Factory = viewModelFactory {
        initializer { AdminClientesViewModel(contenedor().usuarioRepository, contenedor().pedidoRepository) }
    }
    val nuevoPedido: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val c = contenedor()
            NuevoPedidoViewModel(c.usuarioRepository, c.productoRepository, c.pedidoRepository, c.authRepository)
        }
    }
}

private fun CreationExtras.contenedor(): AppContainer = (this[APPLICATION_KEY] as GepsacApp).contenedor
