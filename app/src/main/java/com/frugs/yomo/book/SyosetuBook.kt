package com.frugs.yomo.book

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkRequest
import android.net.Uri
import android.os.PersistableBundle
import com.frugs.yomo.syosetu.SyosetuDownloadJobService
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.util.Collections

class SyosetuBook(context: Context?) : Book(context) {

  companion object {
    private val META_PREFIX = "meta."
    val KEY_PAGE_COUNT = "ordercount"
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
    if (!sharedPreferences.contains(KEY_PAGE_COUNT)) {
      val blockingJob = Job()
      val listener = object : OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
          if (key == KEY_PAGE_COUNT) {
            blockingJob.complete()
            sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
          }
        }
      }
      sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

      val jobInfo = JobInfo.Builder(
          ncode.hashCode(),
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
          putString(SyosetuDownloadJobService.KEY_BOOK_DATA, dataFileName)
        })
        .build()

      val jobScheduler: JobScheduler =
          context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
      jobScheduler.schedule(jobInfo)

      runBlocking {
        blockingJob.join()
      }
    }

    val pages = sharedPreferences.getInt(KEY_PAGE_COUNT, 1)
    for (i in 1..pages) {
      docFileOrder.add(getOrderFileName(i))
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
