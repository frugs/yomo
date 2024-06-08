package com.frugs.yomo.syosetu

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.RequestFuture
import com.android.volley.toolbox.Volley
import com.frugs.yomo.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.regex.Pattern

class SyosetuService(context: Context) {
    companion object {
        private val pattern = Pattern.compile("https://ncode.syosetu.com/(.*)/.*")

        fun getNcode(url: String): String? {

            val matcher = pattern.matcher(url)
            if (!matcher.matches() || matcher.groupCount() < 1) {
                return null
            }

            return matcher.group(1)
        }
    }

    private val queue = Volley.newRequestQueue(context)
    private val dateFormat = SimpleDateFormat("yyyy-mm-dd HH:mm:ss")

    suspend fun getDetails(ncode: String): SyosetuDetails? {
        val url = "https://api.syosetu.com/novelapi/api/?out=json&ncode=$ncode"
        val future = RequestFuture.newFuture<JSONArray>()
        val request = JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                future,
                future)
        queue.add(request)

        return withContext(Dispatchers.IO) {
            val response = future.get()
            parseSyosetuDetails(response)
        }
    }

    suspend fun getText(ncode: String, page: Int): String {
        return withContext(Dispatchers.IO) {
            val doc = Jsoup.connect("https://ncode.syosetu.com/$ncode/$page/").get()
            val element = doc.selectFirst("div#novel_honbun.novel_view") ?: return@withContext ""

            return@withContext element.html()
        }
    }


    @SuppressLint("SimpleDateFormat")
    private fun parseSyosetuDetails(response: JSONArray?): SyosetuDetails? {
        if (response == null || response.length() < 2) {
            return null
        }

        val jsonObject = response.get(1) as JSONObject?

        if (jsonObject == null) {
            return null
        }

        try {
            val ncode =  jsonObject.getString("ncode")
            val author = jsonObject.getString("writer")
            val title = jsonObject.getString("title")
            val synopsis = jsonObject.getString("story")
            val pages = jsonObject.getInt("general_all_no")
            val lastUpdated = jsonObject.getString("general_lastup")

            val lastUpdatedDateTime = dateFormat.parse(lastUpdated)

            return SyosetuDetails(ncode, author, title, synopsis, pages, lastUpdatedDateTime)
        } catch (e: ParseException) {
            Log.e(TAG, "Failed to parse syosetu", e)
            return null
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unexpected error parsing syosetu", e)
            return null
        }
    }
}

