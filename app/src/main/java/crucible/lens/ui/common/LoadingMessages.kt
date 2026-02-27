package crucible.lens.ui.common

import androidx.compose.runtime.*
import kotlinx.coroutines.delay

val allLoadingMessages = listOf(
        // Science puns
        "Heating up the Crucible...",
        "Crystallizing information...",
        "Bonding with your data...",
        "Precipitating insights...",
        "Pumping down the vacuum...",
        "Converging wavefunctions...",

        // MD / DFT
        "Waiting for the MD simulation to equilibrate...",
        "The DFT converged. Still nobody knows why...",
        "Simulating 1 ns of MD, be back in 3 days...",

        // Spinbot
        "The spinbot is thinking about it...",
        "Spinbot.exe has stopped responding, once again...",
        "Bribing the spinbot with a gentle firmware update...",
        "SpinBot: 'I will try spinning, that's a good trick!'",

        // Spin coating (the funny ones)
        "Blaming the humidity for the thin film defects...",
        "Recalibrating after that catastrophic splat...",
        "Wondering why the center is always thicker...",

        // Procrastination
        "Reorganizing data instead of writing the introduction...",
        "Finding one more experiment before starting the manuscript...",
        "The paper will write itself eventually, right?...",
        "Waiting for the right mood to write...",

        // Molecular Foundry & Berkeley Lab
        "Somewhere in Building 67, the elevator is broken.",
        "Berkeley Lab: where the hills are steep and the science is steeper...",
        "Waiting for that User Proposal to be accepted…",
        "ALS beamtime secured, samples prepared, detector broke yesterday...",
        "News from ALS: the beamline is up, the data system is down, the PI is on vacation.",
        "Your dataset will be ready when Strawberry Gate opens again...",
        "Biking up Oof \uD83D\uDEB2",

        // Crucible
        "Digging through Crucible's storage.",
        "Checking if you really uploaded that dataset.",

        // Declan
        "Waiting for Declan to finish making the solution...",
        "Declan said the concentration should be 'about right'...",
        "Declan ran out of solvent, sourcing more...",

        // Tim
        "Tim is unjamming the SpinBot again...",
        "Tim swears the last recipe definitely worked fine...",
        "Tim is talking the SpinBot out of an existential crisis...",

        // Fabo
        "Fabo is fixing a bug he absolutely did not introduce...",
        "Fabo is doing 'quick' data analysis since 9 am…",
        "Fabo swore that pull request would not break this...",

        // Morgan
        "Asking Morgan why the API returned that...",
        "Morgan promised this endpoint would never break.",
        "The API will be back up when Morgan returns from the marathon.",

        // Ed
        "Ed is writing a ScopeFoundry plugin for this exact situation...",
        "Ed says we should automate this loading screen too...",

        // Maher
        "Running Maher's GPR model — he says it'll converge eventually.",
        "Maher says the uncertainty is well-calibrated, probably.",
        "Maher's acquisition function is actively exploring this delay...",

        // Ari
        "Ari is machining a custom part to fix this...",
        "Ari said it'll be ready by Tuesday (± 2 weeks)...",
        "Ari wanted to make this loading screen with the CNC machine...",

        // Movies
        "Those are not the datasets you are looking for.",
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
