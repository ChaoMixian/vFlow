// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/LocationTriggerData.kt
// 描述: 位置触发器传递的数据
package com.chaomixian.vflow.core.workflow.module.triggers

import android.os.Parcel
import android.os.Parcelable

data class LocationTriggerData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readDouble(),
        parcel.readDouble(),
        parcel.readFloat()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeDouble(latitude)
        parcel.writeDouble(longitude)
        parcel.writeFloat(accuracy)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<LocationTriggerData> {
        override fun createFromParcel(parcel: Parcel): LocationTriggerData {
            return LocationTriggerData(parcel)
        }

        override fun newArray(size: Int): Array<LocationTriggerData?> {
            return arrayOfNulls(size)
        }
    }
}
