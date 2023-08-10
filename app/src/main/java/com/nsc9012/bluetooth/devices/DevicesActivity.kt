package com.nsc9012.bluetooth.devices

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import com.nsc9012.bluetooth.R
import com.nsc9012.bluetooth.extension.*
import kotlinx.android.synthetic.main.activity_devices.*

// Classe que representa a atividade responsável por exibir dispositivos Bluetooth e iniciar a descoberta.
class DevicesActivity : AppCompatActivity() {

    // Os membros dentro de um companion object podem ser acessados diretamente usando o nome da classe,
    // sem a necessidade de criar uma instância da classe.
    companion object {
        const val ENABLE_BLUETOOTH = 1
        const val REQUEST_ENABLE_DISCOVERY = 2
        const val REQUEST_ACCESS_COARSE_LOCATION = 3
    }

    // BroadcastReceiver para escutar os resultados da descoberta Bluetooth.
    // bluetoothDiscoveryResult é uma instância da classe BroadcastReceiver
    // Ao instanciarmos o BroadcastReceiver, utilizamos "object :", que é usada para criar objetos únicos (ou instâncias únicas) de uma classe
    private val bluetoothDiscoveryResult = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_FOUND) {
                // O método getParcelableExtra é usado para extrair um objeto BluetoothDevice da intenção. O operador !! é usado para forçar o desempacotamento,
                // assumindo que o objeto não é nulo (o que pode causar uma exceção se o objeto for nulo).
                val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!
                deviceListAdapter.addDevice(device)
            }
        }
    }

    // BroadcastReceiver para escutar as atualizações da descoberta Bluetooth.
    private val bluetoothDiscoveryMonitor = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    progress_bar.visible()
                    toast("Scan started...")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    progress_bar.invisible()
                    toast("Scan complete. Found ${deviceListAdapter.itemCount} devices.")
                }
            }
        }
    }

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val deviceListAdapter = DevicesAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_devices)
        initUI()
    }

    // Inicializa a interface do usuário.
    private fun initUI() {
        title = "Bluetooth Scanner"
        recycler_view_devices.adapter = deviceListAdapter
        recycler_view_devices.layoutManager = LinearLayoutManager(this)
        recycler_view_devices.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        button_discover.setOnClickListener { initBluetooth() }
        ConnectionManager.registerListener(connectionEventListener)
    }

    // Inicializa o Bluetooth.
    private fun initBluetooth() {
    // Se já está buscando, retornamos nada
        if (bluetoothAdapter.isDiscovering) return
    // Se o bluetooth estiver ligado, começa a busca por dispositivos
        if (bluetoothAdapter.isEnabled) {
            enableDiscovery()
        } else {
            // O Bluetooth não está habilitado - solicita ao usuário para ativá-lo.
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, ENABLE_BLUETOOTH)
        }
    }

    private fun enableDiscovery() {
        // Cria um novo objeto Intent, que representa uma intenção ou ação a ser executada.
        // No contexto do Bluetooth, uma Intent é usada para iniciar uma ação específica,
        // como solicitar que o dispositivo Bluetooth fique visível para outros dispositivos.
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)

        // Inicia uma atividade para solicitar a descoberta Bluetooth visível,
        // passando o Intent criado e um código de solicitação (REQUEST_ENABLE_DISCOVERY) como parâmetros.
        startActivityForResult(intent, REQUEST_ENABLE_DISCOVERY)
    }


    // Monitora a descoberta Bluetooth.
    private fun monitorDiscovery() {
        registerReceiver(bluetoothDiscoveryMonitor, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED))
        registerReceiver(bluetoothDiscoveryMonitor, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
    }

    // Inicia a descoberta de dispositivos Bluetooth.
    private fun startDiscovery() {
        if (hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            if (bluetoothAdapter.isEnabled && !bluetoothAdapter.isDiscovering) {
                beginDiscovery()
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_ACCESS_COARSE_LOCATION
            )
        }
    }

    // Inicia o processo de descoberta de dispositivos Bluetooth.
    private fun beginDiscovery() {
        registerReceiver(bluetoothDiscoveryResult, IntentFilter(BluetoothDevice.ACTION_FOUND))
        deviceListAdapter.clearDevices()
        monitorDiscovery()
        bluetoothAdapter.startDiscovery()
    }

    // Lida com a resposta do usuário sobre permissões.
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_ACCESS_COARSE_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    beginDiscovery()
                } else {
                    toast("Permission required to scan for devices.")
                }
            }
        }
    }

    // Lida com os resultados das atividades iniciadas para resultados (startActivityForResult).
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            ENABLE_BLUETOOTH -> if (resultCode == Activity.RESULT_OK) {
                enableDiscovery()
            }
            REQUEST_ENABLE_DISCOVERY -> if (resultCode == Activity.RESULT_CANCELED) {
                toast("Discovery cancelled.")
            } else {
                startDiscovery()
            }
        }
    }

    // Libera recursos quando a atividade é destruída.
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothDiscoveryMonitor)
        unregisterReceiver(bluetoothDiscoveryResult)
    }

    // Listener para eventos de conexão Bluetooth.
    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onConnectionSetupComplete = { gatt ->
                toast(gatt.device.name)
            }
            onDisconnect = {
                runOnUiThread {
                    toast("Disconnect")
                }
            }
        }
    }
}
