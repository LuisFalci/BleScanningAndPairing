package com.nsc9012.bluetooth.extension

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

// Esta função de extensão é usada para inflar (criar) uma view a partir de um recurso de layout.
// Ela é aplicável a um ViewGroup (como uma Activity ou um Fragment) e recebe o ID do recurso de layout como parâmetro.
// A função retorna a view inflada correspondente ao layoutRes fornecido.
fun ViewGroup.inflate(layoutRes: Int): View = LayoutInflater.from(context).inflate(
    layoutRes,
    this,
    false
)
