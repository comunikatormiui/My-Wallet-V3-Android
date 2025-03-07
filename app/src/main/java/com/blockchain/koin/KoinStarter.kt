package com.blockchain.koin

import android.app.Application
import com.blockchain.koin.modules.apiInterceptorsModule
import com.blockchain.koin.modules.appProperties
import com.blockchain.koin.modules.applicationModule
import com.blockchain.koin.modules.environmentModule
import com.blockchain.koin.modules.featureFlagsModule
import com.blockchain.koin.modules.keys
import com.blockchain.koin.modules.moshiModule
import com.blockchain.koin.modules.nabuUrlModule
import com.blockchain.koin.modules.serviceModule
import com.blockchain.koin.modules.urls
import com.blockchain.koin.modules.xlmModule
import com.blockchain.network.modules.apiModule
import com.blockchain.network.modules.okHttpModule
import com.blockchain.notifications.koin.notificationModule
import com.blockchain.api.blockchainApiModule
import com.blockchain.bitpay.bitpayModule
import com.blockchain.coincore.coincoreModule
import com.blockchain.core.price.pricesModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.android.campaign.campaignModule
import piuk.blockchain.android.ui.activity.activitiesModule
import piuk.blockchain.android.ui.dashboard.announcements.dashboardAnnouncementsModule
import piuk.blockchain.android.ui.kyc.koin.kycUiModule
import piuk.blockchain.android.ui.kyc.koin.kycUiNabuModule
import piuk.blockchain.android.ui.auth.newlogin.secureChannelModule
import piuk.blockchain.android.ui.dashboard.dashboardModule
import piuk.blockchain.android.ui.launcher.loader.loaderModule
import piuk.blockchain.android.ui.kyc.autocomplete.kycAutocompleteModule
import piuk.blockchain.android.ui.login.loginUiModule
import piuk.blockchain.android.ui.reset.resetAccountModule
import piuk.blockchain.android.ui.start.startupUiModule
import piuk.blockchain.android.ui.transactionflow.transactionModule
import piuk.blockchain.android.ui.transfer.receive.receiveCryptoModule
import piuk.blockchain.com.internalFeatureFlagsModule
import timber.log.Timber

object KoinStarter {

    @Suppress("ConstantConditionIf")
    @JvmStatic
    fun start(application: Application) {
        stopKoin()
        startKoin {
            if (BuildConfig.LOG_KOIN_STARTUP) TimberLogger() else NullLogger()
            properties(appProperties + keys + urls)
            androidContext(application)
            modules(
                listOf(
                    activitiesModule,
                    apiInterceptorsModule,
                    apiModule,
                    blockchainApiModule,
                    applicationModule,
                    dashboardModule,
                    campaignModule,
                    bitpayModule,
                    coincoreModule,
                    transactionModule,
                    okHttpModule,
                    coreModule,
                    pricesModule,
                    dashboardAnnouncementsModule,
                    environmentModule,
                    featureFlagsModule,
                    internalFeatureFlagsModule,
                    authenticationModule,
                    kycUiModule,
                    kycUiNabuModule,
                    loginUiModule,
                    loaderModule,
                    moshiModule,
                    nabuModule,
                    nabuUrlModule,
                    notificationModule,
                    receiveCryptoModule,
                    resetAccountModule,
                    secureChannelModule,
                    serviceModule,
                    startupUiModule,
                    sunriverModule,
                    walletModule,
                    xlmModule,
                    kycAutocompleteModule
                )
            )
        }
    }
}

private class TimberLogger : Logger() {
    override fun log(level: Level, msg: MESSAGE) {
        when (level) {
            Level.DEBUG -> Timber.d(msg)
            Level.INFO -> Timber.i(msg)
            Level.ERROR -> Timber.e(msg)
            else -> {
            }
        }
    }
}

private class NullLogger : Logger() {
    override fun log(level: Level, msg: MESSAGE) {}
}