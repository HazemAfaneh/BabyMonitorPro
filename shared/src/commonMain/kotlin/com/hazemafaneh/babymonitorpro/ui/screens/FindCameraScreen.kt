package com.hazemafaneh.babymonitorpro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.ui.components.PairingCelebration
import com.hazemafaneh.babymonitorpro.ui.components.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.hazemafaneh.babymonitorpro.ui.components.initialFocus
import com.hazemafaneh.babymonitorpro.ui.components.pressable
import com.hazemafaneh.babymonitorpro.ui.theme.Tint
import com.hazemafaneh.babymonitorpro.ui.components.CARD_BORDER
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.BorderStroke
import com.hazemafaneh.babymonitorpro.core.Bmp
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.PairingUri
import com.hazemafaneh.babymonitorpro.core.isLinkLocalIpv4
import com.hazemafaneh.babymonitorpro.client.CameraProbe
import com.hazemafaneh.babymonitorpro.discovery.Lan
import com.hazemafaneh.babymonitorpro.discovery.Tailnet
import com.hazemafaneh.babymonitorpro.discovery.allLocalIpv4Addresses
import com.hazemafaneh.babymonitorpro.discovery.createBrowser
import com.hazemafaneh.babymonitorpro.discovery.ownLanAddresses
import com.hazemafaneh.babymonitorpro.store.AppSettings
import com.hazemafaneh.babymonitorpro.ui.components.Chevron
import com.hazemafaneh.babymonitorpro.ui.components.PrivacyLine
import com.hazemafaneh.babymonitorpro.ui.components.SectionLabel
import com.hazemafaneh.babymonitorpro.ui.layout.gutter
import com.hazemafaneh.babymonitorpro.ui.layout.rememberWindowClass
import com.hazemafaneh.babymonitorpro.ui.scan.QrScanner
import com.hazemafaneh.babymonitorpro.ui.scan.qrScanningSupported
import androidx.compose.ui.graphics.vector.ImageVector
import com.hazemafaneh.babymonitorpro.ui.components.IconPlate
import com.hazemafaneh.babymonitorpro.ui.components.PlateSize
import com.hazemafaneh.babymonitorpro.ui.icons.BmpIcons
import com.hazemafaneh.babymonitorpro.ui.theme.BmpTheme
import com.hazemafaneh.babymonitorpro.ui.theme.Space
import com.hazemafaneh.babymonitorpro.ui.theme.Touch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.koinInject

/**
 * Three routes to one camera, ranked rather than stacked.
 *
 * Discovery owns the top card because it is the route that works with no effort; QR and
 * manual entry sit below it as two small equals. Previously all three had equal weight, so
 * the fallback competed for attention with the good path — and on web, where discovery
 * cannot work at all, the only route that *does* work was the one buried at the bottom.
 */
@Composable
fun FindCameraScreen(
    onConnect: (CameraEndpoint) -> Unit,
    onBack: () -> Unit,
) {
    val settings = koinInject<AppSettings>()
    val window = rememberWindowClass()

    var manualHost by remember { mutableStateOf(settings.lastManualHost) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scannerDenied by remember { mutableStateOf(false) }
    var manualOpen by remember { mutableStateOf(false) }

    // Held between the scan landing and the live view opening, so the celebration has
    // something to name. Null every other moment.
    var paired by remember { mutableStateOf<CameraEndpoint?>(null) }

    // Bumped by the Refresh button. Everything keyed on it — the browser itself and the
    // effect that runs it — is torn down and rebuilt, which is what "search again" has to
    // mean: mDNS answers are cached, and a browser that has already decided the network is
    // empty will happily go on saying so.
    var discoveryRound by remember { mutableStateOf(0) }
    val browser = remember(discoveryRound) { createBrowser() }

    // Pressing Search again used to look broken, and for a good reason: the round tears the
    // browser down and builds a new one, so the results went to nothing and the card fell
    // back to "Still looking around" before the same cameras reappeared a second later. The
    // button read as the thing that had lost them. Two states fix it — a window during which
    // the card says it is searching, and the previous results held on screen for the length
    // of that window rather than blinking out and back.
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(discoveryRound) {
        if (discoveryRound == 0) return@LaunchedEffect
        searching = true
        delay(SEARCH_FEEDBACK_MILLIS)
        searching = false
    }

    // Tailscale, and any other VPN that carries no multicast.
    //
    // mDNS cannot reach a tailnet at all — it is a mesh of point-to-point tunnels, and
    // multicast has nowhere to go — so a camera reachable over Tailscale can never appear in
    // the browser's results however well it is working. It is found by asking instead: a
    // bounded probe of the neighbouring tailnet addresses on this app's own port. It runs
    // only when this device actually holds a 100.64.0.0/10 address, so a phone with no VPN
    // never sends a packet it did not before.
    // Found by asking rather than by listening. mDNS stays the first route — it is instant
    // where it works and costs one multicast query — but it is not dependable enough to be
    // the only one, so the same streaming probe the tailnet uses runs over the LAN too and
    // the two sets of results are merged.
    var probedLocal by remember(discoveryRound) { mutableStateOf<List<CameraEndpoint>>(emptyList()) }
    var localProbing by remember(discoveryRound) { mutableStateOf(false) }

    var tailnetCameras by remember(discoveryRound) { mutableStateOf<List<CameraEndpoint>>(emptyList()) }
    var tailnetScanning by remember(discoveryRound) { mutableStateOf(false) }
    val ownAddresses = remember(discoveryRound) { allLocalIpv4Addresses() }
    val onTailnet = remember(ownAddresses) { ownAddresses.any(Tailnet::isTailnetAddress) }

    LaunchedEffect(discoveryRound) {
        val candidates = Lan.candidates(
            ownAddresses = ownAddresses,
            // Where a camera has actually been reached on this network before.
            knownHosts = settings.recentCameras.map { it.host },
        )
        if (candidates.isEmpty()) return@LaunchedEffect
        localProbing = true
        val probe = CameraProbe()
        try {
            probedLocal = probe.probe(
                hosts = candidates,
                ports = listOf(settings.port, Bmp.DEFAULT_PORT, Bmp.LEGACY_PORT),
                onFound = { found ->
                    if (probedLocal.none { it.host == found.host }) {
                        probedLocal = probedLocal + found
                    }
                },
            )
        } finally {
            probe.close()
            localProbing = false
        }
    }

    LaunchedEffect(discoveryRound, onTailnet) {
        if (!onTailnet) return@LaunchedEffect
        val candidates = Tailnet.candidates(
            ownAddresses = ownAddresses,
            // Where a camera has actually been reached before. Worth more than any guess
            // about how Tailscale allocates.
            knownHosts = settings.recentCameras.map { it.host },
        )
        if (candidates.isEmpty()) return@LaunchedEffect
        tailnetScanning = true
        val probe = CameraProbe()
        val ports = listOf(settings.port, Bmp.DEFAULT_PORT, Bmp.LEGACY_PORT)
        val record: (CameraEndpoint) -> Unit = { found ->
            if (tailnetCameras.none { it.host == found.host }) {
                tailnetCameras = tailnetCameras + found
            }
        }
        try {
            // The hosts that have answered before, on their own and with room to resolve a
            // name and bring a tunnel up. A few seconds spent here is why the camera appears
            // before the sweep rather than after it.
            probe.probe(
                hosts = Tailnet.known(settings.recentCameras.map { it.host }),
                ports = ports,
                timeoutMillis = probe.knownHostTimeoutMillis,
                onFound = record,
            )
            // The port the camera settings on *this* device use, because a household that
            // moved the port moved it everywhere.
            // Then the blocks. This device's port first, then the default, then the port
            // older builds used — a household mid-upgrade has one of each.
            probe.probe(
                hosts = candidates,
                ports = ports,
                // Shown the moment one answers rather than when the sweep ends. The rest of
                // the list is several hundred addresses nobody lives at, each costing its full
                // deadline, so returning only at the end left a camera that was up, reachable
                // and answering sitting behind a minute and a half of other people's silence.
                onFound = record,
            )
        } finally {
            probe.close()
            tailnetScanning = false
        }
    }
    val announced by remember(browser) {
        browser?.cameras ?: MutableStateFlow(emptyList<CameraEndpoint>())
    }.collectAsState()

    // What is left after this device stops offering itself.
    //
    // Two things get dropped. A device in the camera role hears its own mDNS advertisement,
    // so the list handed the parent their own phone to go and watch — the one device that
    // cannot be the answer. And a link-local 169.254 host is one nothing on the WiFi can
    // reach, so offering it only produces a connection that refuses, with the camera taking
    // the blame; the pairing card already withholds those for the same reason.
    //
    // Recomputed when the announcements change rather than on every recomposition:
    // enumerating interfaces is a syscall, and a new list is the only thing that can change
    // the answer.
    val discovered = remember(announced, probedLocal) {
        val own = ownLanAddresses()
        // Announced first, because an announcement carries the name the camera chose for
        // itself; a probe only learns that name by asking, and asks later.
        (announced + probedLocal)
            .filterNot { it.host in own || isLinkLocalIpv4(it.host) }
            .distinctBy { it.host }
    }

    // Survives one round, and only while that round is running: a camera that has genuinely
    // gone off the air disappears from the list as soon as the sweep is over, which is the
    // one thing this card must not lie about.
    var lastResults by remember { mutableStateOf<List<CameraEndpoint>>(emptyList()) }
    LaunchedEffect(discovered) {
        if (discovered.isNotEmpty()) lastResults = discovered
    }
    val onScreen = if (discovered.isEmpty() && searching) lastResults else discovered

    DisposableEffect(browser) {
        browser?.start()
        onDispose { browser?.stop() }
    }

    val justPaired = paired
    if (justPaired != null) {
        PairingCelebration(
            cameraName = justPaired.name,
            onDone = { onConnect(justPaired) },
        )
        return
    }

    if (scanning) {
        ScanOverlay(
            denied = scannerDenied,
            onCancel = { scanning = false },
            onTypeInstead = {
                scanning = false
                manualOpen = true
            },
            onUnavailable = { scannerDenied = true },
            onCode = { raw ->
                val parsed = PairingUri.parse(raw) ?: return@ScanOverlay
                scanning = false
                // The celebration navigates when it is finished. Only the scanned route
                // gets it — typing an address in is not a moment, and a row tap is a
                // choice the parent already knew the answer to.
                paired = CameraEndpoint(
                    name = parsed.host,
                    host = parsed.host,
                    port = parsed.port,
                    source = CameraEndpoint.Source.QR,
                )
            },
        )
        return
    }

    // Where discovery is impossible the manual field is not a consolation prize, it is the
    // only door — so it opens as the primary control rather than behind a disclosure.
    val discoveryImpossible = browser == null
    val showManual = manualOpen || discoveryImpossible

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            // Room for the keyboard.
            //
            // Without this the IME simply covers the bottom of the screen — and the bottom of
            // this screen is the Connect button, directly under the field being typed into.
            // The parent types an address and then cannot reach the one control that uses it.
            // It is worst on a television, where the leanback keyboard is enormous, but it
            // happens on a phone in landscape too.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = window.gutter()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = CONTENT_MAX_WIDTH)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Space.xxs, bottom = Space.sm)
                    .heightIn(min = Touch.min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The moon is the viewer role's mark, the same one the role picker's second
                // card carries — so arriving here confirms which half of the app you chose.
                IconPlate(
                    icon = BmpIcons.Moon,
                    fill = BmpTheme.tints.grape.fill,
                    contentColor = BmpTheme.tints.grape.glyph,
                    size = PlateSize.small,
                )
                Spacer(Modifier.width(Space.xs))
                Text(
                    text = "Find your camera",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = TITLE_TEXT),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary,
                    ),
                ) {
                    Text(
                        text = "Back",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = ACTION_TEXT),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Discovery owns the top card at full width. It is the route that costs the
            // parent nothing, and the two below it are the ways out when it fails.
            DiscoveryCard(
                meta = when {
                    discoveryImpossible -> "not available here"
                    searching || localProbing -> "searching…"
                    onScreen.isEmpty() -> "mDNS · local only"
                    onScreen.size == 1 -> "1 found"
                    else -> "${onScreen.size} found"
                },
                searching = searching || localProbing,
                // Where the remote lands on this screen. Only this card claims it: two
                // controls both asking for initial focus is a race, and the second card is
                // not even present on most devices.
                focusFirst = true,
                // Nothing to search again with in a browser, so the control is simply absent
                // there rather than present and dead.
                onRefresh = if (discoveryImpossible) null else {
                    { discoveryRound++ }
                },
            ) {
                when {
                    // No plate on this one. It is a dead end rather than a wait, and the
                    // baby icons stay off anything that has gone wrong — a friendly mark
                    // over a sentence saying the thing cannot work reads as a shrug.
                    discoveryImpossible -> EmptyNote(
                        title = "This browser cannot search your network",
                        note = "Browsers have no way to look for devices on your WiFi. " +
                            "Type the address the camera screen shows.",
                    )

                    onScreen.isEmpty() -> EmptyNote(
                        icon = BmpIcons.House,
                        title = if (searching || localProbing) {
                            "Searching your network"
                        } else {
                            "Still looking around"
                        },
                        note = "Open BabyMonitor Pro on the phone you're leaving in the " +
                            "nursery and tap Use this device as Camera.",
                    )

                    else -> Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                        onScreen.forEachIndexed { index, camera ->
                            // The row is the action. One result should be one obvious tap,
                            // not an address to read and a Connect button to find.
                            DiscoveredRow(
                                camera = camera,
                                // Only the first is tinted. With every row lemon the list
                                // read as a set of buttons of equal weight; with one tinted
                                // the eye lands on the camera most likely to be the answer
                                // and can still see the others are the same kind of thing.
                                highlighted = index == 0,
                                onClick = { onConnect(camera) },
                            )
                        }
                    }
                }
            }

            if (onTailnet) {
                Spacer(Modifier.height(Space.sm))
                DiscoveryCard(
                    meta = when {
                        // The count leads even while the sweep runs: something found is the
                        // answer to the parent's question, and "scanning…" over a list of
                        // cameras reads as though the list is not to be trusted yet.
                        tailnetCameras.size == 1 -> "1 found"
                        tailnetCameras.size > 1 -> "${tailnetCameras.size} found"
                        tailnetScanning -> "scanning…"
                        else -> "tailnet · none found"
                    },
                    title = "Over Tailscale",
                    searching = tailnetScanning,
                    onRefresh = { discoveryRound++ },
                ) {
                    when {
                        // Results first, even mid-sweep. The rows arrive as they answer.
                        tailnetCameras.isEmpty() && tailnetScanning -> EmptyNote(
                            title = "Asking your tailnet",
                            // Honest about the shape of it: a camera that is there answers
                            // almost at once and appears the moment it does, while the rest
                            // of the sweep goes on knocking on empty addresses for a while.
                            note = "A camera that is on shows up here within a second or two. " +
                                "The sweep keeps looking for a minute after that.",
                        )

                        tailnetCameras.isEmpty() -> EmptyNote(
                            icon = BmpIcons.House,
                            title = "Nothing answered",
                            note = "The nursery device has to be on the same tailnet and " +
                                "broadcasting. If it is, open it once by address and it will " +
                                "be waiting under Recently connected next time.",
                        )

                        else -> Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                            tailnetCameras.forEachIndexed { index, camera ->
                                DiscoveredRow(
                                    camera = camera,
                                    highlighted = index == 0,
                                    onClick = { onConnect(camera) },
                                )
                            }
                        }
                    }
                }
            }

            // Everything mDNS cannot reach, which is not an edge case: a tailnet carries no
            // multicast at all, so a camera reachable over Tailscale can never be discovered,
            // however well the network is working. This list is how that camera gets opened
            // — one tap, with the name it announced rather than the address it lives at.
            val recents = remember { settings.recentCameras }
            if (recents.isNotEmpty()) {
                Spacer(Modifier.height(Space.sm))
                DiscoveryCard(
                    meta = if (recents.size == 1) "1 camera" else "${recents.size} cameras",
                    title = "Recently connected",
                    // Nothing to refresh: this list is memory, not a search.
                    onRefresh = null,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                        for (camera in recents) {
                            DiscoveredRow(
                                camera = CameraEndpoint(
                                    name = camera.name,
                                    host = camera.host,
                                    port = camera.port,
                                    source = CameraEndpoint.Source.MANUAL,
                                ),
                                highlighted = false,
                                onClick = {
                                    onConnect(
                                        CameraEndpoint(
                                            name = camera.name,
                                            host = camera.host,
                                            port = camera.port,
                                            source = CameraEndpoint.Source.MANUAL,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.sm))

            if (showManual) {
                ManualCard(
                    title = if (discovered.isEmpty()) "Type the address" else "Or type the address",
                    host = manualHost,
                    error = error,
                    onHostChange = {
                        manualHost = it
                        error = null
                    },
                    onConnect = {
                        val parsed = parseAddress(manualHost)
                        if (parsed == null) {
                            // Show the shape wanted. "That does not look like an address"
                            // tells a tired parent nothing they can act on.
                            error = "That is not a full address. It should look like " +
                                "192.168.1.42, a name like nursery.tailnet.ts.net, or either with :port."
                        } else {
                            settings.lastManualHost = manualHost
                            onConnect(
                                CameraEndpoint(
                                    name = parsed.host,
                                    host = parsed.host,
                                    port = parsed.port,
                                    source = CameraEndpoint.Source.MANUAL,
                                ),
                            )
                        }
                    },
                )
            } else {
                // Two small equals, side by side. Stacked full-width they read as a ranking,
                // and neither of them outranks the other: which one is faster depends on
                // whether the parent is holding both devices.
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    if (qrScanningSupported) {
                        RouteCard(
                            icon = BmpIcons.Camera,
                            tint = BmpTheme.tints.sky,
                            title = "Scan the code",
                            note = "Fastest, if both phones are in the room",
                            onClick = {
                                scannerDenied = false
                                scanning = true
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    RouteCard(
                        // The footprint, same as the card this address is printed on. The
                        // two are the ends of one act: the camera screen shows the address,
                        // this is where it gets typed back in.
                        icon = BmpIcons.Footprint,
                        tint = null,
                        title = "Type the address",
                        note = "It's on the camera screen",
                        onClick = { manualOpen = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(Space.md))
            PrivacyFooter(
                text = when {
                    discoveryImpossible ->
                        "Nothing about this connection leaves your network — the browser " +
                            "talks straight to the camera."

                    discovered.isEmpty() ->
                        "We only search your own WiFi. Nothing on the internet is " +
                            "contacted, and nothing out there can find this camera."

                    else ->
                        "Everything you see here is on your WiFi. The row tells you which " +
                            "device it is before you tap it."
                },
            )
            Spacer(Modifier.height(Space.xl))
        }
    }
}

/** The route that costs nothing, and how it is going. */
@Composable
private fun DiscoveryCard(
    meta: String,
    onRefresh: (() -> Unit)?,
    title: String = "On this network",
    searching: Boolean = false,
    focusFirst: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(CARD_BORDER, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Space.lg)) {
            // The label above its meta, not beside it.
            //
            // All three sat on one line with `SpaceBetween` and no minimum gap, and on a
            // phone there is not room: "ON THIS NETWORK" ran straight into "mDNS · local
            // only" with no space between them, so the two read as one broken word. Stacking
            // them costs one line of a card that has plenty and cannot collide at any width.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    SectionLabel(title)
                    // How the search works, stated rather than implied. A parent who wonders
                    // whether this app is scanning the internet gets the answer on the card
                    // doing the scanning.
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = META_TEXT,
                        ),
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                    )
                }
                if (onRefresh != null) {
                    Spacer(Modifier.width(Space.sm))
                    RefreshButton(
                        onClick = onRefresh,
                        searching = searching,
                        focusFirst = focusFirst,
                    )
                }
            }
            Spacer(Modifier.height(Space.sm))
            content()
        }
    }
}

/**
 * Search the network again.
 *
 * A labelled pill rather than a bare circular-arrow icon, and that is a television decision
 * as much as an accessibility one: a 24dp glyph in a card corner is both unreadable from a
 * sofa and a target the D-pad has to find. This one carries the word, stands 48dp tall, and
 * takes the same focus ring every other control on the screen has — so the remote can see it
 * coming and land on it.
 *
 * It is also the control a television needs most. A TV is usually switched on long after the
 * nursery phone was set up, so its first discovery sweep can easily be the one that missed —
 * and there is no pull-to-refresh on a device with no touchscreen.
 */
@Composable
private fun RefreshButton(
    onClick: () -> Unit,
    searching: Boolean,
    focusFirst: Boolean,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = Touch.min)
            // Where the remote starts on this screen. It is at the top of the card that
            // matters, and one press down from it is the list of cameras — so the first
            // thing the D-pad does is either open a camera or search again, which are the
            // only two things this screen is for.
            .then(if (focusFirst) Modifier.initialFocus() else Modifier)
            .pressable(onClick = onClick, focusShape = MaterialTheme.shapes.extraLarge),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(CARD_BORDER, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // Never wrapped. Two words in a pill broke across two lines on a phone and
                // turned a control into a lozenge twice the height of everything beside it.
                text = if (searching) "Searching…" else "Search again",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = ACTION_TEXT),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Nothing found yet, or nothing findable here.
 *
 * Centred and given a plate, because an empty card with a line of grey text in the corner
 * reads as a thing that failed rather than a thing still working.
 */
@Composable
private fun EmptyNote(title: String, note: String, icon: ImageVector? = null) {
    Column(
        Modifier.fillMaxWidth().padding(top = Space.sm, bottom = Space.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            IconPlate(
                icon = icon,
                fill = BmpTheme.tints.lemon.fill,
                contentColor = BmpTheme.tints.lemon.glyph,
                size = PlateSize.hero,
            )
            Spacer(Modifier.height(Space.sm))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = EMPTY_TITLE),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.xxs))
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = EMPTY_NOTE,
                lineHeight = EMPTY_NOTE_LINE,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = EMPTY_NOTE_WIDTH),
        )
    }
}

/** One camera, and the whole row is the way to it. */
@Composable
private fun DiscoveredRow(
    camera: CameraEndpoint,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val lemon = BmpTheme.tints.lemon
    val fill = if (highlighted) lemon.fill else MaterialTheme.colorScheme.surfaceVariant
    val border = if (highlighted) lemon.border else MaterialTheme.colorScheme.outlineVariant
    val addressColor =
        if (highlighted) lemon.glyph else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ROW_RADIUS))
            .pressable(onClick = onClick, wide = true),
        color = fill,
        shape = RoundedCornerShape(ROW_RADIUS),
        border = BorderStroke(CARD_BORDER, border),
    ) {
        Row(
            Modifier
                .heightIn(min = ROW_HEIGHT)
                .padding(horizontal = ROW_H_PADDING, vertical = ROW_V_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Same teddy the camera screen shows for itself. This is the one row in the
            // app a parent taps without reading, and the mark is what they aim at.
            IconPlate(
                icon = BmpIcons.Crib,
                fill = MaterialTheme.colorScheme.surface,
                contentColor = if (highlighted) lemon.glyph else MaterialTheme.colorScheme.onSurfaceVariant,
                size = PlateSize.row,
            )
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = camera.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = ROW_NAME),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                // The row carries the reason to choose — which device — rather than an id
                // the parent has to decode.
                Text(
                    text = camera.id,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = ROW_ADDRESS,
                    ),
                    color = addressColor,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(Space.xs))
            Chevron(color = addressColor)
        }
    }
}

/** One of the two ways out when discovery finds nothing. */
@Composable
private fun RouteCard(
    icon: ImageVector,
    tint: Tint?,
    title: String,
    note: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .pressable(onClick = onClick),
        color = tint?.fill ?: MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            CARD_BORDER,
            tint?.border ?: MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(start = Space.md, end = Space.md, top = Space.md, bottom = ROUTE_BOTTOM)) {
            IconPlate(
                icon = icon,
                fill = if (tint != null) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = tint?.glyph ?: MaterialTheme.colorScheme.onSurfaceVariant,
                size = PlateSize.row,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = ROUTE_TITLE,
                    lineHeight = ROUTE_TITLE_LINE,
                ),
                fontWeight = FontWeight.Bold,
                color = tint?.content ?: MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = ROUTE_NOTE,
                    lineHeight = ROUTE_NOTE_LINE,
                ),
                color = tint?.contentMuted ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The address, typed in by hand. */
@Composable
private fun ManualCard(
    title: String,
    host: String,
    error: String?,
    onHostChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(CARD_BORDER, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = Space.lg, vertical = MANUAL_V_PADDING)) {
            SectionLabel(title)
            Spacer(Modifier.height(Space.xs))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        // Go, not Done: the keyboard's own action connects, so a parent who
                        // has just typed an address never has to find a button at all — and
                        // on a remote, pressing the middle button after typing does the
                        // obvious thing.
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { onConnect() }),
                    value = host,
                    onValueChange = onHostChange,
                    placeholder = { Text("192.168.1.42") },
                    singleLine = true,
                    isError = error != null,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = FIELD_TEXT,
                        fontWeight = FontWeight.Bold,
                    ),
                    shape = RoundedCornerShape(FIELD_RADIUS),
                    modifier = Modifier.weight(1f),
                )
                val connectInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = onConnect,
                    interactionSource = connectInteraction,
                    // Disabled rather than allowed to fail silently.
                    enabled = host.isNotBlank(),
                    shape = RoundedCornerShape(FIELD_RADIUS),
                    contentPadding = PaddingValues(horizontal = Space.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    modifier = Modifier
                        .heightIn(min = Touch.min)
                        .pressScale(connectInteraction),
                ) {
                    Text(
                        text = "Connect",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = ACTION_TEXT),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (error != null) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** The promise, in the same leaf as everywhere else, with the shield beside it. */
@Composable
private fun PrivacyFooter(text: String) {
    val privacy = BmpTheme.semantic.privacy
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = BmpIcons.Shield,
            contentDescription = null,
            tint = privacy,
            modifier = Modifier.size(FOOTER_ICON).padding(top = 1.dp),
        )
        Spacer(Modifier.width(Space.xs))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = FOOTER_TEXT,
                lineHeight = FOOTER_LINE,
            ),
            color = privacy,
        )
    }
}

@Composable
private fun ScanOverlay(
    denied: Boolean,
    onCancel: () -> Unit,
    onTypeInstead: () -> Unit,
    onUnavailable: () -> Unit,
    onCode: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!denied) {
            QrScanner(
                modifier = Modifier.fillMaxSize(),
                onResult = onCode,
                onUnavailable = onUnavailable,
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = if (denied) {
                        "Scanning is unavailable without camera access."
                    } else {
                        "Point at the code on the nursery phone"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Space.md, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                // A dead end has to offer the other route. Before, a denied camera left the
                // overlay telling the parent to point at a code the phone could not see.
                if (denied) {
                    TextButton(onClick = onTypeInstead) { Text("Type the address") }
                }
            }
        }
    }
}

/** Accepts a full `bmpro://` link or a bare `host[:port]`. */
private fun parseAddress(input: String): PairingUri.Parsed? =
    PairingUri.parse(input)
        ?: PairingUri.parseHostPort(input)?.let { (host, port) ->
            PairingUri.Parsed(host, port)
        }

/**
 * How long the card says it is searching after the button is pressed.
 *
 * mDNS answers arrive in the first second on a quiet network and can take two on a busy one,
 * so this is long enough to cover a real sweep and short enough that it never sits there
 * claiming to search a network it has already finished with.
 */
private const val SEARCH_FEEDBACK_MILLIS = 2_500L

private val TITLE_TEXT = 21.sp
private val ACTION_TEXT = 13.sp
private val META_TEXT = 10.5.sp
private val EMPTY_TITLE = 17.sp
private val EMPTY_NOTE = 13.sp
private val EMPTY_NOTE_LINE = 20.sp
private val EMPTY_NOTE_WIDTH = 300.dp
private val ROW_GAP = 9.dp
private val ROW_RADIUS = 18.dp
private val ROW_HEIGHT = 64.dp
private val ROW_H_PADDING = 14.dp
private val ROW_V_PADDING = 13.dp
private val ROW_NAME = 16.sp
private val ROW_ADDRESS = 11.5.sp
private val ROUTE_TITLE = 15.sp
private val ROUTE_TITLE_LINE = 19.sp
private val ROUTE_NOTE = 11.5.sp
private val ROUTE_NOTE_LINE = 16.sp
private val ROUTE_BOTTOM = 18.dp
private val MANUAL_V_PADDING = 18.dp
private val FIELD_TEXT = 15.sp
private val FIELD_RADIUS = 14.dp
private val FOOTER_ICON = 15.dp
private val FOOTER_TEXT = 11.5.sp
private val FOOTER_LINE = 17.sp

private val CONTENT_MAX_WIDTH = 560.dp
