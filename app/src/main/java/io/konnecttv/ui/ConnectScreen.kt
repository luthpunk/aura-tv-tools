package io.konnecttv.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.konnecttv.TvViewModel
import io.konnecttv.adb.FoundTv

/** What a pair or manual sheet is about. */
private data class PairTarget(val host: String, val name: String?, val pairPort: Int?)

@Composable
fun ConnectScreen(vm: TvViewModel, busyText: String?) {
    val found by vm.found.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    var pairing by remember { mutableStateOf<PairTarget?>(null) }
    var manual by remember { mutableStateOf(false) }

    fun open(tv: FoundTv) = when {
        tv.pairPort != null -> pairing = PairTarget(tv.host, tv.name, tv.pairPort)
        tv.tlsPort != null -> vm.connect(tv.host, tv.tlsPort, tv.name)
        else -> vm.connect(tv.host, 5555, tv.name)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("Hubungkan TV", style = H1, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)) }

            if (busyText != null) item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = C.Accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(busyText, style = Body, modifier = Modifier.weight(1f))
                        Text("Batal", style = Small.copy(color = C.Accent), modifier = Modifier.clickable { vm.cancelConnect() }.padding(8.dp))
                    }
                }
            }

            if (saved.isNotEmpty()) {
                item { Text("TV ANDA", style = Section, modifier = Modifier.padding(top = 8.dp)) }
                items(saved, key = { "s" + it.id }) { tv ->
                    // A TV on wireless debugging gets a new port after each reboot; mDNS knows the current one.
                    val live = found.firstOrNull { it.host == tv.host }
                    Card(onClick = { vm.connect(tv.host, live?.tlsPort ?: tv.port, tv.name) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Monogram(tv.name)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tv.name, style = Body)
                                Text("${tv.host} · ${if (live != null) "aktif di Wi-Fi" else "terakhir dilihat"}", style = Small)
                            }
                            Text("Hapus", style = Small, modifier = Modifier.clickable { vm.forget(tv.id) }.padding(8.dp))
                        }
                    }
                }
            }

            item { Text("DITEMUKAN DI WI-FI", style = Section, modifier = Modifier.padding(top = 8.dp)) }
            val fresh = found.filter { f -> saved.none { it.host == f.host } }
            if (fresh.isEmpty()) item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = C.Muted, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Mencari Android TV / STB…", style = Small)
                    }
                }
            }
            items(fresh, key = { "f" + it.host }) { tv ->
                Card(onClick = { open(tv) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Monogram(tv.name)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tv.name, style = Body)
                            Text(
                                "${tv.host} · " + when {
                                    tv.pairPort != null -> "siap pairing"
                                    tv.tlsPort != null -> "debugging nirkabel aktif"
                                    else -> "port 5555 siap"
                                },
                                style = Small,
                            )
                        }
                        Text(if (tv.pairPort != null) "Pairing" else "Hubungkan", style = Body.copy(color = C.Accent))
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }
            item { SecondaryButton("Masukkan alamat IP manual") { manual = true } }
            item {
                Text(
                    "Pertama kali? Di TV: Pengaturan › Preferensi Perangkat › Tentang › ketuk Build 7 kali. " +
                        "Lalu di Opsi Pengembang aktifkan USB debugging (dan Debugging nirkabel jika ada). " +
                        "Untuk pairing, buka Debugging nirkabel › Sandingkan perangkat dengan kode.",
                    style = Small,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }

        GlassSheet(pairing != null, onDismiss = { pairing = null }) {
            val target = pairing ?: return@GlassSheet
            var code by remember(target) { mutableStateOf("") }
            var port by remember(target) { mutableStateOf(target.pairPort?.toString().orEmpty()) }
            Text("Pairing dengan ${target.name ?: target.host}", style = Body.copy(fontSize = 17.sp))
            Text("Di TV: Opsi Pengembang › Debugging nirkabel › Sandingkan perangkat dengan kode.", style = Small)
            if (target.pairPort == null) Field(port, { port = it.filter(Char::isDigit) }, "Port pairing (tampil di TV)", KeyboardType.Number)
            CodeBoxes(code) { code = it.filter(Char::isDigit).take(6) }
            PrimaryButton("Hubungkan Pairing", enabled = code.length == 6 && port.isNotEmpty()) {
                vm.pair(target.host, port.toInt(), code, target.name)
                pairing = null
            }
        }

        GlassSheet(manual, onDismiss = { manual = false }) {
            var host by remember { mutableStateOf("") }
            var port by remember { mutableStateOf("5555") }
            Text("Hubungkan dengan IP", style = Body.copy(fontSize = 17.sp))
            Text("Cek IP di TV: Pengaturan › Jaringan › Wi-Fi Anda.", style = Small)
            Field(host, { host = it.trim() }, "IP TV, contoh: 192.168.1.6", KeyboardType.Uri)
            Field(port, { port = it.filter(Char::isDigit) }, "Port (default 5555)", KeyboardType.Number)
            PrimaryButton("Hubungkan", enabled = host.isNotEmpty() && port.isNotEmpty()) {
                vm.connect(host, port.toInt())
                manual = false
            }
            SecondaryButton("Pairing dengan kode saja") {
                if (host.isNotEmpty()) { pairing = PairTarget(host, null, null); manual = false } else vm.say("Masukkan IP TV terlebih dahulu.")
            }
        }
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType = KeyboardType.Text) {
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value, onChange,
        singleLine = true,
        textStyle = Body,
        cursorBrush = SolidColor(C.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth().background(C.Card, shape).border(1.dp, C.RaisedBorder, shape).padding(14.dp),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, style = Small.copy(fontSize = 14.sp)); inner() } },
    )
}

/** Six boxes over one hidden field, so the phone keyboard and paste both work. */
@Composable
private fun CodeBoxes(code: String, onChange: (String) -> Unit) {
    BasicTextField(
        code, onChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(androidx.compose.ui.graphics.Color.Transparent),
        textStyle = Body.copy(color = androidx.compose.ui.graphics.Color.Transparent),
        decorationBox = { inner ->
            Box {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    repeat(6) { i ->
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            Modifier.size(44.dp, 54.dp).background(C.Card, shape)
                                .border(1.dp, if (i == code.length) C.Accent else C.RaisedBorder, shape),
                            contentAlignment = Alignment.Center,
                        ) { Text(code.getOrNull(i)?.toString().orEmpty(), style = H1, textAlign = TextAlign.Center) }
                    }
                }
                Box(Modifier.size(1.dp)) { inner() }
            }
        },
    )
}
