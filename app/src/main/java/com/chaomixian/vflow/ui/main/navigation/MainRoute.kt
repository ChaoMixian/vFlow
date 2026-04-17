package com.chaomixian.vflow.ui.main.navigation

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface MainRoute : NavKey, Parcelable {
    @Parcelize
    @Serializable
    data object Main : MainRoute
}
