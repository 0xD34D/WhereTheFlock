package com.scheffsblend.myapplication.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.scheffsblend.myapplication.R
import com.scheffsblend.myapplication.data.Detection
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: DetectionViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val isScanning by viewModel.isScanning.collectAsState()
    val currentDetections by viewModel.currentDetections.collectAsState()
    val savedDetections by viewModel.savedDetections.collectAsState()
    val autoSave by viewModel.saveAutomatically.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()

    var mapCenterOverride by remember { mutableStateOf<GeoPoint?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedTab == 0) {
                        viewModel.clearCurrentDetections()
                    } else if (selectedTab == 1) {
                        viewModel.clearSavedDetections()
                    }
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.delete_confirm_positive), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.delete_confirm_negative))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title)) },
                actions = {
                    if (selectedTab == 0 || selectedTab == 1) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear_saved))
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.scanner)) },
                    label = { Text(stringResource(R.string.scanner)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = stringResource(R.string.history)) },
                    label = { Text(stringResource(R.string.history)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { 
                        selectedTab = 2
                        mapCenterOverride = null // Reset override when manually switching
                    },
                    icon = { Icon(Icons.Default.Map, contentDescription = stringResource(R.string.map)) },
                    label = { Text(stringResource(R.string.map)) }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { viewModel.toggleScanning() },
                    containerColor = if (isScanning) Color.Red else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isScanning) stringResource(R.string.stop) else stringResource(R.string.start)
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> DetectionListTab(
                    detections = currentDetections,
                    isScanning = isScanning,
                    autoSave = autoSave,
                    onAutoSaveToggle = viewModel::toggleAutoSave,
                    onSaveManual = viewModel::saveCurrentDetections,
                    onNavigateToMap = { detection ->
                        mapCenterOverride = GeoPoint(detection.latitude, detection.longitude)
                        selectedTab = 2
                    }
                )
                1 -> DetectionListTab(
                    detections = savedDetections,
                    onNavigateToMap = { detection ->
                        mapCenterOverride = GeoPoint(detection.latitude, detection.longitude)
                        selectedTab = 2
                    }
                )
                2 -> MapTab(savedDetections, userLocation, mapCenterOverride)
            }
        }
    }
}

@Composable
fun DetectionListTab(
    detections: List<Detection>,
    isScanning: Boolean? = null,
    autoSave: Boolean? = null,
    onAutoSaveToggle: ((Boolean) -> Unit)? = null,
    onSaveManual: (() -> Unit)? = null,
    onNavigateToMap: (Detection) -> Unit
) {
    var expandedKey by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Sticky-top logic: Auto-scroll to top when detections change IF we are already at the top
    LaunchedEffect(detections) {
        // If we are at index 0 or 1 (likely pushed down by a new item) 
        // and not currently dragging, snap to top.
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex <= 1) {
            listState.scrollToItem(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isScanning != null && autoSave != null && onAutoSaveToggle != null && onSaveManual != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isScanning) stringResource(R.string.scanning) else stringResource(R.string.idle),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isScanning) Color.Green else Color.Gray
                    )
                    Text(
                        text = stringResource(R.string.targets_found, detections.size),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.auto_save))
                    Switch(checked = autoSave, onCheckedChange = onAutoSaveToggle)
                }
            }
            
            if (!autoSave && detections.isNotEmpty()) {
                Button(
                    onClick = onSaveManual,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save_current_results))
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().clickable { expandedKey = null }
        ) {
            items(detections, key = { "${it.id}-${it.macAddress}" }) { detection ->
                val itemKey = "${detection.id}-${detection.macAddress}"
                DetectionItem(
                    detection = detection,
                    isExpanded = expandedKey == itemKey,
                    onClick = {
                        expandedKey = if (expandedKey == itemKey) null else itemKey
                    },
                    onNavigateToMap = { onNavigateToMap(detection) }
                )
            }
        }
    }
}

@Composable
fun MapTab(detections: List<Detection>, userLocation: Pair<Double, Double>?, centerOverride: GeoPoint?) {
    val context = LocalContext.current
    var followUser by remember { mutableStateOf(centerOverride == null) }
    
    // Reset followUser if centerOverride is provided
    LaunchedEffect(centerOverride) {
        if (centerOverride != null) {
            followUser = false
        }
    }

    Configuration.getInstance().userAgentValue = context.packageName

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(18.0)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { mapView ->
                mapView.overlays.clear()
                
                userLocation?.let {
                    val userMarker = Marker(mapView)
                    userMarker.position = GeoPoint(it.first, it.second)
                    userMarker.title = context.getString(R.string.your_location)
                    userMarker.icon = context.getDrawable(org.osmdroid.library.R.drawable.person)
                    mapView.overlays.add(userMarker)
                }

                detections.forEach { detection ->
                    val marker = Marker(mapView)
                    marker.position = GeoPoint(detection.latitude, detection.longitude)
                    marker.title = detection.name ?: context.getString(R.string.unknown_target)
                    marker.subDescription = context.getString(R.string.threat_label, detection.threatLevel, detection.rssi)
                    mapView.overlays.add(marker)
                }

                if (centerOverride != null) {
                    mapView.controller.setCenter(centerOverride)
                    mapView.controller.setZoom(19.0)
                } else if (followUser && userLocation != null) {
                    mapView.controller.animateTo(GeoPoint(userLocation.first, userLocation.second))
                }
                
                mapView.invalidate()
            }
        )
        
        FloatingActionButton(
            onClick = { followUser = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = if (followUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.my_location))
        }
    }
}

@Composable
fun DetectionItem(
    detection: Detection,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val context = LocalContext.current
    val threatLabel = when (detection.threatLevel) {
        3 -> stringResource(R.string.threat_high)
        2 -> stringResource(R.string.threat_med)
        1 -> stringResource(R.string.threat_low)
        else -> stringResource(R.string.threat_unknown)
    }
    val threatColor = when (detection.threatLevel) {
        3 -> Color.Red
        2 -> Color(0xFFFFA500) // Orange
        1 -> Color.Yellow
        else -> Color.Gray
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val dateString = dateFormat.format(Date(detection.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: Name and Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = detection.name ?: stringResource(R.string.unknown_target),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: Content and Threat Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.mac_label, detection.macAddress),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                    if (detection.reason != null) {
                        Text(
                            text = stringResource(R.string.reason_label, detection.reason),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.rssi_label, detection.rssi),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.loc_label, detection.latitude, detection.longitude),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(threatColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (detection.type == "WiFi") Icons.Default.Wifi else Icons.Default.Bluetooth,
                            contentDescription = detection.type,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(threatLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onNavigateToMap) {
                        Icon(Icons.Default.Map, contentDescription = stringResource(R.string.view_on_map))
                    }
                    IconButton(onClick = {
                        val shareText = context.getString(
                            R.string.share_text_template,
                            detection.type,
                            detection.name ?: context.getString(R.string.unknown_target),
                            detection.macAddress,
                            detection.uuid ?: "N/A",
                            detection.latitude,
                            detection.longitude,
                            detection.reason ?: "N/A",
                            detection.threatLevel,
                            threatLabel,
                            dateString
                        )
                        
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_details))
                    }
                }
            }
        }
    }
}
