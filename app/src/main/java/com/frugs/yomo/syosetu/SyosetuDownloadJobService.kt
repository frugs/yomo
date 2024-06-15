package com.frugs.yomo.syosetu

import android.app.Notification
import android.app.job.JobParameters
import android.app.job.JobService
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.frugs.yomo.BookyApp
import com.frugs.yomo.R
import com.frugs.yomo.book.SyosetuBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.File

class SyosetuDownloadJobService : JobService() {

  private val supervisorJob = SupervisorJob()
  private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)

  private var nextNotificationId = FOREGROUND_NOTIFICATION_ID + 1
  private val notificationIdsCache = mutableMapOf<String, Int>()

  private val jobs = mutableMapOf<String, Job>()

  override fun onStartJob(params: JobParameters?): Boolean {
    if (params == null) {
      return false
    }

    val ncode = params.extras.getString(KEY_NCODE, "")
    if (ncode.isEmpty()) {
      return false
    }

    val bookData = when (val bookDataFileName = params.extras.getString(KEY_BOOK_DATA, "")) {
      "" -> return false
      else -> getSharedPreferences(bookDataFileName, MODE_PRIVATE)
    }

    val bookDir = when (val bookUriString = params.extras.getString(KEY_BOOK_URI, "")) {
      "" -> return false
      else -> bookUriString.toUri().toFile()
    }

    jobs[ncode]?.cancel()

    val notificationId = when (ncode) {
      "" -> nextNotificationId++
      else -> notificationIdsCache.computeIfAbsent(ncode) { nextNotificationId++ }
    }
    setNotification(
        params,
        notificationId,
        Notification.Builder(applicationContext, CHANNEL_ID)
          .setSmallIcon(R.drawable.ic_stat_file_download)
          .setContentTitle("Downloading novel")
          .build(),
        JOB_END_NOTIFICATION_POLICY_DETACH)

    val job = scope.launch {
      val syosetuService = BookyApp.getSyosetuService(this@SyosetuDownloadJobService)
      val details = syosetuService.getDetails(ncode)
      val title = details?.title

      setNotification(
          params,
          notificationId,
          Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_file_download)
            .setContentTitle("Downloading novel")
            .setContentText(if (title.isNullOrEmpty()) ncode else "「$title」")
            .build(),
          JOB_END_NOTIFICATION_POLICY_DETACH)

      val pages = details?.pages ?: 1
      bookData.edit()
        .putInt(SyosetuBook.KEY_PAGE_COUNT, pages)
        .apply()

      (1..pages).map { i ->
        launch {
          val outFile = File(bookDir, "${i}.html")
          if (!outFile.exists()) {
            val text = syosetuService.getText(ncode, i)
            outFile.writeText(text)
          }
        }
      }.joinAll()

      setNotification(
          params,
          notificationId,
          Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Downloaded novel")
            .setContentText(if (title.isNullOrEmpty()) ncode else "「$title」")
            .build(),
          JOB_END_NOTIFICATION_POLICY_DETACH)

      jobFinished(params, false)
    }
    jobs[ncode] = job

    return true;
  }
  //      .setContentText("Downloading ${ncode}")

  override fun onStopJob(params: JobParameters?): Boolean {
    val ncode = params?.extras?.getString(KEY_NCODE, "")
    if (ncode.isNullOrEmpty()) {
      return false
    }

    val job = jobs[ncode]
    if (job == null || job.isCompleted || job.isCancelled) {
      return false
    }

    job.cancel()
    return true
  }

  override fun onDestroy() {
    supervisorJob.cancelChildren()
  }

  companion object {
    private const val CHANNEL_ID = "download_channel"
    private const val FOREGROUND_NOTIFICATION_ID = 1
    private const val JOB_ID = 1

    const val KEY_NCODE = "KEY_NCODE"
    const val KEY_BOOK_URI = "KEY_BOOK_DIR"
    const val KEY_BOOK_DATA = "KEY_BOOK_DATA"
  }
}