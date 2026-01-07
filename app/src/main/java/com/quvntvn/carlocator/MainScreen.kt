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
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Couleurs
val NeonBlue = Color(0xFF2979FF)
val DeepBlack = Color(0xFF121212)
val SurfaceBlack = Color(0xFF1E1E1E)
val TextWhite = Color(0xFFEEEEEE)
val TextGrey = Color(0xFFAAAAAA)
val SuccessGreen = Color(0xFF00E676)
val ErrorRed = Color(0xFFFF5252)

@Composable
fun MainScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsManager = remember { PrefsManager(context) }

    // États
    var showGarageDialog by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(false) } // État pour le tuto
    val allCars by db.carDao().getAllCars().collectAsState(initial = emptyList())
    var connectedCarName by remember { mutableStateOf<String?>(null) }

    val currentCarsState = rememberUpdatedState(allCars)

    // Vérification Premier Lancement (Tuto)
    LaunchedEffect(Unit) {
        if (prefsManager.isFirstLaunch()) {
            showTutorialDialog = true
        }
    }

    // 1. Écouteur Dynamique (Interface Graphique)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                // On récupère l'appareil qui vient de bouger
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                if (device != null) {
                    // Est-ce une de nos voitures ?
                    val car = currentCarsState.value.find { it.macAddress.equals(device.address, ignoreCase = true) }

                    if (car != null) {
                        if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                            connectedCarName = car.name
                        } else if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                            // C'est ICI le correctif : on force la remise à zéro
                            // On vérifie juste que c'est bien la voiture actuellement affichée qui se déconnecte
                            if (connectedCarName == car.name) {
                                connectedCarName = null
                            }
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

    // 2. Vérification au DÉMARRAGE
    LaunchedEffect(allCars) {
        if (allCars.isNotEmpty()) {
            checkCurrentConnection(context, allCars) { name ->
                if (name != null) connectedCarName = name
            }
        }
    }

    var selectedCar by remember { mutableStateOf<CarLocation?>(null) }
    LaunchedEffect(allCars) {
        if (selectedCar == null && allCars.isNotEmpty()) {
            selectedCar = allCars.find { it.latitude != null } ?: allCars.first()
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(48.8566, 2.3522), 15f)
    }

    // Permissions
    var hasPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms -> hasPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    LaunchedEffect(selectedCar) {
        selectedCar?.let { car ->
            if (car.latitude != null && car.longitude != null) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(car.latitude, car.longitude), 16f))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepBlack)) {

        // --- LA CARTE ---
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasPermission),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, compassEnabled = false)
        ) {
            allCars.forEach { car ->
                if (car.latitude != null && car.longitude != null) {
                    Marker(
                        state = MarkerState(position = LatLng(car.latitude, car.longitude)),
                        title = car.name,
                        snippet = "Garée le ${formatDate(car.timestamp)}",
                        onClick = { selectedCar = car; false }
                    )
                }
            }
        }

        // --- BARRE DU HAUT (MENU) ---
        // Ajout de statusBarsPadding pour éviter l'encoche/barre de notif
        Box(modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            TopMenuCard(
                onGarageClick = { showGarageDialog = true }
            )
        }

        // --- PARTIE BASSE (INFOS) ---
        // Ajout de navigationBarsPadding pour éviter la barre des 3 boutons Android
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            SmallFloatingButton(
                icon = Icons.Rounded.MyLocation,
                onClick = {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { location ->
                            if (location != null) scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15f)) }
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End).padding(bottom = 16.dp)
            )

            // On passe l'état de connexion ici pour l'afficher en bas
            CarInfoCard(
                car = selectedCar,
                connectedCarName = connectedCarName,
                onParkClick = {
                    if (selectedCar == null) { showGarageDialog = true }
                    else { saveCurrentLocation(context, db, scope, selectedCar!!) }
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

        // --- DIALOGUES ---

        if (showGarageDialog) {
            GarageDialog(
                savedCars = allCars,
                currentSelectedCar = selectedCar,
                onAddCar = { mac, name -> scope.launch { db.carDao().insertOrUpdateCar(CarLocation(macAddress = mac, name = name)) } },
                onDeleteCar = { car -> scope.launch { db.carDao().deleteCar(car); if (selectedCar == car) selectedCar = null } },
                onCarSelect = { car -> selectedCar = car; showGarageDialog = false },
                onDismiss = { showGarageDialog = false }
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

// --- FONCTION MAGIQUE : Vérifie ce qui est DÉJÀ connecté ---
@SuppressLint("MissingPermission")
fun checkCurrentConnection(context: Context, cars: List<CarLocation>, onResult: (String?) -> Unit) {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter = bluetoothManager.adapter ?: return // Si pas de bluetooth, on quitte

    // Si le Bluetooth est éteint, on est sûr d'être déconnecté
    if (!adapter.isEnabled) {
        onResult(null)
        return
    }

    val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            val connectedDevices = proxy.connectedDevices
            var foundName: String? = null

            for (device in connectedDevices) {
                val car = cars.find { it.macAddress.equals(device.address, ignoreCase = true) }
                if (car != null) {
                    foundName = car.name
                    break
                }
            }

            // IMPORTANT : On renvoie le résultat (même si c'est null)
            // On ne le fait que si on a trouvé quelque chose pour éviter de faire clignoter l'UI
            // si un profil répond avant l'autre.
            if (foundName != null) {
                onResult(foundName)
            }

            adapter.closeProfileProxy(profile, proxy)
        }
        override fun onServiceDisconnected(profile: Int) {}
    }

    // On interroge les profils
    adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
    adapter.getProfileProxy(context, listener, BluetoothProfile.HEADSET)
}

// --- NOUVEAUX COMPOSANTS UI ---

@Composable
fun TopMenuCard(onGarageClick: () -> Unit) {
    Surface(
        modifier = Modifier.shadow(8.dp, CircleShape).clip(CircleShape).clickable { onGarageClick() },
        color = SurfaceBlack.copy(alpha = 0.95f),
        contentColor = TextWhite
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icône Hamburger Menu claire
            Icon(Icons.Rounded.Menu, contentDescription = "Menu", tint = NeonBlue)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                "Mon Garage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CarInfoCard(car: CarLocation?, connectedCarName: String?, onParkClick: () -> Unit, onNavigateClick: () -> Unit) {
    Surface(modifier = Modifier.shadow(16.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), color = SurfaceBlack) {
        Column(modifier = Modifier.padding(24.dp)) {

            // --- NOUVEAU : Statut de connexion en haut de la carte ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isConnected = connectedCarName != null && (car == null || connectedCarName == car.name)
                val statusColor = if (isConnected) SuccessGreen else ErrorRed
                val statusText = if (isConnected) "Connecté" else "Déconnecté"

                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                Spacer(modifier = Modifier.width(8.dp))
                Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DirectionsCar, null, tint = NeonBlue, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(car?.name ?: "Sélectionnez une voiture", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (car?.latitude != null) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Place, null, tint = TextGrey, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Dernière position connue", color = TextGrey, fontSize = 12.sp)
                        Text(formatDate(car.timestamp), color = TextWhite, fontSize = 14.sp)
                    }
                }
            } else { Text("Position inconnue ou non garée.", color = TextGrey, fontSize = 14.sp) }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onParkClick, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = SurfaceBlack), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, TextGrey.copy(alpha = 0.3f))) { Text("📍 Garer Ici", color = TextWhite) }
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = onNavigateClick, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonBlue), shape = RoundedCornerShape(16.dp), enabled = car?.latitude != null) { Icon(Icons.Rounded.NearMe, null); Spacer(modifier = Modifier.width(8.dp)); Text("Y Aller") }
            }
        }
    }
}

@Composable
fun TutorialDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBlack,
        title = { Text("Bienvenue sur CarLocator ! 🚗", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Voici comment utiliser l'application :", color = TextGrey)
                Spacer(modifier = Modifier.height(8.dp))
                Text("1. Ouvrez le Menu (Haut) et ajoutez votre voiture via Bluetooth.", color = TextWhite)
                Text("2. C'est tout ! L'appli enregistre votre position automatiquement quand le Bluetooth se déconnecte.", color = TextWhite)
                Text("3. Utilisez 'Y Aller' pour retrouver votre véhicule.", color = TextWhite)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)) {
                Text("C'est parti !", color = TextWhite)
            }
        }
    )
}

@Composable
fun SmallFloatingButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(onClick = onClick, modifier = modifier.size(48.dp), containerColor = SurfaceBlack, contentColor = TextWhite, shape = CircleShape) { Icon(icon, null, modifier = Modifier.size(20.dp)) }
}

fun formatDate(timestamp: Long): String = SimpleDateFormat("dd MMM à HH:mm", Locale.getDefault()).format(Date(timestamp))

fun saveCurrentLocation(context: Context, db: AppDatabase, scope: kotlinx.coroutines.CoroutineScope, car: CarLocation) {
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
    LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { location: Location? ->
        if (location != null) {
            scope.launch {
                db.carDao().insertOrUpdateCar(car.copy(latitude = location.latitude, longitude = location.longitude, timestamp = System.currentTimeMillis()))
                Toast.makeText(context, "${car.name} garée !", Toast.LENGTH_SHORT).show()
            }
        }
    }
}