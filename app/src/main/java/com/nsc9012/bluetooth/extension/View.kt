package com.nsc9012.bluetooth.extension

import android.view.View

// Esta função de extensão torna a View visível, alterando sua propriedade de visibilidade para VISIBLE.
fun View.visible() { this.visibility = View.VISIBLE }

// Esta função de extensão torna a View invisível, alterando sua propriedade de visibilidade para GONE.
fun View.invisible() { this.visibility = View.GONE }
