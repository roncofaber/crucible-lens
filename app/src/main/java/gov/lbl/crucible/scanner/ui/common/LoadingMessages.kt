package gov.lbl.crucible.scanner.ui.common

import androidx.compose.runtime.*
import kotlinx.coroutines.delay

@Composable
fun LoadingMessage(): String {
    val messages = listOf(
        "Heating up the crucible...",
        "Forging molecular connections...",
        "Analyzing atomic structures...",
        "Consulting the periodic table...",
        "Mixing chemical reactions...",
        "Calibrating the instruments...",
        "Querying the molecular foundry...",
        "Examining sample data...",
        "Scanning the database...",
        "Melting barriers to knowledge...",
        "Crystallizing information...",
        "Bonding with your data...",
        "Synthesizing results...",
        "Precipitating insights...",
        "Distilling pure knowledge...",
        "Aligning quantum states...",
        "Measuring nanoscale features...",
        "Charging the electron beam...",
        "Focusing the microscope...",
        "Processing spectral data...",
        "Computing band structures...",
        "Simulating molecular dynamics...",
        "Equilibrating the solution...",
        "Adjusting pH levels...",
        "Centrifuging samples...",
        "Spinning up the NMR...",
        "Pumping down the vacuum...",
        "Degassing the solvent...",
        "Activating the catalyst...",
        "Monitoring the reaction...",
        "Collecting diffraction patterns...",
        "Indexing crystal planes...",
        "Refining the structure...",
        "Calculating electron density...",
        "Optimizing geometry...",
        "Running DFT calculations...",
        "Converging wavefunctions...",
        "Exciting electronic transitions...",
        "Relaxing nuclear coordinates...",
        "Probing surface chemistry..."
    )

    var currentMessageIndex by remember { mutableStateOf((0 until messages.size).random()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2500) // Change message every 2.5 seconds
            currentMessageIndex = (0 until messages.size).random()
        }
    }

    return messages[currentMessageIndex]
}
