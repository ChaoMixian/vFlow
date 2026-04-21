package com.chaomixian.vflow.core.workflow.module.triggers

import android.text.Editable
import android.text.TextWatcher

class SimpleTextWatcher(
    private val onAfterTextChanged: (Editable?) -> Unit
) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(s: Editable?) {
        onAfterTextChanged(s)
    }
}
