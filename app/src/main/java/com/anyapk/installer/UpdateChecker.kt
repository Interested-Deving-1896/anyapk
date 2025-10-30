package com.anyapk.installer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks for app updates from GitHub releases
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"

    // TODO: Replace with your actual GitHub repo (username/repo-name)
    private const val GITHUB_REPO = "sam1am/anyapk"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
        val releaseNotes: String,
        val publishedAt: String
    )

    /**
     * Checks if a new version is available on GitHub
     * @return UpdateInfo if an update is available, null otherwise
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val currentVersionCode = getCurrentVersionCode(context)
            val currentVersionName = getCurrentVersionName(context)

            Log.d(TAG, "Checking for updates. Current version: $currentVersionName ($currentVersionCode)")

            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Failed to check for updates. Response code: $responseCode")
                return@withContext null
            }

            val response = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }

            val json = JSONObject(response)
            val tagName = json.getString("tag_name").removePrefix("v")
            val latestVersionName = tagName

            // Extract version code from tag name or release body
            // Assuming tag format: v0.0.5 or similar
            val latestVersionCode = extractVersionCode(latestVersionName)

            Log.d(TAG, "Latest version on GitHub: $latestVersionName ($latestVersionCode)")

            // Find the APK download URL from assets
            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }

            if (apkUrl == null) {
                Log.e(TAG, "No APK file found in latest release")
                return@withContext null
            }

            // Check if update is available
            if (latestVersionCode > currentVersionCode) {
                val releaseNotes = json.optString("body", "No release notes available")
                val publishedAt = json.optString("published_at", "")

                Log.d(TAG, "Update available! $currentVersionName -> $latestVersionName")

                return@withContext UpdateInfo(
                    versionName = latestVersionName,
                    versionCode = latestVersionCode,
                    downloadUrl = apkUrl,
                    releaseNotes = releaseNotes,
                    publishedAt = publishedAt
                )
            } else {
                Log.d(TAG, "App is up to date")
                return@withContext null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            return@withContext null
        }
    }

    private fun getCurrentVersionCode(context: Context): Int {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    }

    private fun getCurrentVersionName(context: Context): String {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }

    /**
     * Extract version code from version name
     * Example: "0.0.5" -> 5, "1.2.3" -> 10203
     */
    private fun extractVersionCode(versionName: String): Int {
        return try {
            val parts = versionName.split(".")
            when (parts.size) {
                // Simple format: 0.0.5 -> use last number
                3 -> {
                    val major = parts[0].toIntOrNull() ?: 0
                    val minor = parts[1].toIntOrNull() ?: 0
                    val patch = parts[2].toIntOrNull() ?: 0

                    // If using simple incrementing (like current: v5 = 0.0.5)
                    if (major == 0 && minor == 0) {
                        patch
                    } else {
                        // Semantic versioning: 1.2.3 -> 10203
                        major * 10000 + minor * 100 + patch
                    }
                }
                else -> {
                    // Fallback: try to extract any number
                    versionName.filter { it.isDigit() }.toIntOrNull() ?: 0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing version code from $versionName", e)
            0
        }
    }
}
