package com.quvntvn.carlocator

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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

    // États des dialogues
    var showGarageDialog by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Données de la DB
    val allCars by db.carDao().getAllCars().collectAsStateWithLifecycle(initialValue = emptyList())
    var connectedCarName by remember { mutableStateOf<String?>(null) }
    var selectedCar by remember { mutableStateOf<CarLocation?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    // Initialisation et Permissions
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

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    val currentCarsState = rememberUpdatedState(allCars)

    // Écouteur Bluetooth pour l'état de connexion UI
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
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Gestion de la sélection automatique au démarrage
    LaunchedEffect(allCars) {
        if (allCars.isNotEmpty()) {
            checkCurrentConnection(context, allCars) { name -> if (name != null) connectedCarName = name }
        }
        // Si aucune voiture n'est sélectionnée ou si la voiture sélectionnée n'existe plus dans la liste
        if (allCars.isNotEmpty()) {
            if (selectedCar == null || allCars.none { it.macAddress == selectedCar?.macAddress }) {
                selectedCar = allCars.find { it.latitude != null } ?: allCars.first()
            }
        } else {
            selectedCar = null
        }
    }

    // Configuration de la carte
    val hasLocationPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(48.8566, 2.3522), 15f) }

    LaunchedEffect(selectedCar) {
        selectedCar?.let { car ->
            if (car.latitude != null && car.longitude != null) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(car.latitude, car.longitude), 16f))
            }
        }
    }

    // --- INTERFACE PRINCIPALE ---
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
                        onClick = { selectedCar = car; false }
                    )
                }
            }
        }

        // Menu du haut (Garage & Paramètres)
        Box(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
            TopMenuCard(
                onGarageClick = { showGarageDialog = true },
                onSettingsClick = { showSettingsDialog = true }
            )
        }

        // Zone du bas (Bouton Position & Carte Voiture)
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
                            val uri = Uri.parse("google.navigation:q=${car.latitude},${car.longitude}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"))
                        }
                    }
                }
            )
        }

        // Affichage des dialogues
        if (showGarageDialog) {
            GarageDialog(
                savedCars = allCars,
                currentSelectedCar = selectedCar,
                onAddCar = { mac, name ->
                    scope.launch {
                        val newCar = CarLocation(macAddress = mac, name = name)
                        db.carDao().insertOrUpdateCar(newCar)

                        checkCurrentConnection(context, listOf(newCar)) { connectedName ->
                            if (connectedName != null && prefsManager.isConnectionNotifEnabled()) {
                                val intent = Intent(context, CarBluetoothReceiver::class.java).apply {
                                    action = BluetoothDevice.ACTION_ACL_CONNECTED
                                    putExtra(BluetoothDevice.EXTRA_DEVICE,
                                        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
                                            .adapter.getRemoteDevice(mac))
                                }
                                context.sendBroadcast(intent)
                            }
                        }
                    }
                },
                onDeleteCar = { car ->
                    scope.launch {
                        db.carDao().deleteCar(car)
                        // ACTUALISATION : Si la voiture supprimée est la sélectionnée, on reset la sélection
                        if (selectedCar?.macAddress == car.macAddress) {
                            selectedCar = null
                        }
                    }
                },
                onRenameCar = { mac, newName -> scope.launch { db.carDao().updateCarName(mac, newName) } },
                onCarSelect = { car -> selectedCar = car; showGarageDialog = false },
                onDismiss = { showGarageDialog = false }
            )
        }

        if (showSettingsDialog) {
            SettingsDialog(prefs = prefsManager, onDismiss = { showSettingsDialog = false })
        }

        if (showTutorialDialog) {
            TutorialDialog(onDismiss = {
                prefsManager.setFirstLaunchDone()
                showTutorialDialog = false
            })
        }
    }
}

// --- COMPOSANTS UI ---

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
                Button(onClick = onParkClick, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = SurfaceBlack), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, TextGrey.copy(alpha = 0.3f))) { Text(stringResource(R.string.park_here), color = TextWhite) }
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = onNavigateClick, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonBlue), shape = RoundedCornerShape(16.dp), enabled = car?.latitude != null) { Icon(Icons.Rounded.NearMe, null); Spacer(modifier = Modifier.width(8.dp)); Text(stringResource(R.string.go_to)) }
            }
        }
    }
}

@Composable
fun SettingsDialog(prefs: PrefsManager, onDismiss: () -> Unit) {
    var isAppEnabled by remember { mutableStateOf(prefs.isAppEnabled()) }
    var isConnectionNotif by remember { mutableStateOf(prefs.isConnectionNotifEnabled()) }
    var isParkedNotif by remember { mutableStateOf(prefs.isParkedNotifEnabled()) }

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
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.notif_connection), color = TextWhite)
                    Switch(checked = isConnectionNotif, onCheckedChange = { isConnectionNotif = it; prefs.setConnectionNotifEnabled(it) }, enabled = isAppEnabled, colors = SwitchDefaults.colors(checkedThumbColor = NeonBlue, checkedTrackColor = SurfaceBlack))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.notif_parked), color = TextWhite)
                    Switch(checked = isParkedNotif, onCheckedChange = { isParkedNotif = it; prefs.setParkedNotifEnabled(it) }, enabled = isAppEnabled, colors = SwitchDefaults.colors(checkedThumbColor = NeonBlue, checkedTrackColor = SurfaceBlack))
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
            Column {
                Text(stringResource(R.string.tuto_intro), color = TextGrey, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.tuto_step1), color = TextWhite, fontSize = 16.sp, lineHeight = 24.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.tuto_step2), color = TextWhite, fontSize = 16.sp, lineHeight = 24.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.tuto_step3), color = TextWhite, fontSize = 16.sp, lineHeight = 24.sp)
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)) { Text(stringResource(R.string.tuto_button), color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold) } }
    )
}

@Composable
fun SmallFloatingButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(onClick = onClick, modifier = modifier.size(48.dp), containerColor = SurfaceBlack, contentColor = TextWhite, shape = CircleShape) { Icon(icon, null, modifier = Modifier.size(20.dp)) }
}

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
fun checkCurrentConnection(context: Context, cars: List<CarLocation>, onResult: (String?) -> Unit) {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter = bluetoothManager.adapter ?: return
    if (!adapter.isEnabled) { onResult(null); return }
    val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            val connectedDevices = proxy.connectedDevices
            var foundName: String? = null
            for (device in connectedDevices) {
                val car = cars.find { it.macAddress.equals(device.address, ignoreCase = true) }
                if (car != null) { foundName = car.name; break }
            }
            if (foundName != null) onResult(foundName)
            adapter.closeProfileProxy(profile, proxy)
        }
        override fun onServiceDisconnected(profile: Int) {}
    }
    adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
    adapter.getProfileProxy(context, listener, BluetoothProfile.HEADSET)
}

@Composable
fun GarageDialog(
    savedCars: List<CarLocation>,
    currentSelectedCar: CarLocation?,
    onAddCar: (String, String) -> Unit,
    onDeleteCar: (CarLocation) -> Unit,
    onRenameCar: (String, String) -> Unit,
    onCarSelect: (CarLocation) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showAddScreen by remember { mutableStateOf(false) }
    var scannedDevices by remember { mutableStateOf(listOf<BluetoothDevice>()) }
    var isBtEnabled by remember { mutableStateOf(isBluetoothEnabled(context)) }

    // État pour la modification du nom
    var carToRename by remember { mutableStateOf<CarLocation?>(null) }
    var newNameText by remember { mutableStateOf("") }

    LaunchedEffect(showAddScreen) {
        if (showAddScreen) {
            isBtEnabled = isBluetoothEnabled(context)
            if (isBtEnabled) scannedDevices = getBondedDevices(context)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkerSurface,
        title = {
            Text(if (showAddScreen) stringResource(R.string.add_car_title) else stringResource(R.string.menu_garage),
                color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            if (showAddScreen) {
                Column {
                    if (!isBtEnabled) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                            Icon(Icons.Rounded.BluetoothDisabled, null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.bt_disabled_title), color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(stringResource(R.string.bt_disabled_msg), color = TextGrey, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }, colors = ButtonDefaults.buttonColors(containerColor = SurfaceBlack)) { Text(stringResource(R.string.open_settings), color = TextWhite) }
                            TextButton(onClick = { isBtEnabled = isBluetoothEnabled(context); if(isBtEnabled) scannedDevices = getBondedDevices(context) }) { Text(stringResource(R.string.refresh_bt), color = NeonBlue) }
                        }
                    } else {
                        Text(stringResource(R.string.select_device_instruction), color = TextGrey, fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp))
                        if (scannedDevices.isEmpty()) {
                            Text(stringResource(R.string.no_device_found), color = TextGrey, fontSize = 14.sp)
                        } else {
                            LazyColumn(modifier = Modifier.height(250.dp)) {
                                items(scannedDevices) { device ->
                                    @SuppressLint("MissingPermission")
                                    val name = device.name ?: device.address
                                    Row(modifier = Modifier.fillMaxWidth().clickable { onAddCar(device.address, name); showAddScreen = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Bluetooth, null, tint = NeonBlue)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(name, color = TextWhite, fontSize = 16.sp)
                                    }
                                    Divider(color = SurfaceBlack)
                                }
                            }
                        }
                    }
                }
            } else {
                if (savedCars.isEmpty()) {
                    Text(stringResource(R.string.no_cars), color = TextGrey)
                } else {
                    LazyColumn(modifier = Modifier.height(250.dp)) {
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
            }
        },
        confirmButton = {
            if (!showAddScreen) {
                Button(onClick = { showAddScreen = true }, colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)) {
                    Text(stringResource(R.string.add_button), color = TextWhite)
                }
            }
        },
        dismissButton = {
            Button(onClick = { if (showAddScreen) showAddScreen = false else onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) {
                Text(if (showAddScreen) stringResource(R.string.back) else stringResource(R.string.close), color = TextGrey)
            }
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
fun SavedCarItem(
    car: CarLocation,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    val backgroundColor = if (isSelected) NeonBlue.copy(alpha = 0.2f) else SurfaceBlack
    val borderColor = if (isSelected) NeonBlue else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.DirectionsCar, null, tint = if (isSelected) NeonBlue else TextWhite)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(car.name, fontWeight = FontWeight.Bold, color = TextWhite)
            Text(
                text = if(car.latitude != null) "${stringResource(R.string.parked_on)}${formatDate(car.timestamp)}" else stringResource(R.string.unknown_position),
                style = MaterialTheme.typography.bodySmall,
                color = TextGrey
            )
        }

        IconButton(onClick = onRename) {
            Icon(Icons.Rounded.Edit, null, tint = TextGrey, modifier = Modifier.size(20.dp))
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
        }
    }
}

@SuppressLint("MissingPermission")
fun getBondedDevices(context: Context): List<BluetoothDevice> {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter = bluetoothManager.adapter

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }

    return adapter?.bondedDevices?.toList() ?: emptyList()
}

fun isBluetoothEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    return manager.adapter?.isEnabled == true
}