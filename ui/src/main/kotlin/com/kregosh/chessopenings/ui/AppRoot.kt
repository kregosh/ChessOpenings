package com.kregosh.chessopenings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kregosh.chessopenings.core.training.Phase
import com.kregosh.chessopenings.core.training.SetupCatalog
import com.kregosh.chessopenings.core.training.TrainerAction
import com.kregosh.chessopenings.core.training.TrainerController
import com.kregosh.chessopenings.ui.screens.ReportScreen
import com.kregosh.chessopenings.ui.screens.SetupScreen
import com.kregosh.chessopenings.ui.screens.SetupSelection
import com.kregosh.chessopenings.ui.screens.TrainingScreen
import com.kregosh.chessopenings.ui.theme.TrainerTheme

/**
 * The whole app: setup, training and report, driven by [controller]. With a [background]
 * picture, the screens sit on a panel in front of it.
 */
@Composable
fun AppRoot(
    controller: TrainerController,
    catalog: SetupCatalog,
    initialSelection: SetupSelection = SetupSelection(),
    onSelectionUsed: (SetupSelection) -> Unit = {},
    background: Painter? = null,
) {
    val state by controller.state.collectAsState()
    var selection by remember { mutableStateOf(initialSelection) }
    TrainerTheme {
        val content = @Composable {
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
        if (background == null) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                // Muted so it stays decoration: softened, less saturated, and washed out
                // towards the page colour.
                Image(
                    background,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(BACKGROUND_SATURATION) }),
                    modifier = Modifier.fillMaxSize().blur(BACKGROUND_BLUR),
                )
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = BACKGROUND_SCRIM)))
                // Setup and report are a narrow column; training spreads out next to the board.
                val maxWidth = if (state.phase == Phase.IDLE || state.phase == Phase.REPORT) 720.dp else 1200.dp
                Surface(
                    Modifier.widthIn(max = maxWidth).fillMaxWidth().fillMaxHeight().padding(PANEL_MARGIN),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 8.dp,
                ) { content() }
            }
        }
    }
}

private val PANEL_MARGIN = 10.dp
private val BACKGROUND_BLUR = 6.dp
private const val BACKGROUND_SATURATION = 0.6f
private const val BACKGROUND_SCRIM = 0.55f
