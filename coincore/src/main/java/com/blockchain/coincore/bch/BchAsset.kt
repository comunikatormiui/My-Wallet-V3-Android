package com.blockchain.coincore.bch

import com.blockchain.core.chains.bitcoincash.BchDataManager
import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.core.custodial.TradingBalanceDataManager
import com.blockchain.core.interest.InterestBalanceDataManager
import com.blockchain.featureflags.InternalFeatureFlagApi
import com.blockchain.logging.CrashLogger
import com.blockchain.nabu.UserIdentity
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.WalletStatus
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.wallet.DefaultLabels
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.isCustodialOnly
import info.blockchain.wallet.util.FormatsUtil
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import com.blockchain.coincore.CryptoAccount
import com.blockchain.coincore.CryptoAddress
import com.blockchain.coincore.NonCustodialSupport
import com.blockchain.coincore.ReceiveAddress
import com.blockchain.coincore.SingleAccountList
import com.blockchain.coincore.TxResult
import com.blockchain.coincore.impl.CryptoAssetBase
import com.blockchain.coincore.impl.BackendNotificationUpdater
import com.blockchain.coincore.impl.CustodialTradingAccount
import com.blockchain.coincore.impl.NotificationAddresses
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payments.SendDataManager
import thepit.PitLinking
import timber.log.Timber

private const val BCH_URL_PREFIX = "bitcoincash:"

/*internal*/ class BchAsset internal constructor(
    payloadManager: PayloadDataManager,
    private val bchDataManager: BchDataManager,
    custodialManager: CustodialWalletManager,
    interestBalances: InterestBalanceDataManager,
    tradingBalances: TradingBalanceDataManager,
    private val feeDataManager: FeeDataManager,
    private val sendDataManager: SendDataManager,
    exchangeRates: ExchangeRatesDataManager,
    currencyPrefs: CurrencyPrefs,
    labels: DefaultLabels,
    pitLinking: PitLinking,
    crashLogger: CrashLogger,
    private val walletPreferences: WalletStatus,
    private val beNotifyUpdate: BackendNotificationUpdater,
    identity: UserIdentity,
    features: InternalFeatureFlagApi
) : CryptoAssetBase(
    payloadManager,
    exchangeRates,
    currencyPrefs,
    labels,
    custodialManager,
    interestBalances,
    tradingBalances,
    pitLinking,
    crashLogger,
    identity,
    features
), NonCustodialSupport {
    override val asset: AssetInfo
        get() = CryptoCurrency.BCH

    override val isCustodialOnly: Boolean = asset.isCustodialOnly
    override val multiWallet: Boolean = true

    override fun initToken(): Completable =
        bchDataManager.initBchWallet(labels.getDefaultNonCustodialWalletLabel())
            .doOnError { Timber.e("Unable to init BCH, because: $it") }
            .onErrorComplete()

    override fun loadNonCustodialAccounts(labels: DefaultLabels): Single<SingleAccountList> =
        Single.fromCallable {
            with(bchDataManager) {
                mutableListOf<CryptoAccount>().apply {
                    getAccountMetadataList().forEachIndexed { i, account ->
                        val bchAccount = BchCryptoWalletAccount.createBchAccount(
                            payloadManager = payloadManager,
                            jsonAccount = account,
                            bchManager = bchDataManager,
                            addressIndex = i,
                            exchangeRates = exchangeRates,
                            feeDataManager = feeDataManager,
                            sendDataManager = sendDataManager,
                            walletPreferences = walletPreferences,
                            custodialWalletManager = custodialManager,
                            refreshTrigger = this@BchAsset,
                            identity = identity
                        )
                        if (bchAccount.isDefault) {
                            updateBackendNotificationAddresses(bchAccount)
                        }
                        add(bchAccount)
                    }
                }
            }
        }

    override fun loadCustodialAccounts(): Single<SingleAccountList> =
        Single.just(
            listOf(
                CustodialTradingAccount(
                    asset = asset,
                    label = labels.getDefaultCustodialWalletLabel(),
                    exchangeRates = exchangeRates,
                    custodialWalletManager = custodialManager,
                    tradingBalances = tradingBalances,
                    identity = identity,
                    features = features
                )
            )
        )

    private fun updateBackendNotificationAddresses(account: BchCryptoWalletAccount) {
        require(account.isDefault)
        require(!account.isArchived)

        val result = mutableListOf<String>()

        for (i in 0 until OFFLINE_CACHE_ITEM_COUNT) {
            account.getReceiveAddressAtPosition(i)?.let {
                result += it
            }
        }

        val notify = NotificationAddresses(
            assetTicker = asset.networkTicker,
            addressList = result
        )
        return beNotifyUpdate.updateNotificationBackend(notify)
    }

    override fun parseAddress(address: String, label: String?): Maybe<ReceiveAddress> =
        Maybe.fromCallable {
            val normalisedAddress = address.removePrefix(BCH_URL_PREFIX)
            if (isValidAddress(normalisedAddress)) {
                BchAddress(normalisedAddress, label ?: address)
            } else {
                null
            }
        }

    override fun isValidAddress(address: String): Boolean =
        FormatsUtil.isValidBCHAddress(address)

    fun createAccount(xpub: String): Completable {
        bchDataManager.createAccount(xpub)
        return bchDataManager.syncWithServer().doOnComplete { forceAccountsRefresh() }
    }

    companion object {
        private const val OFFLINE_CACHE_ITEM_COUNT = 5
    }
}

internal class BchAddress(
    address_: String,
    override val label: String = address_,
    override val onTxCompleted: (TxResult) -> Completable = { Completable.complete() }
) : CryptoAddress {
    override val address: String = address_.removeBchUri()
    override val asset: AssetInfo = CryptoCurrency.BCH

    override fun toUrl(amount: CryptoValue): String {
        return "$BCH_URL_PREFIX$address"
    }
}

private fun String.removeBchUri(): String = this.replace(BCH_URL_PREFIX, "")
