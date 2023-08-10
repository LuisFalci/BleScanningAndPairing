package com.nsc9012.bluetooth.extension

import android.app.Activity
import android.content.pm.PackageManager
import android.support.v4.content.ContextCompat
import android.widget.Toast

// Esta função de extensão verifica se a atividade (Activity) possui uma determinada permissão.
// Ela recebe o nome da permissão como parâmetro e retorna true se a permissão estiver concedida e false caso contrário.
fun Activity.hasPermission(permission: String) = ContextCompat.checkSelfPermission(
    this,
    permission
) == PackageManager.PERMISSION_GRANTED

// Esta função de extensão exibe um toast (mensagem efêmera) na tela.
// Ela recebe o texto da mensagem como parâmetro e exibe um toast curto (SHORT) com esse texto.
fun Activity.toast(message: String){
    Toast.makeText(this, message , Toast.LENGTH_SHORT).show()
}
