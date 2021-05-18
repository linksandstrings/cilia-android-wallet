package com.cilia.wallet.external.partner.model


import com.google.gson.annotations.SerializedName


data class Partner(@SerializedName("title") val title: String,
                   @SerializedName("description") val description: String,
                   @SerializedName("imageUrl") val imageUrl: String,
                   @SerializedName("info") val info: String?,
                   @SerializedName("link") val link: String?,
                   @SerializedName("action") val action: String?) : CommonContent()