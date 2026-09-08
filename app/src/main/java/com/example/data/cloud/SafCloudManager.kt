package com.example.data.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

data class SafCloudDrive(
    val id: String,
    val name: String,
    val uriString: String,
    val providerType: String = "AUTO", // GOOGLE_DRIVE, ONEDRIVE, DROPBOX, NEXTCLOUD, CUSTOM
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val addedTime: Long = System.currentTimeMillis()
)

class SafCloudManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("arcbox_prefs", Context.MODE_PRIVATE)
    private val PREF_SAF_DRIVES = "saf_cloud_drives_list_v2"

    fun getRegisteredDrives(): List<SafCloudDrive> {
        val jsonString = prefs.getString(PREF_SAF_DRIVES, null) ?: return emptyList()
        val list = mutableListOf<SafCloudDrive>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SafCloudDrive(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        uriString = obj.getString("uriString"),
                        providerType = obj.optString("providerType", "AUTO"),
                        totalBytes = obj.optLong("totalBytes", 100L * 1024 * 1024 * 1024),
                        freeBytes = obj.optLong("freeBytes", 75L * 1024 * 1024 * 1024),
                        addedTime = obj.optLong("addedTime", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("SafCloudManager", "Error parsing SAF drives json", e)
        }
        return list
    }

    fun registerSafDrive(treeUri: Uri, customLabel: String? = null): SafCloudDrive? {
        try {
            // Take persistable URI permissions so app can access cloud files across restarts
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (e: Exception) {
                Log.w("SafCloudManager", "Could not take persistable URI permission: ${e.message}")
            }

            val docFile = DocumentFile.fromTreeUri(context, treeUri)
            val detectedName = docFile?.name ?: "Nuvem Real"
            val uriStr = treeUri.toString()
            val lowerUri = uriStr.lowercase()

            val providerType = when {
                lowerUri.contains("com.google.android.apps.docs.storage") || lowerUri.contains("googledrive") -> "GOOGLE_DRIVE"
                lowerUri.contains("com.microsoft.skydrive") || lowerUri.contains("onedrive") -> "ONEDRIVE"
                lowerUri.contains("com.dropbox.android") || lowerUri.contains("dropbox") -> "DROPBOX"
                lowerUri.contains("org.nextcloud") || lowerUri.contains("nextcloud") -> "NEXTCLOUD"
                lowerUri.contains("owncloud") -> "OWNCLOUD"
                lowerUri.contains("box.android") -> "BOX"
                else -> "SAF_CLOUD"
            }

            val providerDisplayName = when (providerType) {
                "GOOGLE_DRIVE" -> if (customLabel.isNullOrBlank()) "Google Drive (Nuvem Real)" else customLabel
                "ONEDRIVE" -> if (customLabel.isNullOrBlank()) "Microsoft OneDrive (Nuvem Real)" else customLabel
                "DROPBOX" -> if (customLabel.isNullOrBlank()) "Dropbox (Nuvem Real)" else customLabel
                "NEXTCLOUD" -> if (customLabel.isNullOrBlank()) "Nextcloud (Nuvem Real)" else customLabel
                "OWNCLOUD" -> if (customLabel.isNullOrBlank()) "ownCloud (Nuvem Real)" else customLabel
                "BOX" -> if (customLabel.isNullOrBlank()) "Box (Nuvem Real)" else customLabel
                else -> if (customLabel.isNullOrBlank()) "$detectedName (Nuvem Real)" else customLabel
            }

            val driveId = "saf_${System.currentTimeMillis()}"
            val newDrive = SafCloudDrive(
                id = driveId,
                name = providerDisplayName,
                uriString = uriStr,
                providerType = providerType,
                totalBytes = 100L * 1024 * 1024 * 1024,
                freeBytes = 75L * 1024 * 1024 * 1024,
                addedTime = System.currentTimeMillis()
            )

            val currentDrives = getRegisteredDrives().filter { it.uriString != uriStr }.toMutableList()
            currentDrives.add(newDrive)
            saveDrives(currentDrives)
            return newDrive
        } catch (e: Exception) {
            Log.e("SafCloudManager", "Failed to register SAF cloud drive", e)
            return null
        }
    }

    fun removeSafDrive(id: String): Boolean {
        try {
            val currentDrives = getRegisteredDrives()
            val driveToRemove = currentDrives.find { it.id == id }
            if (driveToRemove != null) {
                try {
                    val flags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.releasePersistableUriPermission(Uri.parse(driveToRemove.uriString), flags)
                } catch (_: Exception) {}
            }
            val filtered = currentDrives.filter { it.id != id }
            saveDrives(filtered)
            return true
        } catch (e: Exception) {
            Log.e("SafCloudManager", "Failed to remove SAF drive $id", e)
            return false
        }
    }

    private fun saveDrives(drives: List<SafCloudDrive>) {
        val array = JSONArray()
        for (d in drives) {
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("name", d.name)
            obj.put("uriString", d.uriString)
            obj.put("providerType", d.providerType)
            obj.put("totalBytes", d.totalBytes)
            obj.put("freeBytes", d.freeBytes)
            obj.put("addedTime", d.addedTime)
            array.put(obj)
        }
        prefs.edit().putString(PREF_SAF_DRIVES, array.toString()).apply()
    }
}
