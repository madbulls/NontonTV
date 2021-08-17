package net.harimurti.tv.extra

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.google.gson.Gson
import net.harimurti.tv.R
import net.harimurti.tv.model.*
import java.io.*

class PlaylistHelper(val context: Context) {
    private val cache: File = File(context.cacheDir, "NontonTV.json")
    private var taskResponse: TaskResponse? = null
    private var taskChecker: TaskChecker? = null
    private var sources: ArrayList<Source> = ArrayList()
    private var checkSource: Source? = null
    private val volley = VolleyRequestQueue.create(context)

    companion object {
        private const val TAG = "PlaylistHelper"
    }

    interface TaskResponse {
        fun onResponse(playlist: Playlist?) {}
        fun onError(error: Exception, source: Source) {}
        fun onFinish() {}
    }

    interface TaskChecker {
        fun onCheckResult(result: Boolean) {}
    }

    fun writeCache(playlist: Playlist) {
        val content = Gson().toJson(playlist)
        try {
            val fw = FileWriter(cache.absoluteFile)
            val bw = BufferedWriter(fw)
            bw.write(content)
            bw.close()
        } catch (e: Exception) {
            Log.e(TAG, String.format("Could not write %s", cache.name), e)
        }
    }

    fun readFile(file: File): Playlist? {
        return try {
            if (!file.exists()) throw FileNotFoundException()
            FileReader(file.absoluteFile).readText().toPlaylist()
        } catch (e: Exception) {
            if (file == cache) e.printStackTrace()
            else Log.e(TAG, String.format("Could not read %s", file.name), e)
            null
        }
    }

    fun readCache(): Playlist? {
        return readFile(cache)
    }

    fun task(sources: ArrayList<Source>?, taskResponse: TaskResponse?): PlaylistHelper {
        sources?.let { this.sources.addAll(it) }
        this.taskResponse = taskResponse
        return this
    }

    fun task(source: Source, taskChecker: TaskChecker?): PlaylistHelper {
        checkSource = source
        this.taskChecker = taskChecker
        return this
    }

    fun getResponse() {
        // send onFinish when sources is empty
        if (sources.isEmpty()) {
            taskResponse?.onFinish()
            return
        }

        // get first source
        val source = sources.first()
        sources.remove(source)
        if (!source.active) {
            getResponse()
            return
        }

        // local playlist
        if (source.path?.startsWith("http", ignoreCase = true) == false) {
            val playlist = readFile(File(source.path.toString()))
            taskResponse?.onResponse(playlist)
            getResponse()
            return
        }

        // online playlist
        val stringRequest = StringRequest(
            Request.Method.GET, source.path,
            { content ->
                taskResponse?.onResponse(content.toPlaylist())
                getResponse()
            },
            { error ->
                var message = "[UNKNOWN] : ${source.path}"
                if (error.networkResponse != null) {
                    val errorcode = error.networkResponse.statusCode
                    message = "[HTTP_$errorcode] : ${source.path}"
                } else if (!Network(context).isConnected()) {
                    message = context.getString(R.string.no_network)
                }
                Log.e(TAG, "Source : ${source.path}", error)
                taskResponse?.onError(Exception(message), source)
                getResponse()
            })
        volley.add(stringRequest)
    }

    fun checkResult() {
        var result = false
        if (checkSource?.path?.startsWith("http", ignoreCase = true) == false) {
            result = File(checkSource?.path.toString()).exists()
            taskChecker?.onCheckResult(result)
        }

        val stringRequest = StringRequest(
            Request.Method.GET, checkSource?.path.toString(),
            { content ->
                val pls = content.toPlaylist()
                result = pls != null && pls.categories.isNotEmpty()
                taskChecker?.onCheckResult(result)
            },
            { error ->
                var message = "[UNKNOWN] : ${checkSource?.path}"
                if (error.networkResponse != null) {
                    val errorcode = error.networkResponse.statusCode
                    message = "[HTTP_$errorcode] : ${checkSource?.path}"
                } else if (!Network(context).isConnected()) {
                    message = context.getString(R.string.no_network)
                }
                Log.e(TAG, message, error)
                taskChecker?.onCheckResult(result)
            })
        volley.add(stringRequest)
    }
}

