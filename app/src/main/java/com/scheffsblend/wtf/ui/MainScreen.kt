package com.scheffsblend.wtf.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.appcompat.content.res.AppCompatResources
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.scheffsblend.wtf.MainActivity
import com.scheffsblend.wtf.R
import com.scheffsblend.wtf.data.Detection
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: DetectionViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val isScanning by viewModel.isScanning.collectAsState()
    val currentDetections by viewModel.currentDetections.collectAsState()
    val savedDetections by viewModel.savedDetections.collectAsState()
    val autoSave by viewModel.saveAutomatically.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val hasPermissions by viewModel.hasPermissions.collectAsState()

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
                    onClick = { if (hasPermissions) viewModel.toggleScanning() else {} },
                    containerColor = if (!hasPermissions) Color.Gray 
                                    else if (isScanning) colorResource(R.color.threat_color_high) 
                                    else MaterialTheme.colorScheme.primaryContainer
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
                0 -> {
                    if (!hasPermissions) {
                        PermissionMissingScreen()
                    } else {
                        DetectionListTab(
                            detections = currentDetections,
                            isScanning = isScanning,
                            autoSave = autoSave,
                            onAutoSaveToggle = viewModel::toggleAutoSave,
                            onSaveManual = viewModel::saveCurrentDetections,
                            onNavigateToMap = { detection ->
                                mapCenterOverride = GeoPoint(detection.latitude, detection.longitude)
                                selectedTab = 2
                            },
                            onRemoveItem = { viewModel.removeCurrentDetection(it) }
                        )
                    }
                }
                1 -> DetectionListTab(
                    detections = savedDetections,
                    onNavigateToMap = { detection ->
                        mapCenterOverride = GeoPoint(detection.latitude, detection.longitude)
                        selectedTab = 2
                    },
                    onRemoveItem = { viewModel.deleteSavedDetection(it) }
                )
                2 -> {
                    val allDetections = remember(currentDetections, savedDetections) {
                        (currentDetections + savedDetections).distinctBy { it.macAddress }
                    }
                    MapTab(allDetections, userLocation, mapCenterOverride)
                }
            }
        }
    }
}

@Composable
fun PermissionMissingScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Color.Red,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.permissions_missing_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.permissions_missing_msg),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { (context as? MainActivity)?.openAppSettings() }) {
            Text(stringResource(R.string.open_settings))
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
    onNavigateToMap: (Detection) -> Unit,
    onRemoveItem: (Detection) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(detections) {
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex <= 1) {
            listState.scrollToItem(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isScanning != null && autoSave != null && onAutoSaveToggle != null && onSaveManual != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                    Text(
                        text = stringResource(R.string.auto_save),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Switch(checked = autoSave, onCheckedChange = onAutoSaveToggle)
                }
            }
            
            if (autoSave == false && detections.isNotEmpty()) {
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
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(detections, key = { "${it.id}-${it.macAddress}" }) { detection ->
                DetectionItem(
                    detection = detection,
                    onNavigateToMap = { onNavigateToMap(detection) },
                    onRemove = { onRemoveItem(detection) }
                )
            }
        }
    }
}

@Composable
fun MapTab(detections: List<Detection>, userLocation: Pair<Double, Double>?, centerOverride: GeoPoint?) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val wifiPainter = rememberVectorPainter(Icons.Default.Wifi)
    val bluetoothPainter = rememberVectorPainter(Icons.Default.Bluetooth)
    
    var followUser by remember { mutableStateOf(centerOverride == null) }
    var lastProcessedOverride by remember { mutableStateOf<GeoPoint?>(null) }
    val currentCenterOverride by rememberUpdatedState(centerOverride)

    // Threat colors
    val highColor = colorResource(R.color.threat_color_high)
    val medColor = colorResource(R.color.threat_color_med)
    val lowColor = colorResource(R.color.threat_color_low)
    val unknownColor = Color.Gray

    val markerCache = remember(density, wifiPainter, bluetoothPainter) {
        mutableMapOf<String, Drawable>()
    }

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

                    // Detect user interaction to stop following/centering
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            followUser = false
                            // Also mark the current override as processed so it doesn't snap back
                            lastProcessedOverride = currentCenterOverride
                        }
                        false
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { mapView ->
                mapView.overlays.clear()
                
                userLocation?.let {
                    val userMarker = Marker(mapView)
                    userMarker.position = GeoPoint(it.first, it.second)
                    userMarker.title = context.getString(R.string.your_location)
                    userMarker.icon = AppCompatResources.getDrawable(context, org.osmdroid.library.R.drawable.person)
                    userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    mapView.overlays.add(userMarker)
                }

                detections.forEach { detection ->
                    val color = when (detection.threatLevel) {
                        3 -> highColor
                        2 -> medColor
                        1 -> lowColor
                        else -> unknownColor
                    }

                    // Add a semi-transparent circle around the marker to estimate distance based on RSSI
                    val circle = Polygon(mapView)
                    circle.points = Polygon.pointsAsCircle(
                        GeoPoint(detection.latitude, detection.longitude),
                        calculateRadiusFromRssi(detection.rssi)
                    )
                    circle.fillPaint.color = color.copy(alpha = 0.15f).toArgb()
                    circle.outlinePaint.color = color.copy(alpha = 0.4f).toArgb()
                    circle.outlinePaint.strokeWidth = 2f
                    mapView.overlays.add(circle)

                    val marker = Marker(mapView)
                    marker.position = GeoPoint(detection.latitude, detection.longitude)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.title = detection.name ?: context.getString(R.string.unknown_target)
                    
                    val threatLevelLabel = when (detection.threatLevel) {
                        3 -> context.getString(R.string.threat_high)
                        2 -> context.getString(R.string.threat_med)
                        1 -> context.getString(R.string.threat_low)
                        else -> context.getString(R.string.threat_unknown)
                    }
                    marker.subDescription = context.getString(R.string.threat_label, threatLevelLabel, detection.rssi)
                    
                    val isWifi = detection.type == "WiFi"
                    val painter = if (isWifi) wifiPainter else bluetoothPainter
                    val key = "${detection.type}_${detection.threatLevel}"
                    
                    marker.icon = markerCache.getOrPut(key) {
                        createCustomMarker(context, density, painter, color)
                    }
                    mapView.overlays.add(marker)

                    // Show info window if this marker matches the NEW centerOverride
                    if (centerOverride != null && centerOverride != lastProcessedOverride &&
                        detection.latitude == centerOverride.latitude &&
                        detection.longitude == centerOverride.longitude) {
                        marker.showInfoWindow()
                    }
                }

                // Positioning logic - only reposition if it's a NEW override
                if (centerOverride != null && centerOverride != lastProcessedOverride) {
                    mapView.controller.setCenter(centerOverride)
                    mapView.controller.setZoom(19.0)
                    lastProcessedOverride = centerOverride
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

private fun calculateRadiusFromRssi(rssi: Int): Double {
    // A very rough estimate of distance in meters based on RSSI
    // d = 10 ^ ((P0 - RSSI) / (10 * n))
    // Using P0 = -40 (RSSI at 1m) and n = 3.0 (path loss exponent)
    val p0 = -40.0
    val n = 3.0
    return Math.pow(10.0, (p0 - rssi) / (10.0 * n))
}

private fun createCustomMarker(
    context: Context,
    density: Density,
    painter: Painter,
    color: Color
): Drawable {
    val size = 80
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val androidCanvas = AndroidCanvas(bitmap)
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
    }

    // Draw background circle
    androidCanvas.drawCircle(size / 2f, size / 2f, size / 2.2f, paint)

    // Draw white border
    paint.style = Paint.Style.STROKE
    paint.color = android.graphics.Color.WHITE
    paint.strokeWidth = 4f
    androidCanvas.drawCircle(size / 2f, size / 2f, size / 2.2f, paint)

    // Draw Icon using CanvasDrawScope
    val drawScope = CanvasDrawScope()
    val iconSize = size * 0.5f
    val offset = (size - iconSize) / 2f
    
    drawScope.draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(androidCanvas),
        size = Size(size.toFloat(), size.toFloat())
    ) {
        translate(offset, offset) {
            with(painter) {
                draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(Color.White))
            }
        }
    }

    return BitmapDrawable(context.resources, bitmap)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionItem(
    detection: Detection,
    onNavigateToMap: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { 80.dp.toPx() }

    val threatLabel = when (detection.threatLevel) {
        3 -> stringResource(R.string.threat_high)
        2 -> stringResource(R.string.threat_med)
        1 -> stringResource(R.string.threat_low)
        else -> stringResource(R.string.threat_unknown)
    }
    val threatColor = when (detection.threatLevel) {
        3 -> colorResource(R.color.threat_color_high)
        2 -> colorResource(R.color.threat_color_med)
        1 -> colorResource(R.color.threat_color_low)
        else -> Color.Gray
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val dateString = dateFormat.format(Date(detection.timestamp))
    val dismissStateRef = remember { object { var state: SwipeToDismissBoxState? = null } }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val currentOffset = abs(dismissStateRef.state?.requireOffset() ?: 0f)
            if (currentOffset < maxOffsetPx * 0.95f) return@rememberSwipeToDismissBoxState false

            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onRemove()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    val shareText = context.getString(
                        R.string.share_text_template,
                        detection.type,
                        detection.name ?: context.getString(R.string.unknown_target),
                        detection.macAddress,
                        detection.latitude,
                        detection.longitude,
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
                    false // snap back after sharing
                }
                else -> false
            }
        },
        positionalThreshold = { _ -> maxOffsetPx * 0.9f }
    )

    dismissStateRef.state = dismissState

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection

            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> colorResource(R.color.share_color)
                SwipeToDismissBoxValue.EndToStart -> colorResource(R.color.threat_color_high)
                else -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Share
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                else -> null
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .background(color, CardDefaults.shape)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = Color.White)
                }
            }
        },
        content = {
            Box(Modifier.offset {
                val offset = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
                val delta = offset.coerceIn(-maxOffsetPx, maxOffsetPx) - offset
                IntOffset(delta.roundToInt(), 0)
            }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable { onNavigateToMap() },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Row 1: Name and Date
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            @Suppress("DEPRECATION")
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
                            verticalAlignment = Alignment.Bottom
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
                    }
                }
            }
        }
    )
}
