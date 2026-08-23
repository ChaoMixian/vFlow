package com.chaomixian.vflow.core.system

import android.net.Uri
import android.service.notification.ConditionProviderService

class DndConditionProviderService : ConditionProviderService() {
    override fun onConnected() {
        // Called when the system binds to the provider
    }

    override fun onSubscribe(conditionId: Uri?) {
        // System is listening for updates on this conditionId
    }

    override fun onUnsubscribe(conditionId: Uri?) {
        // System stopped listening
    }
}
