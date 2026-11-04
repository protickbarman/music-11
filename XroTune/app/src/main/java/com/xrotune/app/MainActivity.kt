package com.xrotune.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xrotune.app.auth.AuthManager
import com.xrotune.app.data.model.Track
import com.xrotune.app.player.PlaybackConnection
import com.xrotune.app.ui.player.NowPlayingBar
import com.xrotune.app.ui.player.PlayerScreen
import com.xrotune.app.ui.player.PlayerViewModel
import com.xrotune.app.ui.screens.AuthViewModel
import com.xrotune.app.ui.screens.HomeScreen
import com.xrotune.app.ui.screens.HomeViewModel
import com.xrotune.app.ui.screens.LibraryScreen
import com.xrotune.app.ui.screens.LibraryViewModel
import com.xrotune.app.ui.screens.SearchScreen
import com.xrotune.app.ui.screens.SearchViewModel
import com.xrotune.app.ui.screens.SignInScreen
import com.xrotune.app.ui.theme.XroTuneTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app: XroTuneApp get() = application as XroTuneApp

    private val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = app.repository
            return when {
                modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                    AuthViewModel(AuthManager(applicationContext), app.sessionStore, repo) as T
                modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                    HomeViewModel(repo) as T
                modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                    SearchViewModel(repo) as T
                modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
                    LibraryViewModel(repo) as T
                modelClass.isAssignableFrom(PlayerViewModel::class.java) ->
                    PlayerViewModel(repo) as T
                else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        setContent {
            XroTuneTheme {
                val authViewModel: AuthViewModel = viewModel(factory = factory)
                val authState by authViewModel.state.collectAsState()

                var guest by remember { mutableStateOf(false) }

                when {
                    authState.restoring -> com.xrotune.app.ui.components.LoadingBox()
                    authState.account == null && !guest -> SignInScreen(
                        signingIn = authState.signingIn,
                        error = authState.error,
                        onSignIn = authViewModel::signIn,
                        onSkip = { guest = true },
                    )
                    else -> MainScaffold(factory = factory)
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                    .launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun MainScaffold(
    factory: ViewModelProvider.Factory,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val playback = remember(context) { PlaybackConnection(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val playerViewModel: PlayerViewModel = viewModel(factory = factory)
    val playerState by playerViewModel.state.collectAsState()

    var currentTrack by remember { mutableStateOf<Track?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    DisposableEffect(playback) {
        playback.setListener(object : PlaybackConnection.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTrackEnded() {
                isPlaying = false
            }
        })
        onDispose {
            playback.setListener(null)
            playback.release()
        }
    }

    LaunchedEffect(Unit) {
        runCatching { playback.connect() }
    }

    LaunchedEffect(playerState.error) {
        playerState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    val tabs = listOf(
        TabItem("home", "Home", Icons.Filled.Home),
        TabItem("search", "Search", Icons.Filled.Search),
        TabItem("library", "Library", Icons.Filled.LibraryMusic),
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "home"

    // Poll the player for position/duration while the player screen is visible.
    LaunchedEffect(currentRoute, isPlaying) {
        if (currentRoute != "player") return@LaunchedEffect
        while (true) {
            positionMs = playback.currentPositionMs()
            durationMs = playback.durationMs()
            kotlinx.coroutines.delay(500)
        }
    }

    val onTrackClick: (Track) -> Unit = { track ->
        currentTrack = track
        scope.launch {
            runCatching { playback.connect() }
            playerViewModel.play(track) { media ->
                playback.play(media)
                isPlaying = true
            }
        }
        navController.navigate("player")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute != "player") {
                Column {
                    currentTrack?.let { track ->
                        NowPlayingBar(
                            title = track.title,
                            subtitle = track.subtitle,
                            artworkUrl = track.thumbnailUrl,
                            isPlaying = isPlaying,
                            onToggle = { playback.togglePlayPause() },
                            onExpand = { navController.navigate("player") },
                        )
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    if (currentRoute != tab.route) {
                                        navController.navigate(tab.route) {
                                            popUpTo("home") { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable("home") {
                val vm: HomeViewModel = viewModel(factory = factory)
                HomeScreen(vm, onTrackClick)
            }
            composable("search") {
                val vm: SearchViewModel = viewModel(factory = factory)
                SearchScreen(vm, onTrackClick)
            }
            composable("library") {
                val vm: LibraryViewModel = viewModel(factory = factory)
                LibraryScreen(vm, onTrackClick)
            }
            composable("player") {
                PlayerScreen(
                    title = currentTrack?.title,
                    artist = currentTrack?.subtitle,
                    artworkUrl = currentTrack?.thumbnailUrl,
                    isPlaying = isPlaying,
                    isResolving = playerState.isResolving,
                    error = playerState.error,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onBack = { navController.popBackStack() },
                    onToggle = { playback.togglePlayPause() },
                    onSeek = { playback.seekTo(it); positionMs = it },
                )
            }
        }
    }
}
