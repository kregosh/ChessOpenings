package com.kregosh.chessopenings.android

import android.content.Context
import androidx.core.content.edit
import com.kregosh.chessopenings.core.pet.Moment
import com.kregosh.chessopenings.core.pet.PetStore
import java.time.ZonedDateTime

/** The pup lives in the app's SharedPreferences. */
class PrefsPetStore(context: Context) : PetStore {
    private val prefs = context.getSharedPreferences("pet", Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString(KEY, null)

    override fun save(data: String) = prefs.edit { putString(KEY, data) }

    private companion object {
        const val KEY = "state"
    }
}

/** Now, with the user's local calendar day and hour. */
fun androidMoment(): Moment {
    val now = ZonedDateTime.now()
    return Moment(now.toInstant().toEpochMilli(), now.toLocalDate().toEpochDay(), now.hour)
}
