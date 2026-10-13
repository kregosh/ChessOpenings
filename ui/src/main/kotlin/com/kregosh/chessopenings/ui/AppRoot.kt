package com.kregosh.chessopenings.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kregosh.chessopenings.core.training.Phase
import com.kregosh.chessopenings.core.training.SetupCatalog
import com.kregosh.chessopenings.core.training.TrainerAction
import com.kregosh.chessopenings.core.training.TrainerController
import com.kregosh.chessopenings.ui.screens.ReportScreen
import com.kregosh.chessopenings.ui.screens.SetupScreen
import com.kregosh.chessopenings.ui.screens.SetupSelection
import com.kregosh.chessopenings.ui.screens.TrainingScreen
import com.kregosh.chessopenings.ui.theme.TrainerTheme

/** The whole app: setup, training and report, driven by [controller]. */
@Composable
fun AppRoot(controller: TrainerController, catalog: SetupCatalog, initialSelection: SetupSelection = SetupSelection(), onSelectionUsed: (SetupSelection) -> Unit = {}) {
    val state by controller.state.collectAsState()
    var selection by remember { mutableStateOf(initialSelection) }
    TrainerTheme {
        Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            val report = state.report
            when {
                state.phase == Phase.IDLE && state.error == null -> SetupScreen(catalog, selection) { setup, chosen ->
                    selection = chosen
                    onSelectionUsed(chosen)
                    controller.dispatch(TrainerAction.Start(setup))
                }
                state.phase == Phase.REPORT && report != null -> ReportScreen(report, controller::dispatch)
                else -> TrainingScreen(state, controller::dispatch)
            }
        }
    }
}
