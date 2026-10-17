package com.sukot.vpn.data

import com.google.gson.annotations.SerializedName

/**
 * User-adjustable tunnel behaviour.
 */
data class SukotSettings(
    @SerializedName("bypassIran") val bypassIran: Boolean = true,
    @SerializedName("blockQuic") val blockQuic: Boolean = false,
    @SerializedName("blockAds") val blockAds: Boolean = false,
    @SerializedName("blockTorrent") val blockTorrent: Boolean = false,
    @SerializedName("mtu") val mtu: Int = 8500
)
