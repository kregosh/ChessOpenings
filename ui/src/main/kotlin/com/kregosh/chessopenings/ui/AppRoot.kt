package com.kregosh.chessopenings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.kregosh.chessopenings.core.pet.PetController
import com.kregosh.chessopenings.core.training.Phase
import com.kregosh.chessopenings.core.training.Quality
import com.kregosh.chessopenings.core.training.SetupCatalog
import com.kregosh.chessopenings.core.training.TrainerAction
import com.kregosh.chessopenings.core.training.TrainerController
import com.kregosh.chessopenings.core.training.TrainerState
import com.kregosh.chessopenings.ui.pet.FeedingCard
import com.kregosh.chessopenings.ui.pet.PetCard
import com.kregosh.chessopenings.ui.pet.PupReaction
import com.kregosh.chessopenings.ui.pet.TrainingPup
import com.kregosh.chessopenings.ui.pet.rememberReaction
import com.kregosh.chessopenings.ui.screens.ReportScreen
import com.kregosh.chessopenings.ui.screens.SetupScreen
import com.kregosh.chessopenings.ui.screens.SetupSelection
import com.kregosh.chessopenings.ui.screens.TrainingScreen
import com.kregosh.chessopenings.ui.theme.TrainerTheme
import kotlinx.coroutines.delay

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
    pet: PetController? = null,
) {
    val state by controller.state.collectAsState()
    var selection by remember { mutableStateOf(initialSelection) }
    val report = state.report
    if (pet != null) {
        LaunchedEffect(pet) {
            while (true) {
                pet.refresh()
                delay(PET_REFRESH_MS)
            }
        }
        // Every finished session feeds the pup.
        LaunchedEffect(report) { if (report != null) pet.sessionFinished(report) }
    }
    val petView = pet?.view?.collectAsState()?.value
    TrainerTheme {
        val content = @Composable {
            when {
                state.phase == Phase.IDLE && state.error == null -> SetupScreen(
                    catalog,
                    selection,
                    header = { if (pet != null && petView != null) PetCard(petView, pet::pat) },
                ) { setup, chosen ->
                    selection = chosen
                    onSelectionUsed(chosen)
                    controller.dispatch(TrainerAction.Start(setup))
                }
                state.phase == Phase.REPORT && report != null -> ReportScreen(report, controller::dispatch) {
                    petView?.lastTreat?.let { FeedingCard(petView, it) }
                }
                else -> TrainingScreen(state, controller::dispatch) {
                    if (petView != null) TrainingPup(petView, rememberMoveReaction(state))
                }
            }
        }
        if (background == null) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) { content() }
            }
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
                    // Clear of the status and navigation bars on Android; no insets on the web.
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                        .widthIn(max = maxWidth).fillMaxWidth().fillMaxHeight().padding(PANEL_MARGIN),
                    shape = RoundedCornerShape(16.dp),
                    // A hint of the picture shows through behind the screens.
                    color = MaterialTheme.colorScheme.background.copy(alpha = PANEL_ALPHA),
                    shadowElevation = 8.dp,
                ) { content() }
            }
        }
    }
}

/** The pup reacts to the user's latest graded move, and cheers at the end of the book. */
@Composable
private fun rememberMoveReaction(state: TrainerState): PupReaction? {
    val index = state.moves.indexOfLast { it.byUser && !it.startingLine && it.verdict != null }
    val quality = state.moves.getOrNull(index)?.verdict?.quality
    val keepGoing = state.phase == Phase.KEEP_GOING
    val reaction = when {
        keepGoing -> PupReaction.CHEER
        quality == null -> null
        quality <= Quality.BEST -> PupReaction.CHEER
        quality == Quality.PLAYABLE -> null
        quality == Quality.BLUNDER -> PupReaction.COVER_EYES
        else -> PupReaction.WORRIED
    }
    return rememberReaction(Triple(index, quality, keepGoing), reaction)
}

private const val PET_REFRESH_MS = 60_000L
private val PANEL_MARGIN = 10.dp
private const val PANEL_ALPHA = 0.86f
private val BACKGROUND_BLUR = 2.dp
private const val BACKGROUND_SATURATION = 0.8f
private const val BACKGROUND_SCRIM = 0.3f
