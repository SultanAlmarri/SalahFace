package com.salahface.watch.face

import android.view.SurfaceHolder
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.salahface.watch.data.HealthRegistration

class SalahWatchFaceService : WatchFaceService() {
    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        HealthRegistration.register(applicationContext) // no-op until permissions are granted
        val renderer = SalahRenderer(applicationContext, surfaceHolder, watchState, currentUserStyleRepository)
        return WatchFace(WatchFaceType.DIGITAL, renderer)
    }
}
