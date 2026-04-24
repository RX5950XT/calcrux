package com.calcrux.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class FxResponse(
    val base: String,
    val date: String,
    val rates: Map<String, Double>,
)

interface FxService {
    /** Fetch latest rates with USD as base. */
    @GET("latest")
    suspend fun latest(@Query("from") base: String = "USD"): FxResponse
}
