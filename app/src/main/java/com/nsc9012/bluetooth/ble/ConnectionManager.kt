/*
 * Direitos autorais 2019 Punch Through Design LLC
 *
 * Licenciado sob a Licença Apache, Versão 2.0 (a "Licença");
 * você não pode usar este arquivo exceto em conformidade com a Licença.
 * Você pode obter uma cópia da Licença em
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * A menos que exigido pela lei aplicável ou acordado por escrito, o software
 * distribuído sob a Licença é distribuído "COMO ESTÁ",
 * SEM GARANTIAS OU CONDIÇÕES DE QUALQUER TIPO, expressas ou implícitas.
 * Consulte a Licença para obter os detalhes específicos sobre as permissões e
 * limitações sob a Licença.
 */

package com.nsc9012.bluetooth.devices

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.os.Handler
import android.os.Looper
import com.neuphony.music.ui.blutooth.ble.*
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

// Tamanho mínimo do MTU do GATT definido como 23.
private const val GATT_MIN_MTU_SIZE = 23

// Tamanho máximo do MTU do BLE conforme definido em gatt_api.h.
private const val GATT_MAX_MTU_SIZE = 517

// Classe responsável por gerenciar a conexão BLE e operações relacionadas.
object ConnectionManager {

    // Conjunto de ouvintes de eventos de conexão, usando referências fracas.
    private var listeners: MutableSet<WeakReference<ConnectionEventListener>> = mutableSetOf()

    // Mapa para armazenar dispositivos Bluetooth e seus objetos BluetoothGatt.
    private val deviceGattMap = ConcurrentHashMap<BluetoothDevice, BluetoothGatt>()

    // Fila de operações BLE a serem executadas.
    private val operationQueue = ConcurrentLinkedQueue<BleOperationType>()

    // Operação pendente atualmente em execução.
    private var pendingOperation: BleOperationType? = null

    // Registra um ouvinte para eventos de conexão.
    fun registerListener(listener: ConnectionEventListener) {
        if (listeners.map { it.get() }.contains(listener)) {
            return
        }
        listeners.add(WeakReference(listener))
        listeners = listeners.filter { it.get() != null }.toMutableSet()
    }

    // Encerra a conexão com um dispositivo Bluetooth.
    fun teardownConnection(device: BluetoothDevice) {
        if (device.isConnected()) {
            enqueueOperation(Disconnect(device))
        } else {
            // Não está conectado ao dispositivo, não é possível encerrar a conexão.
        }
    }

    // Solicitação de alteração do MTU (Unidade Máxima de Transferência) para um dispositivo Bluetooth.
    fun requestMtu(device: BluetoothDevice, mtu: Int) {
        if (device.isConnected()) {
            enqueueOperation(MtuRequest(device, mtu.coerceIn(GATT_MIN_MTU_SIZE, GATT_MAX_MTU_SIZE)))
        } else {
            // Não está conectado ao dispositivo, não é possível solicitar a atualização do MTU.
        }
    }

    // - Início das funções PRIVADAS

    // Adiciona uma operação à fila de operações a serem executadas.
    @Synchronized
    private fun enqueueOperation(operation: BleOperationType) {
        operationQueue.add(operation)
        if (pendingOperation == null) {
            doNextOperation()
        }
    }

    // Sinaliza o término da operação atual.
    @Synchronized
    private fun signalEndOfOperation() {
        pendingOperation = null
        if (operationQueue.isNotEmpty()) {
            doNextOperation()
        }
    }

    /**
     * Executa uma determinada [BleOperationType].
     * Todas as verificações de permissão são realizadas antes que uma operação possa ser enfileirada por [enqueueOperation].
     */
    @Synchronized
    private fun doNextOperation() {
        if (pendingOperation != null) {
            return
        }

        val operation = operationQueue.poll() ?: run {
            return
        }
        pendingOperation = operation

        // Lida com a conexão separadamente de outras operações que requerem que o dispositivo esteja conectado.
        if (operation is Connect) {
            with(operation) {
                device.connectGatt(context, false, callback)
            }
            return
        }

        // Verifica a disponibilidade do BluetoothGatt para outras operações.
        val gatt = deviceGattMap[operation.device]
            ?: this@ConnectionManager.run {
                signalEndOfOperation()
                return
            }

        // TODO: Certifique-se de que cada operação conduza finalmente a signalEndOfOperation()
        // TODO: Refatorar isso em uma função abstrata ou de extensão BleOperationType
        when (operation) {
            is Disconnect -> with(operation) {
                gatt.close()
                deviceGattMap.remove(device)
                listeners.forEach { it.get()?.onDisconnect?.invoke(device) }
                signalEndOfOperation()
            }
            is CharacteristicWrite -> with(operation) {
                gatt.findCharacteristic(characteristicUuid)?.let { characteristic ->
                    characteristic.writeType = writeType
                    characteristic.value = payload
                    gatt.writeCharacteristic(characteristic)
                } ?: this@ConnectionManager.run {
                    signalEndOfOperation()
                }
            }
            is CharacteristicRead -> with(operation) {
                gatt.findCharacteristic(characteristicUuid)?.let { characteristic ->
                    gatt.readCharacteristic(characteristic)
                } ?: this@ConnectionManager.run {
                    signalEndOfOperation()
                }
            }
            is DescriptorWrite -> with(operation) {
                gatt.findDescriptor(descriptorUuid)?.let { descriptor ->
                    descriptor.value = payload
                    gatt.writeDescriptor(descriptor)
                } ?: this@ConnectionManager.run {
                    signalEndOfOperation()
                }
            }
            is DescriptorRead -> with(operation) {
                gatt.findDescriptor(descriptorUuid)?.let { descriptor ->
                    gatt.readDescriptor(descriptor)
                } ?: this@ConnectionManager.run {
                    signalEndOfOperation()
                }
            }
            is EnableNotifications -> with(operation) {
                gatt.findCharacteristic(characteristicUuid)?.let { characteristic ->
                    val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
                    val payload = when {
                        characteristic.isIndicatable() ->
                            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        characteristic.isNotifiable() ->
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        else ->
                            error("${characteristic.uuid} não suporta notificações/indicações")
                    }

                    characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
                        if (!gatt.setCharacteristicNotification(characteristic, true)) {
                            signalEndOfOperation()
                            return
                        }

                        cccDescriptor.value = payload
                        gatt.writeDescriptor(cccDescriptor)
                    } ?: this@ConnectionManager.run {
                        signalEndOfOperation()
                    }
                } ?: this@ConnectionManager.run {
                    signalEndOfOperation()
                }
            }
            is DisableNotifications -> with(operation) {
                gatt.findCharacteristic(characteristicUuid)?.let { characteristic ->
                    val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
                    characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
                        if (!gatt.setCharacteristicNotification(characteristic, false)) {
                            signalEndOfOperation()
                            return
                        }

                        cccDescriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(cccDescriptor)
                    } ?: this@ConnectionManager.run {
                        signalEndOfOperation()
                    }
                } ?: this@ConnectionManager.run {
                    signalEndOfOperation()
                }
            }
            is MtuRequest -> with(operation) {
                gatt.requestMtu(mtu)
            }
        }
    }

    // Implementa um callback BluetoothGattCallback para manipular eventos de conexão e operações GATT.
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    deviceGattMap[gatt.device] = gatt
                    Handler(Looper.getMainLooper()).post {
                        gatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    teardownConnection(gatt.device)
                }
            } else {
                if (pendingOperation is Connect) {
                    signalEndOfOperation()
                }
                teardownConnection(gatt.device)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    requestMtu(device, GATT_MAX_MTU_SIZE)
                    listeners.forEach { it.get()?.onConnectionSetupComplete?.invoke(this) }
                } else {
                    teardownConnection(gatt.device)
                }
            }

            if (pendingOperation is Connect) {
                signalEndOfOperation()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            listeners.forEach { it.get()?.onMtuChanged?.invoke(gatt.device, mtu) }

            if (pendingOperation is MtuRequest) {
                signalEndOfOperation()
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        listeners.forEach {
                            it.get()?.onCharacteristicRead?.invoke(
                                gatt.device,
                                this
                            )
                        }
                    }
                    else -> {
                        signalEndOfOperation()
                    }
                }
            }

            if (pendingOperation is CharacteristicRead) {
                signalEndOfOperation()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        listeners.forEach {
                            it.get()?.onCharacteristicWrite?.invoke(
                                gatt.device,
                                this
                            )
                        }
                    }
                    else -> {
                        signalEndOfOperation()
                    }
                }
            }

            if (pendingOperation is CharacteristicWrite) {
                signalEndOfOperation()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            with(characteristic) {
                listeners.forEach { it.get()?.onCharacteristicChanged?.invoke(gatt.device, this) }
            }
        }

        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            with(descriptor) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        listeners.forEach { it.get()?.onDescriptorRead?.invoke(gatt.device, this) }
                    }
                    else -> {
                        signalEndOfOperation()
                    }
                }
            }

            if (pendingOperation is DescriptorRead) {
                signalEndOfOperation()
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            with(descriptor) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        if (isCccd()) {
                            onCccdWrite(gatt, value, characteristic)
                        } else {
                            listeners.forEach {
                                it.get()?.onDescriptorWrite?.invoke(
                                    gatt.device,
                                    this
                                )
                            }
                        }
                    }
                    else -> {
                        signalEndOfOperation()
                    }
                }
            }

            if (descriptor.isCccd() &&
                (pendingOperation is EnableNotifications || pendingOperation is DisableNotifications)
            ) {
                signalEndOfOperation()
            } else if (!descriptor.isCccd() && pendingOperation is DescriptorWrite) {
                signalEndOfOperation()
            }
        }

        private fun onCccdWrite(
            gatt: BluetoothGatt,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic
        ) {
            val notificationsEnabled =
                value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                        value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
            val notificationsDisabled =
                value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)

            when {
                notificationsEnabled -> {
                    listeners.forEach {
                        it.get()?.onNotificationsEnabled?.invoke(
                            gatt.device,
                            characteristic
                        )
                    }
                }
                notificationsDisabled -> {
                    listeners.forEach {
                        it.get()?.onNotificationsDisabled?.invoke(
                            gatt.device,
                            characteristic
                        )
                    }
                }
            }
        }
    }
    private fun BluetoothDevice.isConnected() = deviceGattMap.containsKey(this)
}