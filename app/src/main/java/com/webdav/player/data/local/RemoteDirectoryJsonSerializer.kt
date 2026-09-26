package com.webdav.player.data.local

import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import org.json.JSONArray
import org.json.JSONObject

object RemoteDirectoryJsonSerializer {

    fun serialize(directory: RemoteDirectory): String {
        val root = JSONObject()
        root.put("path", directory.path)
        root.put("name", directory.name)

        val subDirs = JSONArray()
        for (sub in directory.subDirectories) {
            val sObj = JSONObject()
            sObj.put("path", sub.path)
            sObj.put("name", sub.name)
            subDirs.put(sObj)
        }
        root.put("subDirectories", subDirs)

        val filesArray = JSONArray()
        for (file in directory.files) {
            val fObj = JSONObject()
            fObj.put("name", file.name)
            fObj.put("path", file.path)
            fObj.put("size", file.size)
            if (file.lastModified != null) fObj.put("lastModified", file.lastModified)
            if (file.contentType != null) fObj.put("contentType", file.contentType)
            filesArray.put(fObj)
        }
        root.put("files", filesArray)

        return root.toString()
    }

    fun deserialize(json: String): RemoteDirectory? {
        return try {
            val root = JSONObject(json)
            val path = root.getString("path")
            val name = root.getString("name")

            val subDirsList = mutableListOf<RemoteDirectory>()
            val subDirs = root.optJSONArray("subDirectories")
            if (subDirs != null) {
                for (i in 0 until subDirs.length()) {
                    val sObj = subDirs.getJSONObject(i)
                    subDirsList.add(
                        RemoteDirectory(
                            path = sObj.getString("path"),
                            name = sObj.getString("name")
                        )
                    )
                }
            }

            val filesList = mutableListOf<RemoteFile>()
            val filesArray = root.optJSONArray("files")
            if (filesArray != null) {
                for (i in 0 until filesArray.length()) {
                    val fObj = filesArray.getJSONObject(i)
                    filesList.add(
                        RemoteFile(
                            name = fObj.getString("name"),
                            path = fObj.getString("path"),
                            size = fObj.optLong("size", 0L),
                            lastModified = if (fObj.has("lastModified") && !fObj.isNull("lastModified")) fObj.getString("lastModified") else null,
                            contentType = if (fObj.has("contentType") && !fObj.isNull("contentType")) fObj.getString("contentType") else null
                        )
                    )
                }
            }

            RemoteDirectory(
                path = path,
                name = name,
                subDirectories = subDirsList,
                files = filesList
            )
        } catch (e: Exception) {
            null
        }
    }
}
