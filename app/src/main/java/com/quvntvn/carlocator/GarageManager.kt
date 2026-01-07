package com.quvntvn.carlocator

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.*

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
        containerColor = DarkerSurface,
        title = {
            Text(if (showAddScreen) "Ajouter une voiture" else "Mon Garage", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
                            Icon(Icons.Rounded.BluetoothDisabled, null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Le Bluetooth est désactivé.",
                                color = ErrorRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Veuillez l'activer pour voir vos appareils.",
                                color = TextGrey,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            // Bouton raccourci vers les paramètres Bluetooth
                            Button(
                                onClick = {
                                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceBlack)
                            ) {
                                Text("Ouvrir les Paramètres", color = TextWhite)
                            }
                            // Bouton pour rafraichir l'état après activation
                            TextButton(onClick = { isBtEnabled = isBluetoothEnabled(context); if(isBtEnabled) scannedDevices = getBondedDevices(context) }) {
                                Text("J'ai activé le Bluetooth, rafraîchir", color = NeonBlue)
                            }
                        }
                    } else {
                        // Bluetooth ACTIF -> On affiche la liste
                        Text(
                            "Sélectionnez votre voiture dans la liste des appareils appairés :",
                            color = TextGrey,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (scannedDevices.isEmpty()) {
                            Text("Aucun appareil trouvé. Vérifiez que votre voiture est bien appairée dans les réglages du téléphone.", color = TextGrey, fontSize = 14.sp)
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

                // Chargement initial
                LaunchedEffect(Unit) {
                    isBtEnabled = isBluetoothEnabled(context)
                    if (isBtEnabled) {
                        scannedDevices = getBondedDevices(context)
                    }
                }

                // Réaction au retour de l'utilisateur (Lifecycle)
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
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
                // ÉCRAN LISTE "MON GARAGE" (inchangé)
                if (savedCars.isEmpty()) {
                    Text("Aucune voiture enregistrée.", color = TextGrey)
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
                                    Icon(Icons.Rounded.DirectionsCar, null, tint = if (isSelected) NeonBlue else TextGrey)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(car.name, color = if (isSelected) NeonBlue else TextWhite, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                                IconButton(onClick = { onDeleteCar(car) }) {
                                    Icon(Icons.Rounded.Delete, null, tint = ErrorRed)
                                }
                            }
                            Divider(color = SurfaceBlack)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!showAddScreen) {
                Button(onClick = { showAddScreen = true }, colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)) {
                    Text("Ajouter", color = TextWhite)
                }
            }
        },
        dismissButton = {
            Button(onClick = { if (showAddScreen) showAddScreen = false else onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) {
                Text(if (showAddScreen) "Retour" else "Fermer", color = TextGrey)
            }
        }
    )
}

// Fonction utilitaire à ajouter juste en dessous (ou à la fin du fichier)
fun isBluetoothEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    return manager.adapter?.isEnabled == true
}

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

@Composable
fun SavedCarItem(car: CarLocation, isSelected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    // Couleur de fond : Gris clair si sélectionnée, gris foncé sinon
    val backgroundColor = if (isSelected) Color(0xFF2979FF).copy(alpha = 0.2f) else Color(0xFF2C2C2C)
    val borderColor = if (isSelected) Color(0xFF2979FF) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() } // <--- C'est ici que la magie opère !
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.DirectionsCar, null, tint = if (isSelected) Color(0xFF2979FF) else Color.White)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(car.name, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                text = if(car.latitude != null) "Garée le ${formatCarTimestamp(car.timestamp)}" else "Pas de position", // <-- Changed function call
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF5252))
        }
    }
}

fun formatCarTimestamp(timestamp: Long): String = SimpleDateFormat("dd MMM à HH:mm", Locale.getDefault()).format(Date(timestamp))