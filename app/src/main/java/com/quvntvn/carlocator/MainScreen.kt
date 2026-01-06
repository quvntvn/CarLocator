package com.quvntvn.carlocator

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
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

@Composable
fun MainScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showGarageDialog by remember { mutableStateOf(false) }
    val allCars by db.carDao().getAllCars().collectAsState(initial = emptyList())
    var connectedCarName by remember { mutableStateOf<String?>(null) }

    val currentCarsState = rememberUpdatedState(allCars)

    // 1. Écouteur Dynamique (Changements PENDANT que l'app est ouverte)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED || intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                    // Si un événement arrive, on revérifie tout (c'est plus sûr)
                    checkCurrentConnection(context, currentCarsState.value) { name -> connectedCarName = name }
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

    // 2. Vérification au DÉMARRAGE (Si déjà connecté)
    LaunchedEffect(allCars) {
        if (allCars.isNotEmpty()) {
            checkCurrentConnection(context, allCars) { name ->
                if (name != null) connectedCarName = name
            }
        }
    }

    // ... (Le reste du code UI reste identique : Map, Marker, GarageDialog, etc.) ...
    // Je remets la structure principale pour la compilation

    // Voiture sélectionnée
    var selectedCar by remember { mutableStateOf<CarLocation?>(null) }
    LaunchedEffect(allCars) {
        if (selectedCar == null && allCars.isNotEmpty()) {
            selectedCar = allCars.find { it.latitude != null } ?: allCars.first()
        }
    }

    // Caméra
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
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasPermission),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false)
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

        TopStatusCard(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp, start = 16.dp, end = 16.dp),
            carCount = allCars.size,
            connectedCarName = connectedCarName,
            onGarageClick = { showGarageDialog = true }
        )

        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth()) {
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
            CarInfoCard(
                car = selectedCar,
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
    }
}

// --- FONCTION MAGIQUE : Vérifie ce qui est DÉJÀ connecté ---
@SuppressLint("MissingPermission")
fun checkCurrentConnection(context: Context, cars: List<CarLocation>, onResult: (String?) -> Unit) {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter = bluetoothManager.adapter ?: return

    // On utilise les Profils Proxy pour interroger le système (A2DP = Audio voiture, HEADSET = Kit mains libres)
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
            // Si on a trouvé une voiture, on met à jour. Sinon, on laisse (peut-être qu'un autre profil la trouvera)
            if (foundName != null) {
                onResult(foundName)
            }
            adapter.closeProfileProxy(profile, proxy)
        }
        override fun onServiceDisconnected(profile: Int) {}
    }

    // On interroge les deux profils classiques des voitures
    adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
    adapter.getProfileProxy(context, listener, BluetoothProfile.HEADSET)
}

// ... Les autres composants (TopStatusCard, CarInfoCard, etc.) restent identiques à la version précédente ...
// Copie-colle les ici si tu as tout effacé, ou garde ceux de ton fichier actuel.
// Je remets les signatures pour éviter les erreurs de compilation :

@Composable
fun TopStatusCard(modifier: Modifier, carCount: Int, connectedCarName: String?, onGarageClick: () -> Unit) {
    Surface(modifier = modifier.shadow(8.dp, CircleShape).clip(CircleShape), color = SurfaceBlack.copy(alpha = 0.95f), contentColor = TextWhite) {
        Row(modifier = Modifier.clickable { onGarageClick() }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (connectedCarName != null) SuccessGreen else if (carCount > 0) Color(0xFFFF9800) else Color.Red))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Mon Garage", style = MaterialTheme.typography.labelSmall, color = TextGrey)
                Text(connectedCarName?.let { "Connecté à $it" } ?: if (carCount > 0) "$carCount voiture(s)" else "Aucune voiture", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if(connectedCarName != null) SuccessGreen else TextWhite)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.DirectionsCar, null, tint = TextWhite)
        }
    }
}

@Composable
fun CarInfoCard(car: CarLocation?, onParkClick: () -> Unit, onNavigateClick: () -> Unit) {
    Surface(modifier = Modifier.shadow(16.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), color = SurfaceBlack) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DirectionsCar, null, tint = NeonBlue, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(car?.name ?: "Sélectionnez une voiture", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (car?.latitude != null) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Place, null, tint = TextGrey, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Dernière position connue", color = TextGrey, fontSize = 12.sp)
                        Text(formatDate(car.timestamp), color = TextWhite, fontSize = 16.sp)
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