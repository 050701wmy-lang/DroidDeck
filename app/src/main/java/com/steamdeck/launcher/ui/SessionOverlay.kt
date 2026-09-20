package com.steamdeck.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steamdeck.launcher.R

/** A line of numbers in the top-right corner. It takes no touches: everything goes to the game. */
@Composable
fun HudText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color.White,
            modifier = Modifier
                .padding(12.dp)
                .background(Color(0x8C000000))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * What the user sees until the client draws its first frame: the runtime's milestone, the
 * client's download progress lifted out of its log, a clock, and a hint. Opaque, so a stale
 * frame from an earlier session never shows through, and it swallows touches.
 */
@Composable
fun LoadingOverlay(step: String, percent: Int, elapsed: String, hint: String, ended: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = MutableInteractionSource(), indication = null) {}
            .padding(32.dp),
    ) {
        Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(14.dp))
        Text(if (ended) "The session ended" else "Steam is loading", color = Color.White, fontSize = 17.sp)
        Text(step, color = Color(0xFFB8C4D0), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        if (!ended) {
            Spacer(Modifier.height(14.dp))
            if (percent >= 0) LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.width(220.dp))
            else LinearProgressIndicator(modifier = Modifier.width(220.dp))
            Text(elapsed, color = Color(0xFF667788), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            Text(
                hint, color = Color(0xFF667788), fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp).widthIn(max = 360.dp),
            )
        }
    }
}

/** Everything the drawer shows and does. */
class DrawerActions(
    val hudOn: Boolean,
    val frameGenLabel: String,
    val oscMode: String,
    val shapeMode: String,
    val onHud: (Boolean) -> Unit,
    val onFrameGen: () -> Unit,
    val onKeyboard: () -> Unit,
    val onProtons: () -> Unit,
    val onOsc: () -> Unit,
    val onShape: () -> Unit,
    val onBackground: () -> Unit,
    val onStop: () -> Unit,
    val onClose: () -> Unit,
)

/**
 * The drawer Back opens over a running session: the switches a player reaches for mid-game, and
 * the two things that leave the session. A tap on the dimmed area closes it.
 */
@Composable
fun SessionDrawer(a: DrawerActions) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = a.onClose),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(264.dp)
                .background(Color(0xF2151A22))
                // The panel swallows its own touches so they do not close it.
                .clickable(interactionSource = MutableInteractionSource(), indication = null) {}
                .padding(16.dp),
        ) {
            Text("SteamDeck", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Performance HUD", color = Color(0xFFDDDDDD), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = a.hudOn, onCheckedChange = a.onHud)
            }
            OutlinedButton(onClick = a.onKeyboard, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Keyboard", fontSize = 13.sp)
            }
            OutlinedButton(onClick = a.onFrameGen, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Frame generation: ${a.frameGenLabel}", fontSize = 13.sp)
            }
            OutlinedButton(onClick = a.onProtons, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Compatibility tools", fontSize = 13.sp)
            }
            OutlinedButton(onClick = a.onOsc, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("On-screen controls: ${a.oscMode}", fontSize = 13.sp)
            }
            OutlinedButton(onClick = a.onShape, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Display shape: ${a.shapeMode} · next session", fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = a.onBackground, modifier = Modifier.fillMaxWidth()) {
                Text("Send to background", fontSize = 13.sp)
            }
            OutlinedButton(onClick = a.onStop, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Stop session", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
