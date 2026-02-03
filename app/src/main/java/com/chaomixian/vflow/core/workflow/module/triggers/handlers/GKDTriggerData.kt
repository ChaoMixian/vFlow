package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.os.Parcel
import android.os.Parcelable
import com.chaomixian.vflow.core.types.complex.VScreenElement

/**
 * GKD订阅触发器触发数据
 */
data class GKDTriggerData(
    val element: VScreenElement,
    val allElements: List<VScreenElement> = listOf(element),
    val ruleName: String = "",
    val ruleGroup: String = ""
) : Parcelable {
    override fun toString(): String = "GKDTriggerData(rule=$ruleName, element=${element.asString()})"

    companion object CREATOR : Parcelable.Creator<GKDTriggerData> {
        @Suppress("DEPRECATION")
        override fun createFromParcel(parcel: Parcel): GKDTriggerData {
            val element = parcel.readParcelable<VScreenElement>(VScreenElement::class.java.classLoader)!!
            val allElements = mutableListOf<VScreenElement>()
            parcel.readTypedList(allElements, VScreenElement.CREATOR)
            return GKDTriggerData(
                element = element,
                allElements = allElements,
                ruleName = parcel.readString() ?: "",
                ruleGroup = parcel.readString() ?: ""
            )
        }

        override fun newArray(size: Int): Array<GKDTriggerData?> {
            return arrayOfNulls<GKDTriggerData>(size)
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(element, flags)
        parcel.writeTypedList(allElements)
        parcel.writeString(ruleName)
        parcel.writeString(ruleGroup)
    }

    override fun describeContents(): Int = 0
}
