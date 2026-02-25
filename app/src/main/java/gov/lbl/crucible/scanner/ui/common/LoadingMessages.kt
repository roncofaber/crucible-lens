package gov.lbl.crucible.scanner.ui.common

import androidx.compose.runtime.*
import kotlinx.coroutines.delay

val allLoadingMessages = listOf(
        // Science puns
        "Heating up the crucible...",
        "Crystallizing information...",
        "Bonding with your data...",
        "Precipitating insights...",
        "Spinning up the NMR...",
        "Pumping down the vacuum...",
        "Converging wavefunctions...",

        // MD / DFT
        "Waiting for the MD simulation to equilibrate…",
        "The DFT converged, but nobody knows why…",
        "Simulating 1ns of molecular dynamics, back in 3 days…",

        // Spinbot
        "The spinbot is thinking about it…",
        "Spinbot.exe has stopped responding…",
        "Bribing the spinbot with a gentle firmware update…",

        // Spin coating (the funny ones)
        "Hoping the chuck held vacuum...",
        "Blaming the humidity for coat defects...",
        "Recalibrating after that catastrophic splat...",
        "Wondering why the center is always thicker...",
        "Arguing with the spin coater manual...",

        // Procrastination
        "Definitely not avoiding that paper deadline...",
        "Reorganizing data instead of writing the introduction...",
        "Finding one more experiment before starting the manuscript...",
        "Perfecting figures instead of writing figure captions...",
        "The paper will write itself eventually, right?...",
        "Citing references to feel productive...",
        "Waiting for the right mood to write...",

        // Molecular Foundry & Berkeley Lab
        "Somewhere in Building 67, a diffractometer is humming…",
        "Berkeley Lab: where the hills are steep and the science is steeper…",
        "Still waiting for that User Proposal to be accepted…",
        "ALS beamtime secured, samples shipped, detector broke yesterday…",
        "ALS called — the beamline is up, the data system is down, the PI is on vacation…",

        // Declan
        "Waiting for Declan to finish making the solution…",
        "Declan said the concentration is 'about right'…",
        "Declan ran out of solvent, sourcing more…",

        // Tim
        "Tim is unjamming the spinbot again…",
        "Tim swears the last recipe definitely worked fine…",
        "Tim is recalibrating the spinbot's existential crisis…",

        // Fabo
        "Fabo is fixing a bug he absolutely did not introduce…",
        "Fabo is doing 'quick' data analysis since 9am…",
        "Fabo renamed a variable and called it a refactor…",

        // Morgan
        "Asking Morgan why the API returned that…",
        "Morgan promised this endpoint would never break…",
        "Filing ticket #347 with Morgan…",

        // Ed
        "Ed automated the experiment but forgot to automate the analysis…",
        "Ed is writing a ScopeFoundry plugin for this exact situation…",
        "Ed says we should automate this loading screen too…",

        // Carolin
        "Carolin needs just one more sample before the paper…",
        "Waiting for Carolin's feedback since last Tuesday…",
        "Carolin approved this measurement six substrates ago…",

        // Maher
        "Running Maher's GPR model — it'll converge eventually…",
        "Maher says the uncertainty is well-calibrated, probably…",
        "Maher's acquisition function is actively exploring this delay…",

        // Ari
        "Ari is machining a custom part for this…",
        "Ari said it'll be ready by Tuesday (± 2 weeks)…",
        "Ari is sourcing a part that definitely exists…"
)

@Composable
fun LoadingMessage(): String {
    var currentMessageIndex by remember { mutableStateOf((0 until allLoadingMessages.size).random()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2500)
            currentMessageIndex = (0 until allLoadingMessages.size).random()
        }
    }

    return allLoadingMessages[currentMessageIndex]
}
