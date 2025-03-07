package com.blockchain.notifications.analytics

import android.content.SharedPreferences
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import java.io.Serializable

class AnalyticsImpl internal constructor(
    private val firebaseAnalytics: FirebaseAnalytics,
    private val nabuAnalytics: Analytics,
    private val store: SharedPreferences
) : Analytics, ProviderSpecificAnalytics {

    private val sentAnalytics = mutableSetOf<String>()

    override fun logEvent(analyticsEvent: AnalyticsEvent) {
        if (nabuAnalyticsNames.contains(analyticsEvent.event)) {
            nabuAnalytics.logEvent(analyticsEvent)
        } else {
            firebaseAnalytics.logEvent(analyticsEvent.event, toBundle(analyticsEvent.params))
        }
    }

    override fun logEventOnce(analyticsEvent: AnalyticsEvent) {
        if (!hasSentMetric(analyticsEvent.event)) {
            setMetricAsSent(analyticsEvent.event)
            logEvent(analyticsEvent)
        }
    }

    override fun logEventOnceForSession(analyticsEvent: AnalyticsEvent) {
        if (!sentAnalytics.contains(analyticsEvent.event)) {
            logEvent(analyticsEvent)
            sentAnalytics.add(analyticsEvent.event)
        }
    }

    private fun toBundle(params: Map<String, Serializable>): Bundle? {
        if (params.isEmpty()) return null

        return Bundle().apply {
            params.forEach { (k, v) -> putString(k, v.toString()) }
        }
    }

    private fun hasSentMetric(metricName: String) =
        store.contains("HAS_SENT_METRIC_$metricName")

    private fun setMetricAsSent(metricName: String) =
        store.edit().putBoolean("HAS_SENT_METRIC_$metricName", true).apply()

    private val nabuAnalyticsNames = AnalyticsNames.values().map { it.eventName }

    override fun logSingUp(success: Boolean) {
        val b = Bundle()
        b.putString(FirebaseAnalytics.Param.METHOD, success.toString())
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, b)
    }

    override fun logLogin(success: Boolean) {
        val b = Bundle()
        b.putString(FirebaseAnalytics.Param.METHOD, success.toString())
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, b)
    }

    override fun logContentView(screen: String) {
        val b = Bundle()
        b.putString(FirebaseAnalytics.Param.ITEMS, screen)
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM, b)
    }

    override fun logShare(share: String) {
        val b = Bundle()
        b.putString(FirebaseAnalytics.Param.METHOD, share)
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SHARE, b)
    }
}