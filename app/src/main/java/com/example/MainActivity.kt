package com.example
 
import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
 
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Sembunyikan navigasi bawah Android (Bottom Navigation Bar) saat masuk aplikasi
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())

        // Pastikan akselerasi perangkat keras grafis (Hardware Acceleration) aktif untuk rendering video 60 FPS
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    WebLauncherApp(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
 
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebLauncherApp(modifier: Modifier = Modifier) {
    var rawInputUrl by remember { mutableStateOf("") }
    var formattedUrl by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(false) }
    
    // Webview navigation state
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    
    // Progress indicator states
    var webProgress by remember { mutableStateOf(0) }
    var isWebLoading by remember { mutableStateOf(false) }

    // Tab state matching High Density footer layout
    var currentTab by remember { mutableStateOf("home") } // "home", "history", "settings"

    // Configuration settings that actually update the WebView settings!
    var isJavaScriptEnabled by remember { mutableStateOf(true) }
    var isDomStorageEnabled by remember { mutableStateOf(true) }
    var isDesktopMode by remember { mutableStateOf(false) }

    // Custom View state for fullscreen video / landscape
    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null) }

    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("wvgm_prefs", android.content.Context.MODE_PRIVATE) }
    
    // Dynamic Connection history
    var historyList by remember { 
        mutableStateOf(
            sharedPrefs.getString("history", "192.168.1.50,dev-server.local,192.168.100.2,10.0.2.2")
                ?.split(",")
                ?.filter { it.isNotEmpty() }
                ?: listOf("192.168.1.50", "dev-server.local", "192.168.100.2", "10.0.2.2")
        )
    }

    // Koneksi Favorit (Bookmarked endpoints)
    var favoriteList by remember {
        mutableStateOf(
            sharedPrefs.getString("favorites", "192.168.1.50,dev-server.local")
                ?.split(",")
                ?.filter { it.isNotEmpty() }
                ?: listOf("192.168.1.50", "dev-server.local")
        )
    }

    // State sub-tab di tab riwayat
    var historySubTab by remember { mutableStateOf("all") } // "all" or "favorites"

    val updateHistoryList = { newList: List<String> ->
        historyList = newList
        sharedPrefs.edit().putString("history", newList.joinToString(",")).apply()
    }
    
    val updateFavoriteList = { newList: List<String> ->
        favoriteList = newList
        sharedPrefs.edit().putString("favorites", newList.joinToString(",")).apply()
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Sesuai syarat navigasi back: Tangani penekanan tombol back saat menonton video fullscreen atau terhubung
    if (customView != null) {
        BackHandler(enabled = true) {
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null
            val activity = context as? android.app.Activity
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    } else if (isConnected) {
        BackHandler(enabled = true) {
            if (webViewInstance?.canGoBack() == true) {
                webViewInstance?.goBack()
            } else {
                // Return to Input State
                isConnected = false
                webViewInstance = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!isConnected) {
            // HIGH DENSITY: Input State Screen
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Material 3 Top App Bar (As seen in High Density template header)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = {
                                Toast.makeText(context, "Navigasi Menu Utama", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu Utama",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "QuickConnect",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Avatar box "JD" on the right
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEADDFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "JD",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF21005D)
                        )
                    }
                }

                // 2. Main Content Container
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(30.dp))

                    // Brand/Icon Section
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "Gateway Icon Logo",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(24.dp))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title & Description
                    Text(
                        text = "WebView Gateway",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = "Masukkan alamat endpoint jaringan atau URL untuk memulai browsing dengan protokol HTTP/HTTPS lancar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    if (currentTab == "home") {
                        // TAB HOME: Input Fields & Brand
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // High Density input fields style: Outlined with floating style
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = rawInputUrl,
                                    onValueChange = { rawInputUrl = it },
                                    label = { Text("Network Endpoint") },
                                    placeholder = { Text("192.168.1.15 atau example.com") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = "Web Network Icon",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon = {
                                        if (rawInputUrl.isNotEmpty()) {
                                            IconButton(onClick = { rawInputUrl = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear Input"
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            if (rawInputUrl.trim().isNotEmpty()) {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                formattedUrl = formatUrlInput(rawInputUrl)
                                                // Simpan ke riwayat jika belum ada
                                                val trimmedInput = rawInputUrl.trim()
                                                val filteredList = historyList.filter { it != trimmedInput }
                                                updateHistoryList(listOf(trimmedInput) + filteredList.take(9))
                                                isConnected = true
                                            }
                                        }
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                                        cursorColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("network_endpoint_input")
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Action Button: High Density beautiful capsule button
                            Button(
                                onClick = {
                                    if (rawInputUrl.trim().isNotEmpty()) {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        formattedUrl = formatUrlInput(rawInputUrl)
                                        // Simpan ke riwayat jika belum ada
                                        val trimmedInput = rawInputUrl.trim()
                                        val filteredList = historyList.filter { it != trimmedInput }
                                        updateHistoryList(listOf(trimmedInput) + filteredList.take(9))
                                        isConnected = true
                                    } else {
                                        Toast.makeText(context, "Silakan masukkan IP atau URL terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(28.dp), // Fully rounded capsule
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("hubungkan_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = "Hubungkan",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Action Arrow",
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(30.dp))

                            // Quick Connections Section (Recent Connections)
                            Text(
                                text = "KONEKSI TERAKHIR",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Render connection tags dynamically
                            val displayHistory = historyList.take(2)
                            if (displayHistory.isEmpty()) {
                                Text(
                                    text = "Belum ada riwayat koneksi.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    displayHistory.forEachIndexed { index, address ->
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    rawInputUrl = address
                                                }
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Green dot for first item, slate for second
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (index == 0) Color(0xFF22C55E) else Color(0xFF94A3B8))
                                            )
                                            Text(
                                                text = address,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (displayHistory.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Favorite Connections Section
                            Text(
                                text = "KONEKSI FAVORIT",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            val displayFavorites = favoriteList.take(2)
                            if (displayFavorites.isEmpty()) {
                                Text(
                                    text = "Belum ada favorit. Berikan bintang/favorit di tab Riwayat untuk melihatnya di sini.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    displayFavorites.forEach { address ->
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    rawInputUrl = address
                                                }
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = "Favorit",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = address,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (displayFavorites.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else if (currentTab == "history") {
                        // TAB HISTORY: Full list of connections
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "Kelola Koneksi Jaringan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Sub-tabs chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = historySubTab == "all",
                                    onClick = { historySubTab = "all" },
                                    label = { Text("Semua Riwayat (${historyList.size})") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Semua Riwayat",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                                FilterChip(
                                    selected = historySubTab == "favorites",
                                    onClick = { historySubTab = "favorites" },
                                    label = { Text("Favorit (${favoriteList.size})") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = "Favorit",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (historySubTab == "all") {
                                // Sub-tab 1: All Connection History
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "RIWAYAT TERBARU",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    if (historyList.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                updateHistoryList(emptyList())
                                                Toast.makeText(context, "Semua riwayat dihapus", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Hapus Semua",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Hapus Semua",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (historyList.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Belum ada riwayat koneksi.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    historyList.forEach { host ->
                                        val isFav = host in favoriteList
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    rawInputUrl = host
                                                    currentTab = "home"
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Home,
                                                    contentDescription = "Host",
                                                    tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = host,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                // Favorite toggle button
                                                IconButton(
                                                    onClick = {
                                                        if (isFav) {
                                                            updateFavoriteList(favoriteList.filter { it != host })
                                                            Toast.makeText(context, "$host dihapus dari favorit", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            updateFavoriteList(favoriteList + host)
                                                            Toast.makeText(context, "$host ditambahkan ke favorit", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                        contentDescription = "Tandai Favorit",
                                                        tint = if (isFav) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                // Delete individual history button
                                                IconButton(
                                                    onClick = {
                                                        updateHistoryList(historyList.filter { it != host })
                                                        Toast.makeText(context, "Dihapus dari riwayat", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Hapus",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Sub-tab 2: Favorites Only
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "KONEKSI FAVORIT ANDA",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    if (favoriteList.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                updateFavoriteList(emptyList())
                                                Toast.makeText(context, "Semua favorit dihapus", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Hapus Semua Favorit",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Hapus Semua",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (favoriteList.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Belum ada koneksi favorit.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    favoriteList.forEach { favHost ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    rawInputUrl = favHost
                                                    currentTab = "home"
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Favorite,
                                                    contentDescription = "Favorit",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = favHost,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    updateFavoriteList(favoriteList.filter { it != favHost })
                                                    Toast.makeText(context, "$favHost dihapus dari favorit", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Favorite,
                                                    contentDescription = "Hapus Favorit",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (currentTab == "settings") {
                        // TAB SETTINGS: Dynamic configuration parameters for the WebView
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "Sistem Konfigurasi WebView",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Settings row 1: JS
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Aktifkan JavaScript (JS)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            Text(
                                                text = "Diperlukan untuk website dinamis modern.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = isJavaScriptEnabled,
                                            onCheckedChange = { isJavaScriptEnabled = it }
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Divider()
                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Settings row 2: DOM Storage
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Aktifkan DOM Storage",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            Text(
                                                text = "Mengizinkan penyimpanan cookie & cache lokal.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = isDomStorageEnabled,
                                            onCheckedChange = { isDomStorageEnabled = it }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    Divider()
                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Settings row 3: Desktop Mode
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Mode Desktop Mode",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            Text(
                                                text = "Menggunakan User-Agent browser komputer desktop.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = isDesktopMode,
                                            onCheckedChange = { isDesktopMode = it }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }

                // 3. M3 Bottom Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavTab(
                        label = "Home",
                        icon = Icons.Default.Home,
                        isSelected = currentTab == "home",
                        onClick = { currentTab = "home" }
                    )
                    BottomNavTab(
                        label = "History",
                        icon = Icons.Default.Info,
                        isSelected = currentTab == "history",
                        onClick = { currentTab = "history" }
                    )
                    BottomNavTab(
                        label = "Settings",
                        icon = Icons.Default.Settings,
                        isSelected = currentTab == "settings",
                        onClick = { currentTab = "settings" }
                    )
                }
            }
        } else {
            // HIGH DENSITY: Browser State (Full screen matched parent WebView)
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = isJavaScriptEnabled
                                domStorageEnabled = isDomStorageEnabled
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                
                                if (isDesktopMode) {
                                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    url?.let { view?.loadUrl(it) }
                                    return true
                                }
 
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isWebLoading = true
                                }
 
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isWebLoading = false
                                }
                            }
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    webProgress = newProgress
                                    isWebLoading = newProgress < 100
                                }

                                override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                                    super.onShowCustomView(view, callback)
                                    customView = view
                                    customViewCallback = callback
                                    
                                    // Sesuai syarat: beralih ke mode lanskap saat menonton video penuh (fullscreen)
                                    val activity = context as? android.app.Activity
                                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }

                                override fun onHideCustomView() {
                                    super.onHideCustomView()
                                    customView = null
                                    customViewCallback = null
                                    
                                    val activity = context as? android.app.Activity
                                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            }
                            webViewInstance = this
                            loadUrl(formattedUrl)
                        }
                    },
                    update = { view ->
                        // Re-assess settings on update safely to prevent reload lag during orientation change
                        if (view.settings.javaScriptEnabled != isJavaScriptEnabled) {
                            view.settings.javaScriptEnabled = isJavaScriptEnabled
                        }
                        if (view.settings.domStorageEnabled != isDomStorageEnabled) {
                            view.settings.domStorageEnabled = isDomStorageEnabled
                        }
                        val targetUserAgent = if (isDesktopMode) {
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        } else {
                            null
                        }
                        if (view.settings.userAgentString != targetUserAgent) {
                            view.settings.userAgentString = targetUserAgent
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
 
                // Progress Indicator at top of browser view
                AnimatedVisibility(
                    visible = isWebLoading,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    LinearProgressIndicator(
                        progress = { webProgress.toFloat() / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }
        }

        // Overlay tampilan penuh untuk video HTML5 (Custom View)
        if (customView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { customView!! },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun BottomNavTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
 
/**
 * Validasi dan format input IP / URL otomatis.
 * Jika pengguna hanya memasukkan IP (contoh: 192.168.1.15), otomatis tambahkan prefix "http://".
 */
fun formatUrlInput(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""
 
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
    }
 
    // Pola pengecekan IP address (e.g. 192.168.1.15 atau port 192.168.1.15:8080)
    val ipPattern = "^([0-9]{1,3}\\.){3}[0-9]{1,3}(:[0-9]+)?(.*)?$".toRegex()
    
    // Pola pengecekan domain atau host (e.g. google.com atau web-dev.local)
    val domainPattern = "^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$".toRegex()
 
    return if (ipPattern.matches(trimmed) || domainPattern.matches(trimmed) || trimmed.startsWith("localhost")) {
        "http://$trimmed"
    } else {
        "http://$trimmed"
    }
}
