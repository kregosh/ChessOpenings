package com.kregosh.chessopenings.android

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kregosh.chessopenings.core.book.OpeningBook
import com.kregosh.chessopenings.core.pet.PetController
import com.kregosh.chessopenings.core.training.SetupCatalog
import com.kregosh.chessopenings.core.training.TrainerController
import com.kregosh.chessopenings.ui.screens.SetupSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the app is busy with before it can show the setup screen. */
sealed interface Loading {
    data object InProgress : Loading
    data class Failed(val message: String) : Loading
    data class Ready(
        val controller: TrainerController,
        val catalog: SetupCatalog,
        val pet: PetController,
        val background: ImageBitmap?,
    ) : Loading
}

/**
 * Holds everything that outlives a screen: the engine, the training session and the pup.
 * Activities come and go; the session stays.
 */
class TrainerApp : Application() {
    private val scope = MainScope()
    private val _loading = MutableStateFlow<Loading>(Loading.InProgress)
    val loading: StateFlow<Loading> = _loading

    /** The setup screen's last choices, kept while the app runs. */
    var selection = SetupSelection()

    lateinit var engine: AndroidEngine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = AndroidEngine(this, scope)
        scope.launch {
            _loading.value = try {
                val book = withContext(Dispatchers.IO) { OpeningBook.decode(assets.open("book.bin").use { it.readBytes() }) }
                // The background picture is decoration: the app works without it.
                val background = withContext(Dispatchers.IO) {
                    runCatching { assets.open("background.jpg").use(BitmapFactory::decodeStream).asImageBitmap() }.getOrNull()
                }
                val pet = PetController(PrefsPetStore(this@TrainerApp), ::androidMoment)
                Loading.Ready(TrainerController(book, engine, scope), SetupCatalog(book), pet, background)
            } catch (e: Exception) {
                Loading.Failed(e.message ?: e.toString())
            }
        }
    }
}
