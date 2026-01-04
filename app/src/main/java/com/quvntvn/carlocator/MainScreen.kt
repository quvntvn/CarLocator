package com.quvntvn.carlocator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
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
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- COULEURS PERSONNALISÉES (Thème Tech/Néon) ---
val NeonBlue = Color(0xFF2979FF)
val DeepBlack = Color(0xFF121212)
val SurfaceBlack = Color(0xFF1E1E1E)
val TextWhite = Color(0xFFEEEEEE)
val TextGrey = Color(0xFFAAAAAA)

@Composable
fun MainScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PrefsManager(context) }

    // États
    var showBluetoothDialog by remember { mutableStateOf(false) }
    val carLocationState by db.carDao().getCarLocation().collectAsState(initial = null)

    // Caméra de la carte
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(48.8566, 2.3522), 15f)
    }

    // --- Permissions (Le code robuste d'avant) ---
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
        permissionLauncher.launch(perms.toTypedArray())
    }

    // Centrer sur la voiture au démarrage
    LaunchedEffect(carLocationState) {
        carLocationState?.let { loc ->
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16f))
        }
    }

    // --- UI PRINCIPALE ---
    Box(modifier = Modifier.fillMaxSize().background(DeepBlack)) {

        // 1. LA CARTE (Plein écran fond)
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasPermission,
                // Le paramètre isZoomControlsEnabled a également été déplacé
                // Il est maintenant redondant car déjà dans uiSettings
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                mapToolbarEnabled = false // <-- PARAMÈTRE CORRIGÉ ET AU BON ENDROIT
            )
        ) {

            carLocationState?.let { loc ->
                Marker(
                    state = MarkerState(position = LatLng(loc.latitude, loc.longitude)),
                    title = "Ma Voiture",
                    snippet = "Garée le ${formatDate(loc.timestamp)}"
                )
            }
        }

        // 2. BARRE D'ÉTAT FLOTTANTE (Haut)
        TopStatusCard(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp, start = 16.dp, end = 16.dp),
            isCarSaved = prefs.getCarDeviceId() != null,
            onSettingsClick = { showBluetoothDialog = true }
        )

        // 3. PANNEAU DE CONTRÔLE (Bas)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Bouton de recentrage (flottant au dessus du panneau)
            SmallFloatingButton(
                icon = Icons.Rounded.MyLocation,
                onClick = {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        // On essaie de recentrer sur l'utilisateur (action par défaut du bouton myLocation Google)
                        // Ici on met juste une action simple pour l'UI
                        Toast.makeText(context, "Recentrage...", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.align(Alignment.End).padding(bottom = 16.dp)
            )

            // Carte d'info principale
            CarInfoCard(
                carLocation = carLocationState,
                onParkClick = { saveCurrentLocation(context, db, scope) },
                onFindClick = {
                    scope.launch {
                        carLocationState?.let {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 17f))
                        }
                    }
                }
            )
        }

        // 4. DIALOGUE BLUETOOTH (Si ouvert)
        if (showBluetoothDialog) {
            BluetoothDeviceList(
                onDeviceSelected = { mac ->
                    prefs.saveCarDeviceId(mac)
                    showBluetoothDialog = false
                    Toast.makeText(context, "Voiture liée !", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showBluetoothDialog = false }
            )
        }
    }
}

// --- COMPOSANTS UI CUSTOM ---

@Composable
fun TopStatusCard(modifier: Modifier = Modifier, isCarSaved: Boolean, onSettingsClick: () -> Unit) {
    Surface(
        modifier = modifier.shadow(8.dp, CircleShape).clip(CircleShape),
        color = SurfaceBlack.copy(alpha = 0.9f),
        contentColor = TextWhite
    ) {
        Row(
            modifier = Modifier
                .clickable { onSettingsClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicateur (Point vert ou rouge)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isCarSaved) Color(0xFF00E676) else Color.Red)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isCarSaved) "Bluetooth Monitor" else "Configuration requise",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGrey
                )
                Text(
                    text = if (isCarSaved) "Surveillance active" else "Sélectionner voiture",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextWhite)
        }
    }
}

@Composable
fun CarInfoCard(
    carLocation: CarLocation?,
    onParkClick: () -> Unit,
    onFindClick: () -> Unit
) {
    Surface(
        modifier = Modifier.shadow(16.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = SurfaceBlack
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Titre
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DirectionsCar, null, tint = NeonBlue, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Ma Voiture", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info Adresse / Date
            if (carLocation != null) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Place, null, tint = TextGrey, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Position enregistrée", color = TextGrey, fontSize = 12.sp)
                        Text(formatDate(carLocation.timestamp), color = TextWhite, fontSize = 16.sp)
                    }
                }
            } else {
                Text("Aucune position enregistrée.", color = TextGrey, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Boutons d'action
            Row(modifier = Modifier.fillMaxWidth()) {
                // Bouton PARKER (Gauche)
                Button(
                    onClick = onParkClick,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceBlack),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TextGrey.copy(alpha = 0.3f))
                ) {
                    Text("📍 Garer ici", color = TextWhite)
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Bouton TROUVER (Droit - Coloré)
                Button(
                    onClick = onFindClick,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                    shape = RoundedCornerShape(16.dp),
                    enabled = carLocation != null
                ) {
                    Icon(Icons.Rounded.Navigation, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Trouver")
                }
            }
        }
    }
}

@Composable
fun SmallFloatingButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        containerColor = SurfaceBlack,
        contentColor = TextWhite,
        shape = CircleShape
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

// Fonction utilitaire pour la date
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM à HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}