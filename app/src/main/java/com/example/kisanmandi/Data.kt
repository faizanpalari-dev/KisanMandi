package com.example.kisanmandi

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

// ---------- Models (same fields as the database structure in the task) ----------

data class User(
    val id: String? = null,
    val name: String,
    val phone: String,
    val district: String,
    val role: String // "Farmer" or "Buyer"
)

data class Crop(val id: String, val name: String, val category: String)

data class Market(val id: String, val name: String, val district: String)

data class Price(
    val id: String,
    val crop_id: String,
    val market_id: String,
    val date: String, // yyyy-MM-dd
    val price: Double
)

data class Listing(
    val id: String? = null,
    val user_id: String,
    val crop_id: String,
    val quantity: Double,
    val asking_price: Double,
    val district: String,
    val grade: String, // A, B, C
    val available_from: String, // yyyy-MM-dd
    val status: String, // Active or Sold
    val phone: String? = null,
    val created_at: String? = null
)

data class Alert(
    val id: String? = null,
    val user_id: String,
    val crop_id: String,
    val market_id: String,
    val condition: String, // Above or Below
    val target_price: Double,
    val active_status: Boolean
)

// ---------- Mock API (JSON Server) ----------

interface Api {
    @GET("users") suspend fun users(): List<User>
    @POST("users") suspend fun addUser(@Body u: User): User

    @GET("crops") suspend fun crops(): List<Crop>
    @GET("markets") suspend fun markets(): List<Market>
    @GET("prices") suspend fun prices(): List<Price>

    @GET("listings") suspend fun listings(): List<Listing>
    @POST("listings") suspend fun addListing(@Body l: Listing): Listing
    @PUT("listings/{id}") suspend fun updateListing(@Path("id") id: String, @Body l: Listing): Listing
    @DELETE("listings/{id}") suspend fun deleteListing(@Path("id") id: String): Response<Unit>

    @GET("alerts") suspend fun alerts(): List<Alert>
    @POST("alerts") suspend fun addAlert(@Body a: Alert): Alert
    @PUT("alerts/{id}") suspend fun updateAlert(@Path("id") id: String, @Body a: Alert): Alert
    @DELETE("alerts/{id}") suspend fun deleteAlert(@Path("id") id: String): Response<Unit>
}

object Net {
    // Emulator: keep 10.0.2.2
    // Real phone: change to your laptop's Wi-Fi IP, e.g. "http://192.168.1.5:3000/"
    const val BASE_URL = "http://127.0.0.1:3000/"

    val api: Api by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api::class.java)
    }
}
