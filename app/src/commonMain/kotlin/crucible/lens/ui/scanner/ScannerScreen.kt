package crucible.lens.ui.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import crucible.lens.ui.navigation.DeepLinkTarget
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ScannerScreen(
    onResourceResolved: (String) -> Unit,
    onProjectResolved: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ScannerViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var scannerSession by remember { mutableIntStateOf(0) }

    LaunchedEffect(state) {
        val target = (state as? ScannerResolutionState.Resolved)?.target ?: return@LaunchedEffect
        viewModel.reset()
        when (target) {
            is DeepLinkTarget.Resource -> onResourceResolved(target.resourceReference)
            is DeepLinkTarget.Project -> onProjectResolved(target.projectReference)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        key(scannerSession) {
            QRCodeScannerView(
                modifier = Modifier.fillMaxSize(),
                onCodeScanned = viewModel::scan,
                onBack = {
                    viewModel.reset()
                    onBack()
                }
            )
        }

        when (val current = state) {
            ScannerResolutionState.Scanning,
            is ScannerResolutionState.Resolved -> Unit
            ScannerResolutionState.Resolving -> ScannerStatusSurface(
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                CircularProgressIndicator()
                Text("Checking with Crucible", style = MaterialTheme.typography.bodyMedium)
            }
            is ScannerResolutionState.Error -> ScannerStatusSurface(
                modifier = Modifier.align(Alignment.BottomCenter),
                isError = true
            ) {
                Text(
                    current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                TextButton(
                    onClick = {
                        viewModel.reset()
                        scannerSession++
                    }
                ) {
                    Text("Scan again")
                }
            }
        }
    }
}

@Composable
private fun ScannerStatusSurface(
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}
