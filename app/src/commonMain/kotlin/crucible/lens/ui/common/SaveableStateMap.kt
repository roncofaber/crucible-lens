package crucible.lens.ui.common

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateMap

/**
 * Saver for a String-keyed [SnapshotStateMap] of primitive values (Boolean, Int, etc.), so group
 * expand/collapse and pagination state can survive navigating away and back via rememberSaveable,
 * the same way rememberLazyListState's scroll position already does.
 */
fun <V : Any> stateMapSaver(): Saver<SnapshotStateMap<String, V>, List<Pair<String, V>>> = Saver(
    save = { it.toList() },
    restore = { entries -> mutableStateMapOf<String, V>().apply { putAll(entries) } }
)
