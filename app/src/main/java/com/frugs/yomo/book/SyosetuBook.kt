package com.frugs.yomo.book

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkRequest
import android.net.Uri
import android.os.PersistableBundle
import androidx.annotation.OptIn
import com.frugs.yomo.BookyApp
import com.frugs.yomo.syosetu.SyosetuDownloadJobService
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.util.Collections

class SyosetuBook(context: Context?) : Book(context) {

  companion object {
    private val META_PREFIX = "meta."
    private val ORDERCOUNT = "ordercount"
    private val BOOK_CONTENT_DIR = "bookContentDir"
    private val ORDER = "order."
    private val ITEM = "item."
    private val TOCCOUNT = "toccount"
    private val TOC_LABEL = "toc.label."
    private val TOC_CONTENT = "toc.content."
    private val TOC = "toc"

    private fun getOrderFileName(order: Int): String {
      return "${order}.html"
    }
  }

  private val docFileOrder: MutableList<String> = ArrayList()

  private val ncode: String
    get() {
      val fileName = file.name

      if (fileName.isEmpty()) {
        return ""
      }

      val split = fileName.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      if (split.isEmpty()) {
        return ""
      }

      return split[0]
    }

  @Throws(IOException::class)
  override fun load() {
    runBlocking {
      val syosetuService = BookyApp.getSyosetuService(context)

      if (!sharedPreferences.contains(ORDERCOUNT)) {
        val details = syosetuService.getDetails(ncode)
        val pages = details?.pages ?: 1
        for (i in 1..pages) {
          val jobInfo = JobInfo.Builder(
              i,
              ComponentName(context, SyosetuDownloadJobService::class.java))
            .setUserInitiated(true)
            .setRequiredNetwork(
                NetworkRequest.Builder()
                  .addCapability(NET_CAPABILITY_INTERNET)
                  .build())
            .setEstimatedNetworkBytes(1024 * 1024 * 1024, 1024 * 1024 * 1024)
            .setExtras(PersistableBundle().apply {
              putString(SyosetuDownloadJobService.KEY_NCODE, ncode)
              putString(SyosetuDownloadJobService.KEY_BOOK_URI, thisBookDir.toURI().toString())
            })
            .build()

          val jobScheduler: JobScheduler =
              context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
          jobScheduler.schedule(jobInfo)
        }

        val bookData = sharedPreferences.edit()
        bookData.putInt(ORDERCOUNT, pages)
        bookData.apply()
      }

      val pages = sharedPreferences.getInt(ORDERCOUNT, 1)
      for (i in 1..pages) {
        docFileOrder.add(getOrderFileName(i))
      }
    }
  }

  override fun getToc(): Map<String, String>? {
    return null
  }

  @Throws(IOException::class)
  override fun getMetaData(): BookMetadata? {
    return null
  }

  override fun getSectionIds(): List<String>? {
    return Collections.unmodifiableList(docFileOrder)
  }

  override fun getUriForSectionID(id: String): Uri? {
    return Uri.fromFile(File(thisBookDir, id))
  }

  override fun locateReadPoint(section: String): ReadPoint? {
    return null
  }
}
