package com.kregosh.chessopenings.web

import com.kregosh.chessopenings.core.pet.Moment
import com.kregosh.chessopenings.core.pet.PetStore

// Storage can be unavailable (private browsing, blocked site data): then the pup just isn't remembered.
private fun storageGet(key: String): String? =
    js("(function () { try { return window.localStorage.getItem(key); } catch (e) { return null; } })()")

private fun storageSet(key: String, value: String): Unit =
    js("(function () { try { window.localStorage.setItem(key, value); } catch (e) {} })()")

private fun nowMs(): Double = js("Date.now()")
private fun timezoneOffsetMinutes(): Int = js("new Date().getTimezoneOffset()")
private fun localHour(): Int = js("new Date().getHours()")

/** The pup lives in the browser's local storage, on this device only. */
class LocalStoragePetStore(private val key: String = "chessopenings.pet") : PetStore {
    override fun load(): String? = storageGet(key)
    override fun save(data: String) = storageSet(key, data)
}

/** Now, with the user's local calendar day and hour. */
fun webMoment(): Moment {
    val ms = nowMs().toLong()
    val localMs = ms - timezoneOffsetMinutes() * 60_000L
    return Moment(ms, localMs.floorDiv(86_400_000L), localHour())
}
