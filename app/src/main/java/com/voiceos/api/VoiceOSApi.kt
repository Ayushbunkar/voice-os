package com.voiceos.api

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit Interface for connecting to the VoiceOS Cloud backend.
 */
interface VoiceOSApi {

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): LoginResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/v1/device/connect")
    suspend fun connectDevice(@Body request: ConnectDeviceRequest): ConnectResponse

    @POST("api/v1/command")
    suspend fun processTextCommand(@Body request: TextCommandRequest): CommandResponse

    @GET("api/v1/macros")
    suspend fun getMacros(): MacrosResponse

    @Multipart
    @POST("api/v1/commands/audio")
    suspend fun processAudioCommand(@Part audio: MultipartBody.Part): CommandResponse

}
