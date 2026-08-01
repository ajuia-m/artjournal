package com.ajuia.artjournal

import android.app.Application

class ArtJournalApplication : Application() {
    val container: AppContainer by lazy {
        DefaultAppContainer(this)
    }
}
