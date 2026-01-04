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
import androidx.compose.ui.window.Dialog

@Composable
fun GarageDialog(
    savedCars: List<CarLocation>,
    currentSelectedCar: CarLocation?, // Pour savoir laquelle surligner
    onAddCar: (String, String) -> Unit,
    onDeleteCar: (CarLocation) -> Unit,
    onCarSelect: (CarLocation) -> Unit, // <--- NOUVEAU : Action quand on clique
    onDismiss: () -> Unit
) {
    var showAddMode by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            contentColor = Color.White,
            modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 500.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (showAddMode) "Ajouter une voiture" else "Mon Garage",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (showAddMode) {
                    BluetoothPicker(
                        onDevicePicked = { mac, defaultName ->
                            onAddCar(mac, defaultName)
                            showAddMode = false
                        },
                        onCancel = { showAddMode = false }
                    )
                } else {
                    if (savedCars.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Aucune voiture. Ajoutes-en une !", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(savedCars) { car ->
                                SavedCarItem(
                                    car = car,
                                    isSelected = car.macAddress == currentSelectedCar?.macAddress, // On vérifie si c'est la voiture active
                                    onClick = { onCarSelect(car) },
                                    onDelete = { onDeleteCar(car) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showAddMode = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ajouter une voiture")
                    }
                }
            }
        }
    }
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
                text = if(car.latitude != null) "Garée le ${formatDate(car.timestamp)}" else "Pas de position",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF5252))
        }
    }
}