package com.quvntvn.carlocator

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

// Couleurs thématiques
val NeonBlue = Color(0xFF2979FF)
val DeepBlack = Color(0xFF121212)
val SurfaceBlack = Color(0xFF1E1E1E)
val TextWhite = Color(0xFFEEEEEE)
val TextGrey = Color(0xFFAAAAAA)
val SuccessGreen = Color(0xFF00E676)
val ErrorRed = Color(0xFFFF5252)
val DarkerSurface = Color(0xFF101010)

@Composable
fun MainScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsManager = remember { PrefsManager(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // États des dialogues
    var showGarageDialog by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // État optimisation batterie (Safety Check)
    val isBatteryOptimized = remember { mutableStateOf(false) }

    // Données de la DB
    val allCars by db.carDao().getAllCars().collectAsStateWithLifecycle(initialValue = emptyList())
    var connectedCarName by remember { mutableStateOf<String?>(null) }
    var selectedCar by remember { mutableStateOf<CarLocation?>(null) }

    // Vérification batterie au lancement
    fun updateBatteryOptimizationState() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        // Renvoie true si l'optimisation est active (mauvais pour nous)
        isBatteryOptimized.value = !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    LaunchedEffect(Unit) {
        updateBatteryOptimizationState()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateBatteryOptimizationState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- LOGIQUE COMMUNE D'AJOUT ---
    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    fun handleNewCar(mac: String, name: String) {
        scope.launch {
            val normalizedMac = mac.uppercase(Locale.ROOT)
            if (normalizedMac == "02:00:00:00:00:00" || !BluetoothAdapter.checkBluetoothAddress(normalizedMac)) {
                Toast.makeText(context, context.getString(R.string.bt_invalid_address), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val normalizedName = name.trim().ifBlank {
                val bluetoothName = runCatching {
                    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    val adapter = bluetoothManager.adapter
                    adapter.getRemoteDevice(normalizedMac).name
                }.getOrNull()
                bluetoothName?.takeIf { it.isNotBlank() }?.trim()
                    ?: context.getString(R.string.default_car_name, normalizedMac.takeLast(4))
            }
            val existingCar = db.carDao().getCarByMac(normalizedMac)
            val newCar = if (existingCar != null && normalizedName.isNotBlank() && existingCar.name != normalizedName) {
                existingCar.copy(name = normalizedName)
            } else {
                existingCar ?: CarLocation(macAddress = normalizedMac, name = normalizedName)
            }
            db.carDao().insertOrUpdateCar(newCar)

            // 1. Activer la surveillance CDM
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val deviceManager = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
                    deviceManager.startObservingDevicePresence(normalizedMac)
                } catch (e: Exception) {
                    // Ignorer si déjà surveillé
                }
            }

            // 2. TENTATIVE D'APPAIRAGE BLUETOOTH (BONDING)
            try {
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter
                val device = adapter.getRemoteDevice(normalizedMac)

                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    val started = device.createBond()
                    val messageId = if (started) {
                        R.string.bt_pairing_started
                    } else {
                        R.string.bt_pairing_pending
                    }
                    Toast.makeText(context, context.getString(messageId), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.bt_already_paired), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.bt_pairing_system), Toast.LENGTH_SHORT).show()
            }

            selectedCar = newCar
            prefsManager.saveLastSelectedCarMac(normalizedMac)
            showGarageDialog = false
        }
    }

    // --- GESTION DE L'ASSOCIATION (Companion Device Manager) ---
    val associationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return@rememberLauncherForActivityResult
        }
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
            }

            device?.let {
                @SuppressLint("MissingPermission")
                val name = it.name.orEmpty()
                handleNewCar(it.address, name)
            }
        }
    }

    fun startAssociationProcess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val deviceManager = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

            val deviceFilter = BluetoothDeviceFilter.Builder()
                .setNamePattern(Pattern.compile(".*"))
                .build()

            val pairingRequest = AssociationRequest.Builder()
                .addDeviceFilter(deviceFilter)
                .setSingleDevice(false)
                .build()

            Toast.makeText(context, context.getString(R.string.bt_association_search), Toast.LENGTH_LONG).show()

            deviceManager.associate(pairingRequest, object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    val intentSenderRequest = IntentSenderRequest.Builder(intentSender).build()
                    associationLauncher.launch(intentSenderRequest)
                }

                @RequiresApi(Build.VERSION_CODES.TIRAMISU)
                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    val mac = associationInfo.deviceMacAddress?.toString()
                    if (mac != null) {
                        handleNewCar(mac, "")
                    }
                }

                override fun onFailure(error: CharSequence?) {
                    val message = error?.toString() ?: context.getString(R.string.error_unknown)
                    Toast.makeText(context, context.getString(R.string.bt_association_error, message), Toast.LENGTH_SHORT).show()
                }
            }, null)
        } else {
            Toast.makeText(context, context.getString(R.string.bt_feature_unavailable), Toast.LENGTH_SHORT).show()
        }
    }
    // -----------------------------------------------------------

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        if (prefsManager.isFirstLaunch()) showTutorialDialog = true
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missingPerms = perms.filter { perm ->
            ActivityCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPerms.isNotEmpty()) {
            permissionLauncher.launch(missingPerms.toTypedArray())
        }
    }

    val currentCarsState = rememberUpdatedState(allCars)

    // Écouteur Bluetooth
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!prefsManager.isAppEnabled()) return
                val action = intent.action
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                if (device != null) {
                    val car = currentCarsState.value.find { it.macAddress.equals(device.address, ignoreCase = true) }
                    if (car != null) {
                        if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                            connectedCarName = car.name
                        } else if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                            if (connectedCarName == car.name) connectedCarName = null
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(allCars) {
        if (allCars.isNotEmpty()) {
            checkCurrentConnection(context, allCars) { name -> if (name != null) connectedCarName = name }
            if (selectedCar == null || allCars.none { it.macAddress == selectedCar?.macAddress }) {
                val lastMac = prefsManager.getLastSelectedCarMac()
                val lastSavedCar = allCars.find { it.macAddress == lastMac }
                selectedCar = lastSavedCar ?: allCars.find { it.latitude != null } ?: allCars.first()
            }
        } else {
            selectedCar = null
        }
    }

    val hasLocationPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(48.8566, 2.3522), 15f) }

    LaunchedEffect(selectedCar) {
        selectedCar?.let { car ->
            if (car.latitude != null && car.longitude != null) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(car.latitude, car.longitude), 16f))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepBlack)) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, compassEnabled = false)
        ) {
            allCars.forEach { car ->
                if (car.latitude != null && car.longitude != null) {
                    Marker(
                        state = MarkerState(position = LatLng(car.latitude, car.longitude)),
                        title = car.name,
                        snippet = "${stringResource(R.string.parked_on)}${formatDate(car.timestamp)}",
                        onClick = {
                            selectedCar = car
                            prefsManager.saveLastSelectedCarMac(car.macAddress)
                            false
                        }
                    )
                }
            }
        }

        // --- MENU DU HAUT + ALERTE BATTERIE ---
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            TopMenuCard(
                onGarageClick = { showGarageDialog = true },
                onSettingsClick = { showSettingsDialog = true }
            )

            // BANDEAU D'ALERTE ROUGE (Si batterie optimisée)
            if (isBatteryOptimized.value) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        } catch(e: Exception) {
                            Toast.makeText(context, context.getString(R.string.open_settings_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.BatteryAlert, contentDescription = null, tint = TextWhite)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.battery_optimization_warning),
                            color = TextWhite,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- PARTIE BASSE (GPS + INFO) ---
        Column(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth()) {
            SmallFloatingButton(
                icon = Icons.Rounded.MyLocation,
                onClick = {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { location ->
                            if (location != null) scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15f)) }
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.perm_gps_required), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.align(Alignment.End).padding(bottom = 16.dp)
            )

            CarInfoCard(
                car = selectedCar,
                connectedCarName = connectedCarName,
                onParkClick = {
                    if (selectedCar == null) showGarageDialog = true
                    else saveCurrentLocation(context, db, scope, selectedCar!!)
                },
                onNavigateClick = {
                    selectedCar?.let { car ->
                        if (car.latitude != null) {
                            // Ouvre n'importe quelle app GPS installée (Waze, Maps...)
                            val uri = Uri.parse("geo:${car.latitude},${car.longitude}?q=${car.latitude},${car.longitude}(${car.name})")
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(mapIntent)
                        }
                    }
                }
            )
        }

        // --- DIALOGUES ---
        if (showGarageDialog) {
            GarageDialog(
                savedCars = allCars,
                currentSelectedCar = selectedCar,
                onAddCarClick = { startAssociationProcess() },
                onDeleteCar = { car ->
                    scope.launch {
                        db.carDao().deleteCar(car)
                        if (selectedCar?.macAddress == car.macAddress) {
                            selectedCar = null
                            prefsManager.saveLastSelectedCarMac("")
                        }
                    }
                },
                onRenameCar = { mac, newName -> scope.launch { db.carDao().updateCarName(mac, newName) } },
                onCarSelect = { car ->
                    selectedCar = car
                    prefsManager.saveLastSelectedCarMac(car.macAddress)
                    showGarageDialog = false
                },
                onDismiss = { showGarageDialog = false }
            )
        }

        if (showSettingsDialog) {
            SettingsDialog(
                context = context,
                prefs = prefsManager,
                onDismiss = { showSettingsDialog = false },
                onShowTutorial = {
                    showSettingsDialog = false
                    showTutorialDialog = true
                }
            )
        }

        if (showTutorialDialog) {
            TutorialDialog(onDismiss = {
                prefsManager.setFirstLaunchDone()
                showTutorialDialog = false
            })
        }
    }
}

// -------------------------------------------------------------------------
// --- COMPOSANTS UI AUXILIAIRES ---
// -------------------------------------------------------------------------

@Composable
fun TopMenuCard(onGarageClick: () -> Unit, onSettingsClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)),
        color = SurfaceBlack,
        contentColor = TextWhite
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f).clickable { onGarageClick() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Menu, contentDescription = null, tint = NeonBlue)
                Spacer(modifier = Modifier.width(16.dp))
                Text(stringResource(R.string.menu_garage), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Rounded.Settings, contentDescription = null, tint = TextGrey)
            }
        }
    }
}

@Composable
fun CarInfoCard(car: CarLocation?, connectedCarName: String?, onParkClick: () -> Unit, onNavigateClick: () -> Unit) {
    Surface(modifier = Modifier.shadow(16.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), color = SurfaceBlack) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isConnected = connectedCarName != null && (car == null || connectedCarName == car.name)
                val statusColor = if (isConnected) SuccessGreen else ErrorRed
                val statusText = stringResource(if (isConnected) R.string.connected else R.string.disconnected)
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                Spacer(modifier = Modifier.width(8.dp))
                Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DirectionsCar, null, tint = NeonBlue, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(car?.name ?: stringResource(R.string.select_car), color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (car?.latitude != null) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Place, null, tint = TextGrey, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.parked_on) + formatDate(car.timestamp), color = TextWhite, fontSize = 14.sp)
                    }
                }
            } else { Text(stringResource(R.string.unknown_position), color = TextGrey, fontSize = 14.sp) }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onParkClick, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = SurfaceBlack), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, TextGrey.copy(alpha = 0.3f))) { Text(stringResource(R.string.park_here), color = TextWhite) }
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = onNavigateClick, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonBlue), shape = RoundedCornerShape(16.dp), enabled = car?.latitude != null) { Icon(Icons.Rounded.NearMe, null); Spacer(modifier = Modifier.width(8.dp)); Text(stringResource(R.string.go_to)) }
            }
        }
    }
}

@Composable
fun GarageDialog(
    savedCars: List<CarLocation>,
    currentSelectedCar: CarLocation?,
    onAddCarClick: () -> Unit,
    onDeleteCar: (CarLocation) -> Unit,
    onRenameCar: (String, String) -> Unit,
    onCarSelect: (CarLocation) -> Unit,
    onDismiss: () -> Unit
) {
    var carToRename by remember { mutableStateOf<CarLocation?>(null) }
    var newNameText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkerSurface,
        title = {
            Text(stringResource(R.string.menu_garage), color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            Column {
                if (savedCars.isEmpty()) {
                    Text(stringResource(R.string.no_cars), color = TextGrey, modifier = Modifier.padding(bottom = 16.dp))
                } else {
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(savedCars) { car ->
                            SavedCarItem(
                                car = car,
                                isSelected = currentSelectedCar?.macAddress == car.macAddress,
                                onClick = { onCarSelect(car) },
                                onDelete = { onDeleteCar(car) },
                                onRename = {
                                    carToRename = car
                                    newNameText = car.name
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = SurfaceBlack,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Info, null, tint = NeonBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.garage_add_info),
                            color = TextGrey,
                            fontSize = 12.sp
                        )
                    }
                }

                Button(
                    onClick = onAddCarClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Add, null, tint = TextWhite)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add_button), color = TextWhite)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = TextGrey) }
        }
    )

    if (carToRename != null) {
        AlertDialog(
            onDismissRequest = { carToRename = null },
            containerColor = SurfaceBlack,
            title = { Text(stringResource(R.string.settings_title), color = TextWhite) },
            text = {
                TextField(
                    value = newNameText,
                    onValueChange = { newNameText = it },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = DarkerSurface,
                        unfocusedContainerColor = DarkerSurface
                    ),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameCar(carToRename!!.macAddress, newNameText)
                    carToRename = null
                }) { Text(stringResource(R.string.ok), color = NeonBlue) }
            },
            dismissButton = {
                TextButton(onClick = { carToRename = null }) { Text(stringResource(R.string.back), color = TextGrey) }
            }
        )
    }
}

@Composable
fun SettingsDialog(context: Context, prefs: PrefsManager, onDismiss: () -> Unit, onShowTutorial: () -> Unit) {
    var isAppEnabled by remember { mutableStateOf(prefs.isAppEnabled()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkerSurface,
        title = { Text(stringResource(R.string.settings_title), color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.enable_app), color = TextWhite, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.enable_app_desc), color = TextGrey, fontSize = 12.sp)
                    }
                    Switch(checked = isAppEnabled, onCheckedChange = { isAppEnabled = it; prefs.setAppEnabled(it) }, colors = SwitchDefaults.colors(checkedThumbColor = NeonBlue, checkedTrackColor = SurfaceBlack))
                }
                Divider(color = TextGrey.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.settings_system_section), color = NeonBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.open_settings_error), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceBlack),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TextGrey.copy(alpha = 0.3f))
                ) {
                    Icon(Icons.Rounded.SettingsSystemDaydream, null, tint = TextWhite)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_open_app_settings), color = TextWhite)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onShowTutorial() }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_show_tuto), color = NeonBlue, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.HelpOutline, null, tint = NeonBlue)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok), color = NeonBlue) } }
    )
}

@Composable
fun TutorialDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkerSurface,
        title = { Text(stringResource(R.string.tuto_title), color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 22.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.tuto_intro), color = TextGrey, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Text(stringResource(R.string.tuto_step1_title), color = NeonBlue, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.tuto_step1_body), color = TextWhite, fontSize = 14.sp)

                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.tuto_step2), color = TextWhite, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.tuto_step3), color = TextWhite, fontSize = 14.sp)

                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.BatteryAlert, null, tint = ErrorRed, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.tuto_battery_title), color = ErrorRed, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.tuto_battery_msg), color = TextGrey, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)) { Text(stringResource(R.string.tuto_button), color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold) } }
    )
}

@Composable
fun SmallFloatingButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(onClick = onClick, modifier = modifier.size(48.dp), containerColor = SurfaceBlack, contentColor = TextWhite, shape = CircleShape) { Icon(icon, null, modifier = Modifier.size(20.dp)) }
}

@Composable
fun SavedCarItem(car: CarLocation, isSelected: Boolean, onClick: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit) {
    val backgroundColor = if (isSelected) NeonBlue.copy(alpha = 0.2f) else SurfaceBlack
    val borderColor = if (isSelected) NeonBlue else Color.Transparent
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(backgroundColor, RoundedCornerShape(12.dp)).border(1.dp, borderColor, RoundedCornerShape(12.dp)).clickable { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.DirectionsCar, null, tint = if (isSelected) NeonBlue else TextWhite)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(car.name, fontWeight = FontWeight.Bold, color = TextWhite)
            Text(text = if(car.latitude != null) "${stringResource(R.string.parked_on)}${formatDate(car.timestamp)}" else stringResource(R.string.unknown_position), style = MaterialTheme.typography.bodySmall, color = TextGrey)
        }
        IconButton(onClick = onRename) { Icon(Icons.Rounded.Edit, null, tint = TextGrey, modifier = Modifier.size(20.dp)) }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, null, tint = ErrorRed, modifier = Modifier.size(20.dp)) }
    }
}

// -------------------------------------------------------------------------
// --- FONCTIONS UTILITAIRES ---
// -------------------------------------------------------------------------

fun formatDate(timestamp: Long): String = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))

fun saveCurrentLocation(context: Context, db: AppDatabase, scope: kotlinx.coroutines.CoroutineScope, car: CarLocation) {
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
    LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { location: Location? ->
        if (location != null) {
            scope.launch {
                db.carDao().insertOrUpdateCar(car.copy(latitude = location.latitude, longitude = location.longitude, timestamp = System.currentTimeMillis()))
                Toast.makeText(context, "${car.name} ${context.getString(R.string.car_parked_toast)}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun checkCurrentConnection(
    context: Context,
    savedCars: List<CarLocation>,
    onResult: (String?) -> Unit
) {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bluetoothManager?.adapter ?: return
    if (!adapter.isEnabled) return
    val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            val connectedDevices = proxy.connectedDevices
            var foundName: String? = null
            for (device in connectedDevices) {
                val car = savedCars.find { it.macAddress?.equals(device.address, ignoreCase = true) == true }
                if (car != null) {
                    foundName = car.name
                    break
                }
            }
            if (foundName != null) onResult(foundName)
            adapter.closeProfileProxy(profile, proxy)
        }
        override fun onServiceDisconnected(profile: Int) {}
    }
    adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
    adapter.getProfileProxy(context, listener, BluetoothProfile.HEADSET)
}
