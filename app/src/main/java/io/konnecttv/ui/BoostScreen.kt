package io.konnecttv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.konnecttv.LAUNCHER_STEPS
import io.konnecttv.LauncherSetup
import io.konnecttv.TabBarClearance
import io.konnecttv.TvViewModel
import io.konnecttv.data.SavedTv
import kotlinx.coroutines.delay
import java.util.Locale

private val SleepChoices = listOf(5, 15, 30, 60)
private val Mint = Color(0xFF3FD6A0)
private val Cyan = Color(0xFF9FF7FF)

@Composable
fun BoostScreen(vm: TvViewModel, tv: SavedTv) {
    val mem by vm.memory.collectAsStateWithLifecycle()
    val tweaks by vm.tweaks.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var pickSleep by remember { mutableStateOf(false) }
    var pickMode by remember { mutableStateOf(false) }
    var launcherMenu by remember { mutableStateOf(false) }
    val launcherOn = tweaks?.launcherOn == true

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Boost", style = H1, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val m = mem
                StatCard("RAM", m?.freeKb, m?.totalKb, Modifier.weight(1f))
                StatCard("Penyimpanan", m?.storageFreeKb, m?.storageTotalKb, Modifier.weight(1f))
            }
            tv.ramBefore?.let { Text("RAM bebas sebelum optimasi: ${gb(it)}", style = Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }

            LauncherOrb(launcherOn, busy, tv.name) {
                if (launcherOn) launcherMenu = true else pickMode = true
            }

            val t = tweaks
            TweakRow("Matikan animasi", "Menu & jendela terbuka instan tanpa jeda", t?.animationsOff == true) { vm.setAnimationsOff(it) }
            TweakRow("Matikan screensaver", "Menghemat daya & sistem saat TV diam", t?.screensaverOff == true) { vm.setScreensaverOff(it) }
            Card(onClick = { pickSleep = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Waktu tidur otomatis", style = Body)
                        Text("TV mati otomatis saat tidak digunakan", style = Small)
                    }
                    // Android stores "never" as Integer.MAX_VALUE ms, about 35791 minutes.
                    val sleep = t?.sleepMinutes?.let { if (it > 24 * 60) "Jangan pernah" else "$it mnt" }
                    Text(sleep?.let { "$it ›" } ?: "›", style = Body.copy(color = C.Accent))
                }
            }
            Spacer(Modifier.height(4.dp))
            SecondaryButton(if (busy) "Memproses…" else "Tutup aplikasi latar & bersihkan cache") { vm.killBackground() }
            Spacer(Modifier.height(TabBarClearance))
        }

        GlassSheet(pickSleep, onDismiss = { pickSleep = false }) {
            Text("Waktu tidur otomatis", style = Body.copy(fontSize = 17.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SleepChoices.forEach { m -> Chip("$m menit", tweaks?.sleepMinutes == m) { vm.setSleep(m); pickSleep = false } }
            }
        }

        GlassSheet(launcherMenu, onDismiss = { launcherMenu = false }) {
            Text("Aura TV Home", style = Body.copy(fontSize = 17.sp))
            Text("Aktif di ${tv.name}.", style = Small)
            PrimaryButton("Buka Aura TV Home") { launcherMenu = false; vm.openLauncher() }
            SecondaryButton("Kembalikan Beranda Bawaan TV") { launcherMenu = false; vm.setLauncher(false) }
        }

        GlassSheet(pickMode, onDismiss = { pickMode = false }) {
            Text("Pasang Aura TV Home", style = Body.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
            Text("Pilih mode pemasangan beranda untuk ${tv.name}:", style = Small)
            Spacer(Modifier.height(4.dp))

            Card(onClick = {
                pickMode = false
                vm.setLauncher(on = true, disableStock = true)
            }) {
                Column(Modifier.fillMaxWidth()) {
                    Text("1. Nonaktifkan Launcher Bawaan (Rekomendasi)", style = Body.copy(fontWeight = FontWeight.SemiBold, color = C.Accent))
                    Spacer(Modifier.height(3.dp))
                    Text("Bebas iklan & bersih total. Tombol Home remote TV 100% langsung membuka Aura TV Home tanpa popup pilihan.", style = Small)
                }
            }

            Card(onClick = {
                pickMode = false
                vm.setLauncher(on = true, disableStock = false)
            }) {
                Column(Modifier.fillMaxWidth()) {
                    Text("2. Tetap Aktifkan Launcher Bawaan (Berdampingan)", style = Body.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(3.dp))
                    Text("Launcher bawaan tetap aktif di latar belakang. Aura TV Home dijadikan launcher default utama.", style = Small)
                }
            }
        }

        val setup by vm.launcherSetup.collectAsStateWithLifecycle()
        setup?.let { st ->
            // Can't be swiped away while the TV is switching: closing it mid-way hides the only sign that work is going on.
            GlassSheet(true, onDismiss = { if (st.done || st.error != null) vm.closeLauncherSetup() }) {
                SetupProgress(st) { vm.closeLauncherSetup() }
            }
        }
    }
}

/** The main feature: one glowing button. The ring fills and the core lights up when it's on. */
@Composable
private fun LauncherOrb(on: Boolean, busy: Boolean, tvName: String, onClick: () -> Unit) {
    val progress by animateFloatAsState(if (on) 1f else 0f, tween(900), label = "ring")
    val core by animateColorAsState(if (on) C.OrbOn else C.Raised, tween(600), label = "core")
    val spark by animateColorAsState(if (on) C.Accent else C.Muted, tween(500), label = "spark")
    Card {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.size(190.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 7.dp.toPx()
                    val inset = w / 2 + 2.dp.toPx()
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                    // Soft glow behind the core once it's on.
                    drawCircle(Brush.radialGradient(listOf(Mint.copy(alpha = 0.28f * progress), Color.Transparent)), radius = size.minDimension / 2)
                    drawArc(C.Border, 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
                    drawArc(Brush.linearGradient(listOf(Mint, Cyan)), -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                }
                Box(
                    Modifier.size(132.dp).clip(CircleShape).background(core).border(1.dp, C.Border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) CircularProgressIndicator(color = C.Accent, strokeWidth = 2.dp, modifier = Modifier.size(40.dp))
                    else Glyph(Glyphs.Sparkle, spark, 46.dp, Modifier.rotate(90f * progress).scale(1f + 0.08f * progress))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Aura TV Home", style = Body.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(8.dp))
            val pill = when {
                busy -> "MEMPROSES…"
                on -> "AKTIF DI ${tvName.uppercase()}"
                else -> "KETUK UNTUK MEMASANG"
            }
            Text(
                pill,
                style = Small.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = if (on) C.Accent else C.Muted),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) C.Accent.copy(alpha = 0.13f) else C.Raised).padding(horizontal = 12.dp, vertical = 5.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text("Launcher TV modern, ringan, dan cepat. Bebas iklan.", style = Small, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Turning Aura TV Home on: every step as it happens, a running clock. */
@Composable
private fun SetupProgress(st: LauncherSetup, onClose: () -> Unit) {
    val running = !st.done && st.error == null
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) { while (running) { now = System.currentTimeMillis(); delay(1000) } }
    val secs = ((now - st.startedAt) / 1000).coerceAtLeast(0)
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "angle")
    val glow by spin.animateFloat(0.15f, 0.4f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow")

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 5.dp.toPx()
                val inset = w / 2 + 2.dp.toPx()
                val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                val ok = st.error == null
                drawCircle(Brush.radialGradient(listOf((if (ok) Mint else C.Danger).copy(alpha = if (running) glow else 0.25f), Color.Transparent)), radius = size.minDimension / 2)
                drawArc(C.Border, 0f, 360f, false, tl, arcSize, style = Stroke(w))
                if (running) drawArc(Brush.sweepGradient(listOf(Color.Transparent, Mint, Cyan)), angle, 270f, false, tl, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                else drawArc(if (ok) Mint else C.Danger, -90f, 360f, false, tl, arcSize, style = Stroke(w))
            }
            Glyph(Glyphs.Sparkle, if (st.error != null) C.Danger else C.Accent, 38.dp, Modifier.rotate(if (running) angle / 4 else 0f))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when { st.error != null -> "Gagal memasang"; st.done -> "Aura TV Home aktif"; else -> "Memasang Aura TV Home" },
            style = Body.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        )
        Text(String.format(Locale.US, "%d:%02d", secs / 60, secs % 60), style = Small.copy(fontFeatureSettings = "tnum"))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LAUNCHER_STEPS.forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    when {
                        i < st.step -> Check(true)
                        i == st.step && st.error != null -> Text("!", style = Body.copy(color = C.Danger, fontWeight = FontWeight.Bold))
                        i == st.step -> CircularProgressIndicator(color = C.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        else -> Box(Modifier.size(8.dp).clip(CircleShape).background(C.Border))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(label, style = Body.copy(color = if (i <= st.step) C.Text else C.Muted))
            }
        }
    }
    when {
        st.error != null -> Text(st.error, style = Small.copy(color = C.Danger))
        st.done -> Text("Tekan Home pada remote TV untuk melihat hasilnya.", style = Small)
        else -> Text(
            "TV mungkin terlihat diam sesaat saat beralih beranda. Harap tunggu hingga selesai.",
            style = Small.copy(color = C.Muted),
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(C.Raised).padding(12.dp),
        )
    }
    if (!running) PrimaryButton("Selesai", onClick = onClose)
}

@Composable
private fun StatCard(title: String, freeKb: Long?, totalKb: Long?, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = Section)
            Spacer(Modifier.height(10.dp))
            if (freeKb == null || totalKb == null || totalKb == 0L) {
                Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Accent, strokeWidth = 2.dp) }
            } else {
                Ring(freeKb, totalKb, 110.dp)
            }
            Spacer(Modifier.height(8.dp))
            Text(if (totalKb != null && totalKb > 0) "${gb(totalKb)} total" else " ", style = Small)
        }
    }
}

@Composable
private fun Ring(freeKb: Long, totalKb: Long, size: Dp) {
    val fraction by animateFloatAsState(freeKb.toFloat() / totalKb, label = "ring")
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = 9.dp.toPx()
            val inset = w / 2
            val arc = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(C.Border, 0f, 360f, false, tl, arc, style = Stroke(w))
            drawArc(C.Accent, -90f, 360f * fraction, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(gb(freeKb), style = Body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            Text("free", style = Small)
        }
    }
}

@Composable
private fun TweakRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Card(onClick = { onChange(!on) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Body)
                Text(sub, style = Small)
            }
            Spacer(Modifier.width(12.dp))
            Toggle(on, onChange)
        }
    }
}

private fun gb(kb: Long) = String.format(Locale.US, "%.1f GB", kb / 1024.0 / 1024.0)
