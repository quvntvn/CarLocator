package com.quvntvn.carlocator

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent // You need to add this import
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // You need to add this import
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.bluetooth.BluetoothDevice // You need to add this import
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// --- DIALOGUE GARAGE (Ajout/Suppression) ---
@Composable
fun GarageDialog(
    savedCars: List<CarLocation>,
    currentSelectedCar: CarLocation?,
    onAddCar: (String, String) -> Unit,
    onDeleteCar: (CarLocation) -> Unit,
    onCarSelect: (CarLocation) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddScreen by remember { mutableStateOf(false) }
    var scannedDevices by remember { mutableStateOf(listOf<BluetoothDevice>()) }
    val context = LocalContext.current

    // On vérifie l'état du Bluetooth à chaque recomposition
    var isBtEnabled by remember { mutableStateOf(isBluetoothEnabled(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        // These colors are not defined, you might need to define them in a Theme.kt file
        // containerColor = DarkerSurface,
        title = {
            Text(if (showAddScreen) "Ajouter une voiture" else "Mon Garage", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            if (showAddScreen) {
                Column {
                    // Vérification dynamique
                    if (!isBtEnabled) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        ) {
                            // Icon(Icons.Rounded.BluetoothDisabled, null, modifier = Modifier.size(48.dp)) // This Icon doesn't exist by default
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Le Bluetooth est désactivé.",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Veuillez l'activer pour voir vos appareils.",
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            // Bouton raccourci vers les paramètres Bluetooth
                            Button(
                                onClick = {
                                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("Ouvrir les Paramètres")
                            }
                            // Bouton pour rafraichir l'état après activation
                            TextButton(onClick = { isBtEnabled = isBluetoothEnabled(context); if(isBtEnabled) scannedDevices = getBondedDevices(context) }) {
                                Text("J'ai activé le Bluetooth, rafraîchir")
                            }
                        }
                    } else {
                        // Bluetooth ACTIF -> On affiche la liste
                        Text(
                            "Sélectionnez votre voiture dans la liste des appareils appairés :",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (scannedDevices.isEmpty()) {
                            Text("Aucun appareil trouvé. Vérifiez que votre voiture est bien appairée dans les réglages du téléphone.", fontSize = 14.sp)
                        } else {
                            LazyColumn(modifier = Modifier.height(200.dp)) {
                                items(scannedDevices) { device ->
                                    @SuppressLint("MissingPermission")
                                    val name = device.name ?: device.address
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onAddCar(device.address, name)
                                                showAddScreen = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Bluetooth, null)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(name, fontSize = 16.sp)
                                    }
                                    Divider()
                                }
                            }
                        }
                    }
                }

                // Chargement initial
                LaunchedEffect(Unit) {
                    isBtEnabled = isBluetoothEnabled(context)
                    if (isBtEnabled) {
                        scannedDevices = getBondedDevices(context)
                    }
                }

                // Réaction au retour de l'utilisateur (Lifecycle)
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            isBtEnabled = isBluetoothEnabled(context)
                            if (isBtEnabled) scannedDevices = getBondedDevices(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
            } else {
                // ÉCRAN LISTE "MON GARAGE"
                if (savedCars.isEmpty()) {
                    Text("Aucune voiture enregistrée.")
                } else {
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(savedCars) { car ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCarSelect(car) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isSelected = currentSelectedCar?.macAddress == car.macAddress
                                    Icon(Icons.Rounded.DirectionsCar, null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(car.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                                IconButton(onClick = { onDeleteCar(car) }) {
                                    Icon(Icons.Rounded.Delete, null)
                                }
                            }
                            Divider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!showAddScreen) {
                Button(onClick = { showAddScreen = true }) {
                    Text("Ajouter")
                }
            }
        },
        dismissButton = {
            Button(onClick = { if (showAddScreen) showAddScreen = false else onDismiss() }) {
                Text(if (showAddScreen) "Retour" else "Fermer")
            }
        }
    )
}

// Fonction utilitaire à ajouter juste en dessous (ou à la fin du fichier)
fun isBluetoothEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    return manager.adapter?.isEnabled == true
}

// You need to define this function, for example:
@SuppressLint("MissingPermission")
fun getBondedDevices(context: Context): List<BluetoothDevice> {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    return bluetoothManager.adapter?.bondedDevices?.toList() ?: emptyList()
}

// You also need to define the CarLocation data class, for example:
data class CarLocation(val macAddress: String, val name: String)


// ↓↓↓↓↓↓ FIX IS HERE: Renamed this function from GarageDialog to BluetoothPicker ↓↓↓↓↓↓
@SuppressLint("MissingPermission")
@Composable
fun BluetoothPicker(onDevicePicked: (String, String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter = bluetoothManager.adapter
    val pairedDevices = remember { adapter?.bondedDevices?.toList() ?: emptyList() }

    var selectedMac by remember { mutableStateOf<String?>(null) }
    var tempName by remember { mutableStateOf("") }

    if (selectedMac != null) {
        AlertDialog(
            onDismissRequest = { selectedMac = null },
            title = { Text("Nom de la voiture") },
            text = { OutlinedTextField(value = tempName, onValueChange = { tempName = it }, label = { Text("Nom (ex: Clio)") }) },
            confirmButton = {
                Button(onClick = { onDevicePicked(selectedMac!!, tempName.ifBlank { "Voiture" }) }) { Text("Sauvegarder") }
            }
        )
    }

    LazyColumn {
        items(pairedDevices) { device ->
            ListItem(
                headlineContent = { Text(device.name ?: "Inconnu", color = Color.White) },
                supportingContent = { Text(device.address, color = Color.Gray) },
                leadingContent = { Icon(Icons.Rounded.Bluetooth, null, tint = Color(0xFF2979FF)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    tempName = device.name ?: "Ma Voiture"
                    selectedMac = device.address
                }
            )
        }
    }
}
