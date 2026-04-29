package com.chaomixian.vflow.core.workflow.module.triggers

import android.os.Parcel
import android.os.Parcelable

data class VoiceTriggerData(
    val similarity: Float,
    val segmentDurationMs: Long,
    val hitCount: Int,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        similarity = parcel.readFloat(),
        segmentDurationMs = parcel.readLong(),
        hitCount = parcel.readInt(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeFloat(similarity)
        parcel.writeLong(segmentDurationMs)
        parcel.writeInt(hitCount)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<VoiceTriggerData> {
        override fun createFromParcel(parcel: Parcel): VoiceTriggerData = VoiceTriggerData(parcel)

        override fun newArray(size: Int): Array<VoiceTriggerData?> = arrayOfNulls(size)
    }
}
