package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DriveFileMeta(
    val id: String,
    val name: String,
    val modifiedTime: String,
    val sizeBytes: Long
)

class GoogleDriveSyncService {

    companion object {
        const val DRIVE_SYNC_JSON_NAME = "AA_Contacts_Sync.json.gz"
        const val DRIVE_SYNC_VCF_NAME = "AA_Contacts_Backup.vcf"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun findSyncFile(accessToken: String, fileName: String): DriveFileMeta? = withContext(Dispatchers.IO) {
        val query = "name = '$fileName' and trashed = false"
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQ&spaces=drive&fields=files(id,name,modifiedTime,size)&orderBy=modifiedTime%20desc&pageSize=1"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Google Drive (${response.code}): $bodyStr")
            }
            val json = JSONObject(bodyStr)
            val files = json.optJSONArray("files") ?: return@withContext null
            if (files.length() == 0) return@withContext null
            val first = files.getJSONObject(0)
            DriveFileMeta(
                id = first.getString("id"),
                name = first.getString("name"),
                modifiedTime = first.optString("modifiedTime", ""),
                sizeBytes = first.optString("size", "0").toLongOrNull() ?: 0L
            )
        }
    }

    suspend fun uploadOrUpdateFile(
        accessToken: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        existingFileId: String?
    ): DriveFileMeta = withContext(Dispatchers.IO) {
        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("mimeType", mimeType)
        }.toString()

        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addPart(bytes.toRequestBody(mimeType.toMediaType()))
            .build()

        val url = if (existingFileId != null) {
            "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=multipart&fields=id,name,modifiedTime,size"
        } else {
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,modifiedTime,size"
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
        if (existingFileId != null) {
            reqBuilder.patch(multipartBody)
        } else {
            reqBuilder.post(multipartBody)
        }

        client.newCall(reqBuilder.build()).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Envoi Drive échoué (${response.code}): $bodyStr")
            }
            val obj = JSONObject(bodyStr)
            DriveFileMeta(
                id = obj.getString("id"),
                name = obj.optString("name", fileName),
                modifiedTime = obj.optString("modifiedTime", ""),
                sizeBytes = obj.optString("size", bytes.size.toString()).toLongOrNull() ?: bytes.size.toLong()
            )
        }
    }

    suspend fun downloadFileBytes(accessToken: String, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string().orEmpty()
                throw IOException("Téléchargement Drive échoué (${response.code}): $err")
            }
            response.body?.bytes() ?: ByteArray(0)
        }
    }
}
