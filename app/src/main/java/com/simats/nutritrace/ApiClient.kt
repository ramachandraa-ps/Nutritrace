package com.simats.nutritrace

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import android.graphics.BitmapFactory
import android.widget.ImageView
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {
    // CHANGE THIS to your computer's local IP address (run ipconfig in terminal)
    private const val BASE_URL = "http://10.161.158.6:5000"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Longer timeout for image upload + OCR + AI analysis (can take 20-30 seconds)
    private val uploadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences("NutriTracePrefs", Context.MODE_PRIVATE)
        return prefs.getString("AUTH_TOKEN", null)
    }

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences("NutriTracePrefs", Context.MODE_PRIVATE)
            .edit().putString("AUTH_TOKEN", token).apply()
    }

    fun saveUserInfo(context: Context, id: Int, fullname: String, email: String, phone: String) {
        context.getSharedPreferences("NutriTracePrefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("USER_ID", id)
            .putString("USER_FULLNAME", fullname)
            .putString("USER_EMAIL", email)
            .putString("USER_PHONE", phone)
            .apply()
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences("NutriTracePrefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
        context.getSharedPreferences("NutriTraceCache", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    fun cacheData(context: Context, key: String, json: String) {
        context.getSharedPreferences("NutriTraceCache", Context.MODE_PRIVATE)
            .edit().putString(key, json).apply()
    }

    fun getCachedData(context: Context, key: String): JsonObject? {
        val cached = context.getSharedPreferences("NutriTraceCache", Context.MODE_PRIVATE)
            .getString(key, null) ?: return null
        return try { Gson().fromJson(cached, JsonObject::class.java) } catch (e: Exception) { null }
    }

    fun getImageUrl(imagePath: String?): String? {
        if (imagePath.isNullOrBlank()) return null
        return "$BASE_URL/uploads/$imagePath"
    }

    fun loadImage(imageView: ImageView, imagePath: String?) {
        val url = getImageUrl(imagePath) ?: return
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val bytes = response.body?.bytes() ?: return
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
                imageView.post {
                    imageView.setPadding(0, 0, 0, 0)
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    imageView.setImageBitmap(bitmap)
                }
            }
        })
    }

    // POST JSON (no auth)
    fun post(endpoint: String, body: Map<String, Any>, callback: (Boolean, JsonObject?) -> Unit) {
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody(JSON_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL$endpoint")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val jsonObj = if (responseBody != null) gson.fromJson(responseBody, JsonObject::class.java) else null
                callback(response.isSuccessful, jsonObj)
            }
        })
    }

    // POST JSON (with auth)
    fun postAuth(context: Context, endpoint: String, body: Map<String, Any>, callback: (Boolean, JsonObject?) -> Unit) {
        val token = getToken(context) ?: run {
            callback(false, null)
            return
        }
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody(JSON_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL$endpoint")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val jsonObj = if (responseBody != null) gson.fromJson(responseBody, JsonObject::class.java) else null
                callback(response.isSuccessful, jsonObj)
            }
        })
    }

    // GET (with auth)
    fun getAuth(context: Context, endpoint: String, callback: (Boolean, JsonObject?) -> Unit) {
        val token = getToken(context) ?: run {
            callback(false, null)
            return
        }
        val request = Request.Builder()
            .url("$BASE_URL$endpoint")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val jsonObj = if (responseBody != null) gson.fromJson(responseBody, JsonObject::class.java) else null
                callback(response.isSuccessful, jsonObj)
            }
        })
    }

    // PUT JSON (with auth)
    fun putAuth(context: Context, endpoint: String, body: Map<String, Any>, callback: (Boolean, JsonObject?) -> Unit) {
        val token = getToken(context) ?: run {
            callback(false, null)
            return
        }
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody(JSON_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL$endpoint")
            .addHeader("Authorization", "Bearer $token")
            .put(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val jsonObj = if (responseBody != null) gson.fromJson(responseBody, JsonObject::class.java) else null
                callback(response.isSuccessful, jsonObj)
            }
        })
    }

    // DELETE (with auth, optional JSON body)
    fun deleteAuth(context: Context, endpoint: String, body: Map<String, Any>? = null, callback: (Boolean, JsonObject?) -> Unit) {
        val token = getToken(context) ?: run {
            callback(false, null)
            return
        }
        val builder = Request.Builder()
            .url("$BASE_URL$endpoint")
            .addHeader("Authorization", "Bearer $token")

        if (body != null) {
            val json = gson.toJson(body)
            builder.delete(json.toRequestBody(JSON_TYPE))
        } else {
            builder.delete()
        }

        client.newCall(builder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val jsonObj = if (responseBody != null) gson.fromJson(responseBody, JsonObject::class.java) else null
                callback(response.isSuccessful, jsonObj)
            }
        })
    }

    // Multipart image upload (with auth)
    fun uploadImage(context: Context, endpoint: String, imageFile: File, extraFields: Map<String, String> = emptyMap(), callback: (Boolean, JsonObject?) -> Unit) {
        val token = getToken(context) ?: run {
            callback(false, null)
            return
        }

        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        bodyBuilder.addFormDataPart(
            "image", imageFile.name,
            RequestBody.create("image/*".toMediaType(), imageFile)
        )
        for ((key, value) in extraFields) {
            bodyBuilder.addFormDataPart(key, value)
        }

        val request = Request.Builder()
            .url("$BASE_URL$endpoint")
            .addHeader("Authorization", "Bearer $token")
            .post(bodyBuilder.build())
            .build()

        uploadClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val jsonObj = if (responseBody != null) gson.fromJson(responseBody, JsonObject::class.java) else null
                callback(response.isSuccessful, jsonObj)
            }
        })
    }
}
