package com.chaomixian.vflow.core.workflow.module.triggers

import android.os.Parcel
import android.os.Parcelable

data class PoseTriggerData(
    val azimuth: Float,
    val pitch: Float,
    val roll: Float,
    val matchScore: Float,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        azimuth = parcel.readFloat(),
        pitch = parcel.readFloat(),
        roll = parcel.readFloat(),
        matchScore = parcel.readFloat(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeFloat(azimuth)
        parcel.writeFloat(pitch)
        parcel.writeFloat(roll)
        parcel.writeFloat(matchScore)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<PoseTriggerData> {
        override fun createFromParcel(parcel: Parcel): PoseTriggerData = PoseTriggerData(parcel)

        override fun newArray(size: Int): Array<PoseTriggerData?> = arrayOfNulls(size)
    }
}
