package com.quvntvn.carlocator

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@SuppressLint("MissingPermission") // On gère la permission avant d'afficher
@Composable
fun BluetoothDeviceList(
    onDeviceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter = bluetoothManager.adapter

    // On récupère la liste des appareils déjà appairés
    val pairedDevices = remember {
        try {
            adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quelle est ta voiture ?") },
        text = {
            if (pairedDevices.isEmpty()) {
                Text("Aucun appareil Bluetooth trouvé. Vérifie que le Bluetooth est activé.")
            } else {
                LazyColumn {
                    items(pairedDevices) { device ->
                        ListItem(
                            headlineContent = { Text(device.name ?: "Appareil inconnu") },
                            supportingContent = { Text(device.address) },
                            leadingContent = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                            modifier = Modifier.clickable {
                                onDeviceSelected(device.address) // On renvoie l'ID de la voiture
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}