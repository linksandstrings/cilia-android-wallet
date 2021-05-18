package com.cilia.wallet.fio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cilia.wallet.MbwManager
import com.cilia.wallet.R
import com.cilia.wallet.activity.fio.requests.ApproveFioRequestActivity
import com.cilia.wallet.activity.util.toStringWithUnit
import com.cilia.wallet.event.SyncStopped
import com.mycelium.wapi.wallet.Util
import com.mycelium.wapi.wallet.Util.getCoinByChain
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.fio.FioAccount
import com.mycelium.wapi.wallet.fio.FioGroup
import com.mycelium.wapi.wallet.fio.getActiveFioAccounts
import com.squareup.otto.Subscribe
import fiofoundation.io.fiosdk.models.fionetworkprovider.FIORequestContent


object FioRequestNotificator {
    const val FIO_REQUEST_ACTION = "fio_request_action"
    private const val chanelId = "FIORequest"
    private const val fioRequestNotificationGroup = "com.cilia.wallet.FIO_REQUESTS"
    private const val fioRequestNotificationId = 24563487

    lateinit var context: Context
    lateinit var preferences: SharedPreferences


    @JvmStatic
    fun initialize(context: Context) {
        this.context = context
        preferences = context.getSharedPreferences("FioRequestNotificator", Context.MODE_PRIVATE)
        createNotificationChannel(context)
        MbwManager.getEventBus().register(this)
    }

    @Subscribe
    fun syncStopped(event: SyncStopped?) {
        handleRequests()
    }

    private fun handleRequests() {
        notifyRequest(MbwManager.getInstance(context).getWalletManager(false)
                .getActiveFioAccounts()
                .asSequence()
                .map(FioAccount::getRequestsGroups)
                .flatten()
                .filter { it.status == FioGroup.Type.PENDING }
                .map { it.children }
                .flatten()
                .filter { !preferences.getBoolean(it.fioRequestId.toString(), false) }
                .toList())

    }

    private fun notifyRequest(requests: List<FIORequestContent>) {
        requests.forEach {
            getCoinByChain(MbwManager.getInstance(context).network, it.deserializedContent!!.chainCode)?.let { requestedCurrency ->
                val amount = Value.valueOf(requestedCurrency, Util.strToBigInteger(requestedCurrency, it.deserializedContent!!.amount))
                val bigView = RemoteViews(context.packageName, R.layout.layout_fio_request_notification_big).apply {
                    setTextViewText(R.id.fromFioName, context.getString(R.string.transaction_from_address_prefix, it.payeeFioAddress))
                    setTextViewText(R.id.amount, context.getString(R.string.amount_label_s, amount.toStringWithUnit()))
                    setTextViewText(R.id.memo, it.deserializedContent?.memo)
                }
                val smallView = RemoteViews(context.packageName, R.layout.layout_fio_request_notification).apply {
                    setTextViewText(R.id.fromFioName, context.getString(R.string.transaction_from_address_prefix, it.payeeFioAddress))
                    setTextViewText(R.id.amount, context.getString(R.string.amount_label_s, amount.toStringWithUnit()))
                    setTextViewText(R.id.memo, it.deserializedContent?.memo)
                }
                NotificationManagerCompat.from(context).notify(fioRequestNotificationId + it.fioRequestId.toInt(),
                        createNotification(context)
                                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                                .setCustomContentView(smallView)
                                .setCustomBigContentView(bigView)
                                .setContentIntent(PendingIntent.getService(context, 0,
                                        createSingleFIORequestIntent(context, it), PendingIntent.FLAG_UPDATE_CURRENT))
                                .setGroup(fioRequestNotificationGroup)
                                .build())
                preferences.edit().putBoolean(it.fioRequestId.toString(), true).apply()
            }
        }
    }

    private fun createSingleFIORequestIntent(context: Context, request: FIORequestContent): Intent =
            Intent(context, FioRequestService::class.java)
                    .setAction(FIO_REQUEST_ACTION)
                    .putExtra(ApproveFioRequestActivity.CONTENT, request.toJson())

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "FIO Request"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(chanelId, name, importance).apply {
                description = name
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
        }
    }

    private fun createNotification(context: Context): NotificationCompat.Builder =
            NotificationCompat.Builder(context.applicationContext, chanelId)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setAutoCancel(true)
                    .setSubText("FIO Request")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
}
