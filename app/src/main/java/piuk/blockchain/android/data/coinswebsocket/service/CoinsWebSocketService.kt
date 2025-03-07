package piuk.blockchain.android.data.coinswebsocket.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.blockchain.koin.scopedInject
import com.blockchain.lifecycle.AppState
import com.blockchain.lifecycle.LifecycleInterestedComponent
import com.blockchain.notifications.NotificationsUtil
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.websocket.MessagesSocketHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import piuk.blockchain.android.R
import piuk.blockchain.android.data.coinswebsocket.strategy.CoinsWebSocketStrategy
import piuk.blockchain.android.ui.customviews.ToastCustom
import piuk.blockchain.android.ui.home.MainActivity
import piuk.blockchain.androidcore.data.events.ActionEvent
import piuk.blockchain.androidcore.data.rxjava.RxBus

class CoinsWebSocketService(
    private val applicationContext: Context
) : MessagesSocketHandler, KoinComponent {

    private val compositeDisposable = CompositeDisposable()
    private val notificationManager: NotificationManager by inject()
    private val coinsWebSocketStrategy: CoinsWebSocketStrategy by scopedInject()
    private val lifecycleInterestedComponent: LifecycleInterestedComponent by inject()
    private val rxBus: RxBus by inject()
    private val analytics: Analytics by inject()

    fun start() {
        compositeDisposable.clear()
        coinsWebSocketStrategy.close()
        coinsWebSocketStrategy.setMessagesHandler(this)
        coinsWebSocketStrategy.open()

        compositeDisposable += lifecycleInterestedComponent
            .appStateUpdated
            .subscribe {
                if (it == AppState.FOREGROUNDED) {
                    coinsWebSocketStrategy.open()
                } else {
                    coinsWebSocketStrategy.close()
                }
            }
        }

    override fun showToast(message: Int) {
        ToastCustom.makeText(
            applicationContext,
            applicationContext.getString(message),
            ToastCustom.LENGTH_SHORT,
            ToastCustom.TYPE_GENERAL)
    }

    override fun triggerNotification(title: String, marquee: String, text: String) {
        val notifyIntent = Intent(applicationContext, MainActivity::class.java)
        notifyIntent.putExtra(NotificationsUtil.INTENT_FROM_NOTIFICATION, true)

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT)

        NotificationsUtil(
            context = applicationContext,
            notificationManager = notificationManager,
            analytics = analytics
        ).triggerNotification(
            title = title,
            marquee = marquee,
            text = text,
            icon = R.mipmap.ic_launcher,
            pendingIntent = pendingIntent,
            id = 1000,
            appName = R.string.app_name,
            colorRes = R.color.primary_navy_medium
        )
    }

    override fun sendBroadcast(event: ActionEvent) {
        rxBus.emitEvent(ActionEvent::class.java, event)
    }

    fun release() {
        coinsWebSocketStrategy.close()
        compositeDisposable.clear()
    }
}