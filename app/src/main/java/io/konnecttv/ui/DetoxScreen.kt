package io.konnecttv.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.konnecttv.TabBarClearance
import io.konnecttv.TvViewModel
import io.konnecttv.data.Group
import java.text.DateFormat
import java.util.Date

@Composable
fun DetoxScreen(vm: TvViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var showHistory by remember { mutableStateOf(false) }
    var showNeeded by remember { mutableStateOf(false) }

    val active = apps.filter { !it.disabled }
    val safe = active.filter { vm.debloat.group(it) == Group.SAFE }
    val needed = active.filter { vm.debloat.group(it) == Group.NEEDED }
    val mine = active.filter { vm.debloat.group(it) == Group.YOURS }
    // Ticked by default unless the list marks the app optional ("keep if you use it").
    val ticked = remember(safe.map { it.pkg }) {
        safe.filter { vm.debloat.safe[it.pkg]?.optional != true }.map { it.pkg }.toMutableStateList()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Detox", style = H1, modifier = Modifier.weight(1f))
                    Text(
                        "⟲ Riwayat${if (history.isNotEmpty()) " · ${history.size}" else ""}",
                        style = Small.copy(fontSize = 13.sp, color = if (history.isNotEmpty()) C.Accent else C.Muted),
                        modifier = Modifier.clip(CircleShape).clickable { showHistory = true }.padding(10.dp),
                    )
                }
            }
            item {
                Card {
                    Text(
                        if (safe.isEmpty()) "Semua sudah bersih" else "${safe.size} aplikasi aman dinonaktifkan",
                        style = Body.copy(color = C.Accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("Hanya dinonaktifkan, tidak dihapus permanen. Bisa dipulihkan kapan saja.", style = Small)
                }
            }

            if (safe.isNotEmpty()) item { Text("AMAN DINONAKTIFKAN · ${safe.size}", style = Section, modifier = Modifier.padding(top = 8.dp)) }
            items(safe, key = { it.pkg }) { app ->
                val entry = vm.debloat.safe[app.pkg]
                val on = app.pkg in ticked
                Card(onClick = { if (on) ticked.remove(app.pkg) else ticked.add(app.pkg) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Check(on)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = Body)
                            Text(entry?.reason.orEmpty(), style = Small)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().clickable { showNeeded = !showNeeded }.padding(top = 10.dp, bottom = 2.dp)) {
                    Text("DIBUTUHKAN SISTEM TV · ${needed.size} · TERKUNCI", style = Section, modifier = Modifier.weight(1f))
                    Text(if (showNeeded) "Sembunyikan" else "Tampilkan", style = Section.copy(color = C.Accent))
                }
            }
            if (showNeeded) items(needed, key = { "n" + it.pkg }) { app ->
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Check(false, enabled = false)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(app.label, style = Body.copy(color = C.Muted))
                            Text(app.pkg, style = Small)
                        }
                    }
                }
            }
            item { Text("APLIKASI ANDA · ${mine.size} · DIPERTAHANKAN", style = Section, modifier = Modifier.padding(top = 10.dp)) }
            item { Text(mine.joinToString(" · ") { it.label }.ifEmpty { "Tidak ada" }, style = Small) }

            item { Spacer(Modifier.height(8.dp)) }
            item {
                PrimaryButton(
                    if (ticked.isEmpty()) "Pilih aplikasi untuk dinonaktifkan" else "Nonaktifkan ${ticked.size} aplikasi",
                    enabled = ticked.isNotEmpty(),
                ) { vm.turnOff(ticked.toList()) }
            }
            item { Spacer(Modifier.height(TabBarClearance)) }
        }

        GlassSheet(showHistory, onDismiss = { showHistory = false }) {
            Text("Riwayat Perubahan", style = Body.copy(fontSize = 17.sp))
            if (history.isEmpty()) Text("Belum ada riwayat perubahan.", style = Small)
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { "${it.time}${it.target}" }) { change ->
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (change.kind) {
                                        "disable" -> "${change.label} dinonaktifkan"
                                        else -> change.label
                                    },
                                    style = Body,
                                )
                                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(change.time)), style = Small)
                            }
                            Text("Pulihkan", style = Body.copy(color = C.Accent), modifier = Modifier.clip(CircleShape).clickable { vm.undo(change) }.padding(8.dp))
                        }
                    }
                }
            }
            if (history.isNotEmpty()) SecondaryButton("Pulihkan semua perubahan") { vm.undoAll(); showHistory = false }
        }
    }

    LaunchedEffect(Unit) { if (apps.isEmpty()) vm.refreshAll() }
}
