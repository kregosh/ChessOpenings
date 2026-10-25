package com.kregosh.chessopenings.android

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import com.kregosh.chessopenings.core.training.Phase
import com.kregosh.chessopenings.core.training.TrainerAction
import com.kregosh.chessopenings.ui.AppRoot
import com.kregosh.chessopenings.ui.theme.TrainerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TrainerApp

        // The engine's WebView lives as long as the app; it's attached here, out of sight,
        // so it keeps running like any visible page.
        val engineView = app.engine.view
        (engineView.parent as? ViewGroup)?.removeView(engineView)
        addContentView(engineView, ViewGroup.LayoutParams(1, 1))

        setContent {
            val loading by app.loading.collectAsState()
            when (val l = loading) {
                is Loading.Ready -> {
                    val state by l.controller.state.collectAsState()
                    // Back leaves a session for the setup screen; on the setup screen it closes the app.
                    BackHandler(enabled = state.phase != Phase.IDLE) { l.controller.dispatch(TrainerAction.Quit) }
                    val background = remember(l.background) { l.background?.let(::BitmapPainter) }
                    AppRoot(
                        l.controller,
                        l.catalog,
                        initialSelection = app.selection,
                        onSelectionUsed = { app.selection = it },
                        background = background,
                        pet = l.pet,
                    )
                }
                is Loading.Failed -> Centered { Text("Couldn't load the opening book: ${l.message}") }
                Loading.InProgress -> Centered { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    TrainerTheme {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
    }
}
