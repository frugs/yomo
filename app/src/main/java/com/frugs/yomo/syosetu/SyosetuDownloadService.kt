package com.frugs.yomo.syosetu

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DefaultDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import com.frugs.yomo.BookListActivity
import com.frugs.yomo.BookyApp
import com.frugs.yomo.R
import java.io.File
import java.util.concurrent.Executors

@UnstableApi
class SyosetuDownloadService : DownloadService(FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_notification_channel_name,
    R.string.download_notification_channel_description) {

  override fun getDownloadManager(): DownloadManager {
    val databaseProvider = DefaultDatabaseProvider(BookyApp.getDB(this))
    val cache = SimpleCache(
        File(cacheDir, "downloads"),
        NoOpCacheEvictor(),
        databaseProvider)
    val downloadManager = DownloadManager(this,
        databaseProvider,
        cache,
        DefaultHttpDataSource.Factory(),
        Executors.newCachedThreadPool())
    downloadManager.requirements = Requirements(Requirements.NETWORK)
    downloadManager.maxParallelDownloads = 5
    return downloadManager
  }

  override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

  override fun getForegroundNotification(
      downloads: List<Download>,
      notMetRequirements: Int): Notification {
    val downloadNotificationHelper = DownloadNotificationHelper(this, CHANNEL_ID)

    return downloadNotificationHelper.buildProgressNotification(
        this,
        R.drawable.ic_stat_file_download,
        PendingIntent.getActivity(this,
            0,
            Intent(this, BookListActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT),
        "Downloading...",
        downloads,
        Requirements.NETWORK)
  }

  companion object {
    private const val CHANNEL_ID = "download_channel"
    private const val FOREGROUND_NOTIFICATION_ID = 1
    private const val JOB_ID = 1
  }
}
