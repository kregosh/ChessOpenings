package com.kregosh.chessopenings.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.kregosh.chessopenings.core.book.OpeningBook
import com.kregosh.chessopenings.core.pet.PetController
import com.kregosh.chessopenings.core.training.SetupCatalog
import com.kregosh.chessopenings.core.training.TrainerController
import com.kregosh.chessopenings.ui.AppRoot
import com.kregosh.chessopenings.ui.theme.TrainerTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlin.js.Promise
import org.jetbrains.skia.Image as SkiaImage

private fun fetchBytes(url: String): Promise<JsAny> =
    js("fetch(url).then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status + ' for ' + url); return r.arrayBuffer(); }).then(function (b) { return new Uint8Array(b); })")

private fun byteLength(array: JsAny): Int = js("array.length")
private fun byteAt(array: JsAny, i: Int): Byte = js("array[i]")

private suspend fun loadBytes(url: String): ByteArray {
    val array = fetchBytes(url).await<JsAny>()
    return ByteArray(byteLength(array)) { byteAt(array, it) }
}

private sealed interface Loading {
    data object InProgress : Loading
    data class Failed(val message: String) : Loading
    data class Ready(val controller: TrainerController, val catalog: SetupCatalog, val background: ImageBitmap?, val pet: PetController) : Loading
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val scope = MainScope()
    var loading by mutableStateOf<Loading>(Loading.InProgress)
    scope.launch {
        loading = try {
            val book = OpeningBook.decode(loadBytes("book.bin"))
            val engine = WebEngine("engine/stockfish.js", scope)
            // The background picture is decoration: the app works without it.
            val background = runCatching { SkiaImage.makeFromEncoded(loadBytes("background.jpg")).toComposeImageBitmap() }.getOrNull()
            val pet = PetController(LocalStoragePetStore(), ::webMoment)
            Loading.Ready(TrainerController(book, engine, scope), SetupCatalog(book), background, pet)
        } catch (e: Throwable) {
            Loading.Failed(e.message ?: e.toString())
        }
    }
    ComposeViewport(viewportContainerId = "app") {
        when (val l = loading) {
            is Loading.Ready -> {
                val background = remember(l.background) { l.background?.let(::BitmapPainter) }
                AppRoot(l.controller, l.catalog, background = background, pet = l.pet)
            }
            is Loading.Failed -> Centered { Text("Couldn't load the opening book: ${l.message}") }
            Loading.InProgress -> Centered { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    TrainerTheme {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
    }
}
