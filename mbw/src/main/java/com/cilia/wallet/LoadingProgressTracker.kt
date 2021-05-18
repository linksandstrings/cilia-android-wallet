package com.cilia.wallet

import android.content.Context
import android.os.Handler
import com.cilia.wallet.event.MigrationStatusChanged
import com.cilia.wallet.event.MigrationPercentChanged
import com.cilia.wallet.persistence.MetadataStorage
import com.mycelium.wapi.wallet.LoadingProgressStatus
import com.mycelium.wapi.wallet.LoadingProgressUpdater

class LoadingProgressTracker(val context: Context) : LoadingProgressUpdater {
    val eventBus = MbwManager.getEventBus()!!

    override var status: LoadingProgressStatus = LoadingProgressStatus.Starting()
        set(value) {
            Handler(context.mainLooper).post {
                eventBus.post(MigrationStatusChanged(value))
            }
            field = value
        }
    override var percent = 0
        set(value) {
            Handler(context.mainLooper).post {
                eventBus.post(MigrationPercentChanged(value))
            }
            field = value
        }


    override fun clearLastFullUpdateTime() {
        MetadataStorage.setLastFullSync(0)
    }
}
