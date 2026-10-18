package com.calcrux.data

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Path

/** ExchangeRate-API open endpoint: ~160 currencies (incl. TWD), refreshed once a day. */
data class FxResponse(
    val result: String,
    @Json(name = "base_code") val baseCode: String,
    @Json(name = "time_last_update_unix") val lastUpdateUnix: Long,
    val rates: Map<String, Double>,
)

interface FxService {
    /** Fetch latest rates for [base]. */
    @GET("v6/latest/{base}")
    suspend fun latest(@Path("base") base: String = "USD"): FxResponse
}
