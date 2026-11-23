package com.magikvlc.app.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.magikvlc.app.diagnostics.DiagnosticLog
import com.magikvlc.app.player.MediaSource
import com.magikvlc.app.ui.about.AboutScreen
import com.magikvlc.app.ui.library.LibraryScreen
import com.magikvlc.app.ui.wall.MosaicScreen
import com.magikvlc.app.ui.wall.MosaicViewModel
import com.magikvlc.app.ui.wall.UrlDialog

object Routes {
    const val LIBRARY = "library"
    const val WALL = "wall"
    const val ABOUT = "about"
}

@Composable
fun MagikVLCAppNav(
    viewModel: MosaicViewModel,
    onEnterPip: (() -> Unit)? = null,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val fullscreenIndex by viewModel.fullscreenIndex.collectAsStateWithLifecycle()
    val chromeVisible by viewModel.chromeVisible.collectAsStateWithLifecycle()
    val pipMode by viewModel.pipMode.collectAsStateWithLifecycle()
    var showUrlDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentRoute) {
        DiagnosticLog.log("NAV", "route changed to $currentRoute")
        viewModel.setRoute(currentRoute)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.persistNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val immersive = currentRoute == Routes.WALL && fullscreenIndex != null
    val pendingReplaceSlot by viewModel.pendingReplaceSlot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
    val view = LocalView.current
    DisposableEffect(immersive) {
        val controller = (view.context as? Activity)?.window
            ?.let { WindowCompat.getInsetsController(it, view) }
        if (immersive) {
            // En tablet conservamos Atrás/Home/Recientes como VLC; solo se oculta
            // la barra de estado durante la reproducción fullscreen.
            controller?.show(WindowInsetsCompat.Type.navigationBars())
            controller?.hide(WindowInsetsCompat.Type.statusBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun openUrl(url: String) {
        showUrlDialog = false
        if (!viewModel.add(MediaSource.Network(url))) {
            toast("Muro lleno: quita un video primero")
            return
        }
        navController.navigateTo(Routes.WALL)
    }

    // El nav se oculta en muro inmersivo (fullscreen, chrome oculto o ventana PiP).
    val showNav = currentRoute != Routes.WALL ||
        (fullscreenIndex == null && chromeVisible && !pipMode)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (showNav) {
                    NavigationRail {
                        NavigationRailItem(
                            selected = currentRoute == Routes.LIBRARY,
                            onClick = { navController.navigateTo(Routes.LIBRARY) },
                            icon = { Icon(Icons.Filled.VideoLibrary, contentDescription = null) },
                            label = { Text("Biblioteca") },
                        )
                        NavigationRailItem(
                            selected = currentRoute == Routes.WALL,
                            onClick = { navController.navigateTo(Routes.WALL) },
                            icon = { Icon(Icons.Filled.GridView, contentDescription = null) },
                            label = { Text("Muro") },
                        )
                    }
                }
                NavContent(
                    viewModel = viewModel,
                    navController = navController,
                    onAddUrl = { showUrlDialog = true },
                    pendingReplaceSlot = pendingReplaceSlot,
                    onToast = ::toast,
                    onEnterPip = onEnterPip,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = {
                    if (showNav) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentRoute == Routes.LIBRARY,
                                onClick = { navController.navigateTo(Routes.LIBRARY) },
                                icon = { Icon(Icons.Filled.VideoLibrary, contentDescription = null) },
                                label = { Text("Biblioteca") },
                            )
                            NavigationBarItem(
                                selected = currentRoute == Routes.WALL,
                                onClick = { navController.navigateTo(Routes.WALL) },
                                icon = { Icon(Icons.Filled.GridView, contentDescription = null) },
                                label = { Text("Muro") },
                            )
                        }
                    }
                },
            ) { padding ->
                NavContent(
                    viewModel = viewModel,
                    navController = navController,
                    onAddUrl = { showUrlDialog = true },
                    pendingReplaceSlot = pendingReplaceSlot,
                    onToast = ::toast,
                    onEnterPip = onEnterPip,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    if (showUrlDialog) {
        UrlDialog(
            onConfirm = ::openUrl,
            onDismiss = { showUrlDialog = false },
        )
    }
}

@Composable
private fun NavContent(
    viewModel: MosaicViewModel,
    navController: NavHostController,
    onAddUrl: () -> Unit,
    pendingReplaceSlot: Int?,
    onToast: (String) -> Unit,
    onEnterPip: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    fun openSelectedVideos(items: List<com.magikvlc.app.data.VideoItem>, fillEmptySingle: Boolean) {
        if (items.isEmpty()) return
        val slot = viewModel.pendingReplaceSlot.value ?: pendingReplaceSlot
        DiagnosticLog.log(
            "LIBRARY",
            "selection count=${items.size} replaceSlot=${slot ?: "none"} fillEmpty=$fillEmptySingle",
        )
        if (slot != null) {
            if (items.size != 1) {
                onToast("Durante un reemplazo selecciona un solo video")
                return
            }
            val item = items.first()
            viewModel.replaceTile(slot, MediaSource.Local(item.uri), item.name)
            navController.navigateTo(Routes.WALL)
            return
        }

        val sources = items
            .take(9)
            .map { MediaSource.Local(it.uri) to it.name }
        if (items.size > 9) {
            onToast("El muro admite máximo 9 videos; se abrieron los primeros 9")
        }
        if (sources.size == 1) {
            val source = sources.first()
            if (fillEmptySingle && viewModel.hasActiveVideos() && viewModel.hasEmptySlot()) {
                viewModel.add(source.first, source.second)
            } else {
                viewModel.playSingle(source)
            }
        } else {
            viewModel.openInWall(sources)
        }
        navController.navigateTo(Routes.WALL)
    }

    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        modifier = modifier,
    ) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onPlaySingle = { item -> openSelectedVideos(listOf(item), fillEmptySingle = true) },
                onOpenWall = { items -> openSelectedVideos(items, fillEmptySingle = false) },
                onAddUrl = onAddUrl,
                onAbout = { navController.navigateTo(Routes.ABOUT) },
                replaceSlot = pendingReplaceSlot,
                onCancelReplace = {
                    viewModel.cancelReplace()
                    navController.navigateTo(Routes.WALL)
                },
                onToast = onToast,
            )
        }
        composable(Routes.WALL) {
            MosaicScreen(
                viewModel = viewModel,
                onOpenLibrary = { navController.returnToLibrary() },
                onAddUrl = onAddUrl,
                onEnterPip = onEnterPip,
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun NavHostController.navigateTo(route: String) {
    DiagnosticLog.log("NAV", "navigateTo route=$route from=${currentDestination?.route}")
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.returnToLibrary() {
    if (currentBackStackEntry?.destination?.route == Routes.LIBRARY) return
    val popped = popBackStack(Routes.LIBRARY, inclusive = false)
    DiagnosticLog.log("NAV", "returnToLibrary popBackStack=$popped")
    if (!popped) {
        navigateTo(Routes.LIBRARY)
    }
}
