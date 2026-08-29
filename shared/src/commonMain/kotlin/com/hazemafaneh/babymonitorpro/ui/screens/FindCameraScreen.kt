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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
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
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.PairingUri
import com.hazemafaneh.babymonitorpro.core.isLinkLocalIpv4
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

    val browser = remember { createBrowser() }
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
    val discovered = remember(announced) {
        val own = ownLanAddresses()
        announced.filterNot { it.host in own || isLinkLocalIpv4(it.host) }
    }

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
            .safeContentPadding()
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
                    discovered.isEmpty() -> "mDNS · local only"
                    discovered.size == 1 -> "1 found"
                    else -> "${discovered.size} found"
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

                    discovered.isEmpty() -> EmptyNote(
                        icon = BmpIcons.House,
                        title = "Still looking around",
                        note = "Open BabyMonitor Pro on the phone you're leaving in the " +
                            "nursery and tap Use this device as Camera.",
                    )

                    else -> Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                        discovered.forEachIndexed { index, camera ->
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
                                "192.168.1.42, or 192.168.1.42:8080 if the port was changed."
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
private fun DiscoveryCard(meta: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(CARD_BORDER, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Space.lg)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("On this network")
                // How the search works, stated rather than implied. A parent who wonders
                // whether this app is scanning the internet gets the answer in the corner
                // of the card doing the scanning.
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = META_TEXT,
                    ),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(Space.sm))
            content()
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
                icon = BmpIcons.Teddy,
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
                .safeContentPadding()
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
