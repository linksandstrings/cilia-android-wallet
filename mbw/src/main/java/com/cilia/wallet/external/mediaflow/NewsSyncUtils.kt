package com.cilia.wallet.external.mediaflow

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.text.Html
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.google.firebase.messaging.RemoteMessage
import com.cilia.wallet.MbwManager
import com.cilia.wallet.R
import com.cilia.wallet.activity.StartupActivity
import com.cilia.wallet.activity.news.NewsActivity
import com.cilia.wallet.activity.news.NewsUtils
import com.cilia.wallet.activity.settings.SettingsPreference
import com.cilia.wallet.external.mediaflow.database.NewsDatabase
import com.cilia.wallet.external.mediaflow.model.Content
import com.cilia.wallet.external.mediaflow.model.News
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object NewsSyncUtils {
    // Start of sync time interval
    private const val START_SYNC_TIME_INTERVAL_MINS = 10L
    // End of sync time interval
    private const val END_SYNC_TIME_INTERVAL_MINS = 30L

    private const val MEDIA_OPERATION = "operation"
    private const val OPERATION_DELETE = "delete"
    private const val OPERATION_PUBLISH = "publish"
    private const val OPERATION_UPDATE = "update"
    private const val WORK_NAME_PERIODIC = "mediaflow-sync-periodic"
    const val WORK_NAME_ONCE = "mediaflow-sync-once"
    private const val DATA_KEY = "data"

    private const val ID = "id"
    private const val TITLE = "title"
    private const val IMAGE = "image"
    private const val MSG = "message"

    private const val mediaFlowNotificationId = 34563487
    private const val mediaFlowNotificationGroup = "Media Flow"
    private const val TAG_IMPORTANT = "important"

    @JvmStatic
    fun startNewsUpdateRepeating(context: Context) {
        createNotificationChannel(context)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.REPLACE,
                PeriodicWorkRequest.Builder(MediaFlowSyncWorker::class.java, 1L, TimeUnit.HOURS)
                        .setInitialDelay(10, TimeUnit.SECONDS)
                        .build())
    }

    fun delete(context: Context, id: String) {
        DeleteNews(id) {
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(NewsConstants.MEDIA_FLOW_UPDATE_ACTION))
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    class DeleteNews(val id: String, val callback: () -> Unit) : AsyncTask<Unit, Unit, Unit>() {
        override fun doInBackground(vararg p0: Unit?) = NewsDatabase.delete(id)

        override fun onPostExecute(result: Unit?) {
            super.onPostExecute(result)
            callback.invoke()
        }
    }

    private fun createNotificationChannel(context: Context) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val name = context.getString(R.string.media_flow_notification_title)
//            val importance = NotificationManager.IMPORTANCE_DEFAULT
//            val channel = NotificationChannel(NewsConstants.NEWS, name, importance).apply {
//                description = name
//            }
//            // Register the channel with the system
//            val notificationManager: NotificationManager =
//                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//            notificationManager.createNotificationChannel(channel)
//        }
    }

    @JvmStatic
    fun handle(context: Context, remoteMessage: RemoteMessage) {
        val data = remoteMessage.data[DATA_KEY]
        try {
            val dataObject = JSONObject(data)
            val operation = dataObject.getString(MEDIA_OPERATION)
            if (dataObject.has(ID)) {
                when (operation.toLowerCase(Locale.ROOT)) {
                    OPERATION_DELETE -> delete(context, dataObject.getString(ID))
                    OPERATION_PUBLISH -> {
                        if (dataObject.has(TITLE)) {
                            val news = News().apply {
                                id = dataObject.getInt(ID)
                                title = Content(dataObject.getString(TITLE))
                                image = dataObject.getString(IMAGE)
                                content = Content(dataObject.getString(MSG))
                                isFull = false
                            }
                            NewsDatabase.saveNews(listOf(news))
                            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(NewsConstants.MEDIA_FLOW_UPDATE_ACTION))
                            if (SettingsPreference.mediaFLowNotificationEnabled) {
                                notifyAboutMediaFlowTopics(context, listOf(news))
                            }
                        }

                        // Start sync in random time
                        scheduleSyncStart(context)
                    }
                    OPERATION_UPDATE -> {
                        val topic = NewsDatabase.getTopic(dataObject.getInt(ID))
                        if (topic != null) {
                            topic.isFull = false // topic need sync
                            topic.title = Content(dataObject.getString(TITLE))
                            topic.image = dataObject.getString(IMAGE)
                            NewsDatabase.saveNews(listOf(topic))
                            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(NewsConstants.MEDIA_FLOW_UPDATE_ACTION))
                        }

                        // Start sync in random time
                        scheduleSyncStart(context)
                    }
                }
            }
        } catch (e: JSONException) {
            Log.e("NewsSync", "json data wrong", e)
        }

    }

    // Schedules sync start in random time between START_SYNC_TIME_INTERVAL_MINS and END_SYNC_TIME_INTERVAL_MINS
    private fun scheduleSyncStart(context: Context) {
        WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME_ONCE, ExistingWorkPolicy.REPLACE, OneTimeWorkRequest.Builder(MediaFlowSyncWorker::class.java)
                        .setInitialDelay(Random.nextLong(TimeUnit.MINUTES.toMillis(START_SYNC_TIME_INTERVAL_MINS), TimeUnit.MINUTES.toMillis(END_SYNC_TIME_INTERVAL_MINS)), TimeUnit.MILLISECONDS)
                        .build())
    }

    fun notifyAboutMediaFlowTopics(context: Context, newTopicsRaw: List<News>) {
//        val newTopics = newTopicsRaw.filter { topic -> topic.tags?.any { tag -> tag?.name == TAG_IMPORTANT } == true }
//        val builder = createNotificationMediaFlowBuilder(context)
//
//        if (newTopics.size == 1) {
//            val news = newTopics[0]
//            builder.setContentText(Html.fromHtml(news.title.rendered))
//            builder.setTicker(Html.fromHtml(news.title.rendered))
//
//            val remoteViews = RemoteViews(context.packageName, R.layout.layout_news_notification)
//            remoteViews.setTextViewText(R.id.title, Html.fromHtml(news.title.rendered))
//
//            val activityIntent = createSingleNewsIntent(context, news)
//            val pIntent = PendingIntent.getActivity(context, 0, activityIntent, PendingIntent.FLAG_CANCEL_CURRENT)
//
//            builder.setContent(remoteViews)
//                    .setContentIntent(pIntent)
//            builder.setGroup(mediaFlowNotificationGroup)
//            NotificationManagerCompat.from(context).notify(mediaFlowNotificationId, builder.build())
//        } else if (newTopics.size > 1) {
//            builder.setGroupSummary(true)
//            val inboxStyle = NotificationCompat.InboxStyle()
//                    .setBigContentTitle(context.getString(R.string.media_flow_notification_title))
//            newTopics.forEach { news ->
//                inboxStyle.addLine(Html.fromHtml(news.title.rendered))
//            }
//            builder.setStyle(inboxStyle)
//
//            val activityIntent = Intent(context, StartupActivity::class.java)
//            activityIntent.action = NewsUtils.MEDIA_FLOW_ACTION
//            val pIntent = PendingIntent.getActivity(context, 0, activityIntent, PendingIntent.FLAG_CANCEL_CURRENT)
//            builder.setContentIntent(pIntent)
//                    .setGroup(mediaFlowNotificationGroup)
//            NotificationManagerCompat.from(context).notify(mediaFlowNotificationId, builder.build())
//        }
    }

    private fun createSingleNewsIntent(context: Context, news: News): Intent {
        val clazz = if (MbwManager.getInstance(context).isAppInForeground) NewsActivity::class.java else StartupActivity::class.java
        val activityIntent = Intent(context, clazz)
        activityIntent.action = NewsUtils.MEDIA_FLOW_ACTION
        activityIntent.putExtra(NewsConstants.NEWS, news)
        return activityIntent
    }

    private fun createNotificationMediaFlowBuilder(context: Context): NotificationCompat.Builder =
            NotificationCompat.Builder(context, NewsConstants.NEWS)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentTitle(context.getString(R.string.media_flow_notification_title))
}
