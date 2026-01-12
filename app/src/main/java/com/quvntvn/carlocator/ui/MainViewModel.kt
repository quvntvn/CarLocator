package com.quvntvn.carlocator.ui

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.annotation.SuppressLint
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.quvntvn.carlocator.R
import com.quvntvn.carlocator.data.AppDatabase
import com.quvntvn.carlocator.data.CarLocation
import com.quvntvn.carlocator.data.PrefsManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.regex.Pattern

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val prefsManager = PrefsManager(appContext)
    private val db = AppDatabase.getInstance(appContext)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)

    val cars: StateFlow<List<CarLocation>> = db.carDao().getAllCars()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCar = MutableStateFlow<CarLocation?>(null)
    val selectedCar: StateFlow<CarLocation?> = _selectedCar.asStateFlow()

    private val _connectedCarName = MutableStateFlow<String?>(null)
    val connectedCarName: StateFlow<String?> = _connectedCarName.asStateFlow()

    private val _isBatteryOptimized = MutableStateFlow(false)
    val isBatteryOptimized: StateFlow<Boolean> = _isBatteryOptimized.asStateFlow()

    private val _isAppEnabled = MutableStateFlow(prefsManager.isAppEnabled())
    val isAppEnabled: StateFlow<Boolean> = _isAppEnabled.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    init {
        refreshBatteryOptimizationState()
        observeCars()
    }

    fun isFirstLaunch(): Boolean = prefsManager.isFirstLaunch()

    fun setFirstLaunchDone() {
        prefsManager.setFirstLaunchDone()
    }

    fun setAppEnabled(enabled: Boolean) {
        prefsManager.setAppEnabled(enabled)
        _isAppEnabled.value = enabled
    }

    fun refreshBatteryOptimizationState() {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        _isBatteryOptimized.value = !pm.isIgnoringBatteryOptimizations(appContext.packageName)
    }

    fun getMissingPermissions(): List<String> {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.filter { perm ->
            ActivityCompat.checkSelfPermission(appContext, perm) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun handleAssociationResult(data: Intent?) {
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
        }

        device?.let {
            handleNewCar(it.address, it.name.orEmpty())
        }
    }

    fun startAssociationProcess(launchIntentSender: (IntentSender) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val deviceManager = appContext.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

            val deviceFilter = BluetoothDeviceFilter.Builder()
                .setNamePattern(Pattern.compile(".*"))
                .build()

            val pairingRequest = AssociationRequest.Builder()
                .addDeviceFilter(deviceFilter)
                .setSingleDevice(false)
                .build()

            emitToast(appContext.getString(R.string.bt_association_search), Toast.LENGTH_LONG)

            deviceManager.associate(pairingRequest, object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    launchIntentSender(intentSender)
                }

                @RequiresApi(Build.VERSION_CODES.TIRAMISU)
                override fun onAssociationCreated(associationInfo: android.companion.AssociationInfo) {
                    val mac = associationInfo.deviceMacAddress?.toString()
                    if (mac != null) {
                        handleNewCar(mac, "")
                    }
                }

                override fun onFailure(error: CharSequence?) {
                    val message = error?.toString() ?: appContext.getString(R.string.error_unknown)
                    emitToast(appContext.getString(R.string.bt_association_error, message), Toast.LENGTH_SHORT)
                }
            }, null)
        } else {
            emitToast(appContext.getString(R.string.bt_feature_unavailable), Toast.LENGTH_SHORT)
        }
    }

    @SuppressLint("MissingPermission")
    fun handleNewCar(mac: String, name: String) {
        viewModelScope.launch {
            val normalizedMac = mac.uppercase(Locale.ROOT)
            if (normalizedMac == "02:00:00:00:00:00" || !BluetoothAdapter.checkBluetoothAddress(normalizedMac)) {
                emitToast(appContext.getString(R.string.bt_invalid_address), Toast.LENGTH_SHORT)
                return@launch
            }
            val normalizedName = name.trim().ifBlank {
                val bluetoothName = runCatching {
                    val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    val adapter = bluetoothManager.adapter
                    adapter.getRemoteDevice(normalizedMac).name
                }.getOrNull()
                bluetoothName?.takeIf { it.isNotBlank() }?.trim()
                    ?: appContext.getString(R.string.default_car_name, normalizedMac.takeLast(4))
            }
            val existingCar = db.carDao().getCarByMac(normalizedMac)
            val newCar = if (existingCar != null && normalizedName.isNotBlank() && existingCar.name != normalizedName) {
                existingCar.copy(name = normalizedName)
            } else {
                existingCar ?: CarLocation(macAddress = normalizedMac, name = normalizedName)
            }
            db.carDao().insertOrUpdateCar(newCar)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val deviceManager = appContext.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
                    deviceManager.startObservingDevicePresence(normalizedMac)
                } catch (e: Exception) {
                    // Ignorer si déjà surveillé
                }
            }

            try {
                val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter
                val device = adapter.getRemoteDevice(normalizedMac)

                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    val started = device.createBond()
                    val messageId = if (started) {
                        R.string.bt_pairing_started
                    } else {
                        R.string.bt_pairing_pending
                    }
                    emitToast(appContext.getString(messageId), Toast.LENGTH_LONG)
                } else {
                    emitToast(appContext.getString(R.string.bt_already_paired), Toast.LENGTH_SHORT)
                }
            } catch (e: Exception) {
                emitToast(appContext.getString(R.string.bt_pairing_system), Toast.LENGTH_SHORT)
            }

            _selectedCar.value = newCar
            prefsManager.saveLastSelectedCarMac(normalizedMac)
            _uiEvents.emit(UiEvent.CloseGarageDialog)
        }
    }

    fun selectCar(car: CarLocation) {
        _selectedCar.value = car
        prefsManager.saveLastSelectedCarMac(car.macAddress)
    }

    fun renameCar(mac: String, newName: String) {
        viewModelScope.launch {
            db.carDao().updateCarName(mac, newName)
        }
    }

    fun deleteCar(car: CarLocation) {
        viewModelScope.launch {
            db.carDao().deleteCar(car)
            if (_selectedCar.value?.macAddress == car.macAddress) {
                _selectedCar.value = null
                prefsManager.saveLastSelectedCarMac("")
            }
            disassociateDevice(car.macAddress)
        }
    }

    fun saveCurrentLocation(car: CarLocation) {
        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                viewModelScope.launch {
                    db.carDao().insertOrUpdateCar(
                        car.copy(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    emitToast("${car.name} ${appContext.getString(R.string.car_parked_toast)}", Toast.LENGTH_SHORT)                }
            }
        }
    }

    fun handleBluetoothEvent(action: String?, device: BluetoothDevice?) {
        if (!isAppEnabled.value) return
        if (device == null) return
        val currentCars = cars.value
        val car = currentCars.find { it.macAddress.equals(device.address, ignoreCase = true) } ?: return
        if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            _connectedCarName.value = car.name
        } else if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            if (_connectedCarName.value == car.name) {
                _connectedCarName.value = null
            }
        }
    }

    private fun observeCars() {
        viewModelScope.launch {
            cars.collect { carList ->
                if (carList.isNotEmpty()) {
                    checkCurrentConnection(carList)
                    val lastMac = prefsManager.getLastSelectedCarMac()
                    val lastSavedCar = carList.find { it.macAddress == lastMac }
                    val fallbackCar = carList.find { it.latitude != null } ?: carList.first()
                    val selected = lastSavedCar ?: fallbackCar
                    if (_selectedCar.value?.macAddress != selected.macAddress) {
                        _selectedCar.value = selected
                    }
                } else {
                    _selectedCar.value = null
                }
            }
        }
    }

    private fun checkCurrentConnection(savedCars: List<CarLocation>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return
        if (!adapter.isEnabled) return
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val connectedDevices = proxy.connectedDevices
                var foundName: String? = null
                for (device in connectedDevices) {
                    val car = savedCars.find { it.macAddress.equals(device.address, ignoreCase = true) }
                    if (car != null) {
                        foundName = car.name
                        break
                    }
                }
                if (foundName != null) {
                    _connectedCarName.value = foundName
                }
                adapter.closeProfileProxy(profile, proxy)
            }

            override fun onServiceDisconnected(profile: Int) = Unit
        }
        adapter.getProfileProxy(appContext, listener, BluetoothProfile.A2DP)
        adapter.getProfileProxy(appContext, listener, BluetoothProfile.HEADSET)
    }

    private fun disassociateDevice(macAddress: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val deviceManager = appContext.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val association = deviceManager.myAssociations.firstOrNull { association ->
                    association.deviceMacAddress?.toString()?.equals(macAddress, ignoreCase = true) == true
                }
                association?.let { deviceManager.disassociate(it.id) }
            } else {
                @Suppress("DEPRECATION")
                deviceManager.disassociate(macAddress)
            }
        } catch (e: Exception) {
            // Ignorer les erreurs de désassociation
        }
    }

    private fun emitToast(message: String, duration: Int) {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.Toast(message, duration))
        }
    }

    sealed class UiEvent {
        data class Toast(val message: String, val duration: Int) : UiEvent()
        data object CloseGarageDialog : UiEvent()
    }
}
