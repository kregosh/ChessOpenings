package com.kregosh.chessopenings.core.pet

import com.kregosh.chessopenings.core.training.Report
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the screens show of the pup. */
data class PetView(
    val state: PetState,
    val mood: Mood,
    /** The streak as it stands now (0 once a day was missed). */
    val streak: Int,
    val fedToday: Boolean,
    /** What the last finished session did, for the report's feeding moment. */
    val lastTreat: Treat? = null,
)

/** Keeps the pup: loads it from [store], applies the rules, and saves every change. */
class PetController(private val store: PetStore, private val clock: () -> Moment) {
    private var pet: PetState = store.load()?.let(PetCodec::decode) ?: PetState()
    private var lastTreat: Treat? = null
    private val _view = MutableStateFlow(view())
    val view: StateFlow<PetView> = _view.asStateFlow()

    /** Brings the needs and mood up to date; call now and then while the app is open. */
    fun refresh() = update(PetRules.elapse(pet, clock()))

    fun sessionFinished(report: Report): Treat {
        val (fed, treat) = PetRules.feed(pet, report, clock())
        lastTreat = treat
        update(fed)
        return treat
    }

    fun pat() = update(PetRules.pat(pet, clock()))

    private fun update(new: PetState) {
        pet = new
        store.save(PetCodec.encode(new))
        _view.value = view()
    }

    private fun view(): PetView {
        val now = clock()
        return PetView(PetRules.elapse(pet, now), PetRules.mood(pet, now), PetRules.streak(pet, now), PetRules.fedToday(pet, now), lastTreat)
    }
}
