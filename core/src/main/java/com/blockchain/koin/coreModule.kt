package com.blockchain.koin

import android.preference.PreferenceManager
import com.blockchain.common.util.AndroidDeviceIdGenerator
import com.blockchain.core.BuildConfig
import com.blockchain.core.Database
import com.blockchain.core.chains.bitcoincash.BchDataManager
import com.blockchain.core.chains.bitcoincash.BchDataStore
import com.blockchain.core.chains.erc20.Erc20DataManager
import com.blockchain.core.chains.erc20.Erc20DataManagerImpl
import com.blockchain.core.chains.erc20.call.Erc20BalanceCallCache
import com.blockchain.core.chains.erc20.call.Erc20HistoryCallCache
import com.blockchain.core.custodial.TradingBalanceCallCache
import com.blockchain.core.custodial.TradingBalanceDataManager
import com.blockchain.core.custodial.TradingBalanceDataManagerImpl
import com.blockchain.core.dynamicassets.DynamicAssetsDataManager
import com.blockchain.core.dynamicassets.impl.DynamicAssetsDataManagerImpl
import com.blockchain.core.interest.InterestBalanceCallCache
import com.blockchain.core.interest.InterestBalanceDataManager
import com.blockchain.core.interest.InterestBalanceDataManagerImpl
import com.blockchain.core.payments.PaymentsDataManager
import com.blockchain.core.payments.PaymentsDataManagerImpl
import com.blockchain.core.user.NabuUserDataManager
import com.blockchain.core.user.NabuUserDataManagerImpl
import com.blockchain.datamanagers.DataManagerPayloadDecrypt
import com.blockchain.logging.LastTxUpdateDateOnSettingsService
import com.blockchain.logging.LastTxUpdater
import com.blockchain.logging.Logger
import com.blockchain.logging.NullLogger
import com.blockchain.logging.TimberLogger
import com.blockchain.metadata.MetadataRepository
import com.blockchain.payload.PayloadDecrypt
import com.blockchain.preferences.AppInfoPrefs
import com.blockchain.preferences.AuthPrefs
import com.blockchain.preferences.BankLinkingPrefs
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.DashboardPrefs
import com.blockchain.preferences.InternalFeatureFlagPrefs
import com.blockchain.preferences.NotificationPrefs
import com.blockchain.preferences.RatingPrefs
import com.blockchain.preferences.SecureChannelPrefs
import com.blockchain.preferences.SecurityPrefs
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.preferences.ThePitLinkingPrefs
import com.blockchain.preferences.WalletStatus
import com.blockchain.sunriver.XlmHorizonUrlFetcher
import com.blockchain.sunriver.XlmTransactionTimeoutFetcher
import com.blockchain.wallet.SeedAccess
import com.blockchain.wallet.SeedAccessWithoutPrompt
import info.blockchain.wallet.metadata.MetadataDerivation
import info.blockchain.wallet.util.PrivateKeyFactory
import org.koin.dsl.bind
import org.koin.dsl.module
import piuk.blockchain.androidcore.data.access.PinRepository
import piuk.blockchain.androidcore.data.access.PinRepositoryImpl
import piuk.blockchain.androidcore.data.auth.AuthDataManager
import piuk.blockchain.androidcore.data.auth.WalletAuthService
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.ethereum.datastores.EthDataStore
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.metadata.MetadataManager
import piuk.blockchain.androidcore.data.metadata.MoshiMetadataRepositoryAdapter
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManagerSeedAccessAdapter
import piuk.blockchain.androidcore.data.payload.PayloadService
import piuk.blockchain.androidcore.data.payload.PayloadVersionController
import piuk.blockchain.androidcore.data.payload.PayloadVersionControllerImpl
import piuk.blockchain.androidcore.data.payload.PromptingSeedAccessAdapter
import piuk.blockchain.androidcore.data.payments.PaymentService
import piuk.blockchain.androidcore.data.payments.SendDataManager
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.data.rxjava.SSLPinningEmitter
import piuk.blockchain.androidcore.data.rxjava.SSLPinningObservable
import piuk.blockchain.androidcore.data.rxjava.SSLPinningSubject
import piuk.blockchain.androidcore.data.settings.EmailSyncUpdater
import piuk.blockchain.androidcore.data.settings.PhoneNumberUpdater
import piuk.blockchain.androidcore.data.settings.SettingsDataManager
import piuk.blockchain.androidcore.data.settings.SettingsEmailAndSyncUpdater
import piuk.blockchain.androidcore.data.settings.SettingsPhoneNumberUpdater
import piuk.blockchain.androidcore.data.settings.SettingsService
import piuk.blockchain.androidcore.data.settings.datastore.SettingsDataStore
import piuk.blockchain.androidcore.data.settings.datastore.SettingsMemoryStore
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsState
import piuk.blockchain.androidcore.utils.AESUtilWrapper
import piuk.blockchain.androidcore.utils.CloudBackupAgent
import piuk.blockchain.androidcore.utils.DeviceIdGenerator
import piuk.blockchain.androidcore.utils.DeviceIdGeneratorImpl
import piuk.blockchain.androidcore.utils.EncryptedPrefs
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.utils.PrefsUtil
import piuk.blockchain.androidcore.utils.UUIDGenerator
import java.util.UUID

val coreModule = module {

    single { RxBus() }

    single { SSLPinningSubject() }.bind(SSLPinningObservable::class).bind(SSLPinningEmitter::class)

    factory {
        WalletAuthService(
            walletApi = get()
        )
    }

    factory { PrivateKeyFactory() }

    scope(payloadScopeQualifier) {

        factory {
            TradingBalanceCallCache(
                balanceService = get(),
                assetCatalogue = get(),
                authHeaderProvider = get()
            )
        }

        scoped {
            TradingBalanceDataManagerImpl(
                balanceCallCache = get()
            )
        }.bind(TradingBalanceDataManager::class)

        factory {
            InterestBalanceCallCache(
                balanceService = get(),
                assetCatalogue = get(),
                authHeaderProvider = get()
            )
        }

        scoped {
            InterestBalanceDataManagerImpl(
                balanceCallCache = get()
            )
        }.bind(InterestBalanceDataManager::class)

        scoped {
            EthDataManager(
                payloadDataManager = get(),
                ethAccountApi = get(),
                ethDataStore = get(),
                metadataManager = get(),
                lastTxUpdater = get()
            )
        }

        factory {
            Erc20BalanceCallCache(
                erc20Service = get(),
                assetCatalogue = get()
            )
        }

        factory {
            Erc20HistoryCallCache(
                ethDataManager = get(),
                erc20Service = get()
            )
        }

        scoped {
            Erc20DataManagerImpl(
                ethDataManager = get(),
                balanceCallCache = get(),
                historyCallCache = get()
            )
        }.bind(Erc20DataManager::class)

        factory { BchDataStore() }

        scoped {
            BchDataManager(
                payloadDataManager = get(),
                bchDataStore = get(),
                bitcoinApi = get(),
                defaultLabels = get(),
                metadataManager = get(),
                crashLogger = get()
            )
        }

        factory {
            PayloadService(
                payloadManager = get(),
                versionController = get(),
                crashLogger = get()
            )
        }

        factory {
            PayloadDataManager(
                payloadService = get(),
                privateKeyFactory = get(),
                bitcoinApi = get(),
                payloadManager = get(),
                crashLogger = get()
            )
        }

        factory {
            PayloadVersionControllerImpl(
                settingsApi = get()
            )
        }.bind(PayloadVersionController::class)

        factory {
            DataManagerPayloadDecrypt(
                payloadDataManager = get(),
                bchDataManager = get()
            )
        }.bind(PayloadDecrypt::class)

        factory { PromptingSeedAccessAdapter(PayloadDataManagerSeedAccessAdapter(get()), get()) }
            .bind(SeedAccessWithoutPrompt::class)
            .bind(SeedAccess::class)

        scoped {
            MetadataManager(
                payloadDataManager = get(),
                metadataInteractor = get(),
                metadataDerivation = MetadataDerivation(),
                crashLogger = get()
            )
        }

        scoped {
            MoshiMetadataRepositoryAdapter(get(), get())
        }.bind(MetadataRepository::class)

        scoped { EthDataStore() }

        scoped { WalletOptionsState() }

        scoped {
            SettingsDataManager(
                settingsService = get(),
                settingsDataStore = get(),
                currencyPrefs = get(),
                walletSettingsService = get()
            )
        }

        scoped { SettingsService(get()) }

        scoped {
            SettingsDataStore(SettingsMemoryStore(), get<SettingsService>().getSettingsObservable())
        }

        factory {
            WalletOptionsDataManager(
                authService = get(),
                walletOptionsState = get(),
                settingsDataManager = get(),
                explorerUrl = getProperty("explorer-api")
            )
        }.bind(XlmTransactionTimeoutFetcher::class)
            .bind(XlmHorizonUrlFetcher::class)

        scoped { FeeDataManager(get()) }

        factory {
            AuthDataManager(
                prefs = get(),
                authApiService = get(),
                walletAuthService = get(),
                pinRepository = get(),
                aesUtilWrapper = get(),
                crashLogger = get()
            )
        }

        factory { LastTxUpdateDateOnSettingsService(get()) }.bind(LastTxUpdater::class)

        factory {
            SendDataManager(
                paymentService = get(),
                lastTxUpdater = get()
            )
        }

        factory { SettingsPhoneNumberUpdater(get()) }.bind(PhoneNumberUpdater::class)

        factory { SettingsEmailAndSyncUpdater(get(), get()) }.bind(EmailSyncUpdater::class)

        scoped {
            NabuUserDataManagerImpl(
                nabuUserService = get(),
                authenticator = get(),
                tierService = get()
            )
        }.bind(NabuUserDataManager::class)

        scoped {
            PaymentsDataManagerImpl(
                paymentsService = get(),
                authenticator = get()
            )
        }.bind(PaymentsDataManager::class)
    }

    single {
        DynamicAssetsDataManagerImpl(
            discoveryService = get()
        )
    }.bind(DynamicAssetsDataManager::class)

    factory {
        AndroidDeviceIdGenerator(
            ctx = get()
        )
    }

    factory {
        DeviceIdGeneratorImpl(
            platformDeviceIdGenerator = get(),
            analytics = get()
        )
    }.bind(DeviceIdGenerator::class)

    factory {
        object : UUIDGenerator {
            override fun generateUUID(): String = UUID.randomUUID().toString()
        }
    }.bind(UUIDGenerator::class)

    single {
        PrefsUtil(
            ctx = get(),
            store = get(),
            backupStore = CloudBackupAgent.backupPrefs(ctx = get()),
            idGenerator = get(),
            uuidGenerator = get(),
            crashLogger = get(),
            environmentConfig = get()
        )
    }.bind(PersistentPrefs::class)
        .bind(CurrencyPrefs::class)
        .bind(NotificationPrefs::class)
        .bind(DashboardPrefs::class)
        .bind(SecurityPrefs::class)
        .bind(ThePitLinkingPrefs::class)
        .bind(SimpleBuyPrefs::class)
        .bind(RatingPrefs::class)
        .bind(WalletStatus::class)
        .bind(EncryptedPrefs::class)
        .bind(AuthPrefs::class)
        .bind(AppInfoPrefs::class)
        .bind(BankLinkingPrefs::class)
        .bind(InternalFeatureFlagPrefs::class)
        .bind(SecureChannelPrefs::class)

    factory {
        PaymentService(
            payment = get(),
            dustService = get()
        )
    }

    factory {
        PreferenceManager.getDefaultSharedPreferences(
            /* context = */ get()
        )
    }

    single {
        if (BuildConfig.DEBUG) {
            TimberLogger()
        } else {
            NullLogger
        }
    }.bind(Logger::class)

    single {
        PinRepositoryImpl()
    }.bind(PinRepository::class)

    factory { AESUtilWrapper() }

    single {
        Database(driver = get())
    }
}
