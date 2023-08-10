package com.nsc9012.bluetooth.devices

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.nsc9012.bluetooth.R
import com.nsc9012.bluetooth.extension.inflate
import kotlinx.android.synthetic.main.adapter_discovered_devices.view.*

// O adaptador (adapter) é responsável por gerenciar a exibição dos dispositivos Bluetooth em um RecyclerView (Listagem na tela).

class DevicesAdapter : RecyclerView.Adapter<DevicesAdapter.ViewHolder>() {

    // ArrayList para armazenar os dispositivos Bluetooth exibidos no RecyclerView.
    private val devices = ArrayList<BluetoothDevice>()

    // Cria uma instância do ViewHolder quando necessário, inflando o layout do item do RecyclerView.
    override fun onCreateViewHolder(container: ViewGroup, viewType: Int) = ViewHolder(
        container.inflate(R.layout.adapter_discovered_devices)
    )

    // Retorna o número total de dispositivos na lista.
    override fun getItemCount() = devices.size

    // Vincula os dados de um dispositivo à visualização (View) do ViewHolder.
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.bind(devices[position])
    }

    // Função para adicionar um dispositivo à lista e notificar a alteração na exibição.
    fun addDevice(device: BluetoothDevice) {
        devices.add(device)
        notifyItemInserted(itemCount)
    }

    // Função para limpar a lista de dispositivos e notificar a alteração na exibição.
    fun clearDevices() {
        devices.clear()
        notifyDataSetChanged()
    }

    // Classe interna que representa o ViewHolder.
    inner class ViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        // Função para vincular um dispositivo à visualização (View) do ViewHolder.
        fun bind(device: BluetoothDevice) {
            // Define o nome do dispositivo como o texto do TextView, ou o endereço caso o nome seja nulo.
            view.text_view_device_name.text = device.name ?: device.address

            // Define um listener para o clique na visualização do dispositivo.
            view.ccMain.setOnClickListener {
                // Verifica se o dispositivo já está emparelhado (bonded).
                if (device.bondState == BOND_BONDED) {
                    // Exibe uma mensagem informando que o dispositivo já está emparelhado.
                    Toast.makeText(view.context, "Device already bonded", Toast.LENGTH_SHORT).show()
                } else {
                    // Se o dispositivo não estiver emparelhado, cria um vínculo de emparelhamento.
                    device.createBond()
                }
            }
        }
    }
}
