package com.cabin.app.data

import android.content.Context
import com.cabin.app.BuildConfig
import com.cabin.app.data.remote.Network

/** Minimal manual dependency container (no DI framework needed for an MVP). */
object ServiceLocator {
    lateinit var repository: CabinRepository
        private set

    fun init(context: Context) {
        val session = SessionStore(context.applicationContext)
        val api = Network.createApi(BuildConfig.API_BASE_URL, session)
        repository = CabinRepository(api, session)
    }
}
