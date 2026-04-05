package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import com.chaomixian.vflow.core.workflow.model.TriggerSpec

interface ITriggerHandler {
    fun start(context: Context)

    fun stop(context: Context)

    fun addTrigger(context: Context, trigger: TriggerSpec)

    fun removeTrigger(context: Context, triggerId: String)
}
