package com.blockchain.coincore

import com.blockchain.logging.CrashLogger
import com.blockchain.wallet.DefaultLabels
import info.blockchain.balance.AssetInfo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import com.blockchain.coincore.impl.AllWalletsAccount
import com.blockchain.coincore.impl.TxProcessorFactory
import com.blockchain.coincore.loader.AssetCatalogueImpl
import com.blockchain.coincore.loader.AssetLoader
import info.blockchain.balance.Money
import piuk.blockchain.androidcore.data.payload.PayloadDataManager

internal class CoincoreInitFailure(msg: String, e: Throwable) : Exception(msg, e)

@Deprecated("Use result from ExchangeManager directly")
data class ExchangePriceWithDelta(
    val price: Money,
    val delta: Double
)

class Coincore internal constructor(
    private val assetLoader: AssetLoader,
    private val assetCatalogue: AssetCatalogueImpl,
    // TODO: Build an interface on PayloadDataManager/PayloadManager for 'global' crypto calls; second password etc?
    private val payloadManager: PayloadDataManager,
    private val txProcessorFactory: TxProcessorFactory,
    private val defaultLabels: DefaultLabels,
    private val fiatAsset: Asset,
    private val crashLogger: CrashLogger
) {

    operator fun get(asset: AssetInfo): CryptoAsset =
        assetLoader[asset]

    fun init(): Completable =
        assetLoader.initAndPreload()
            .doOnComplete {
                crashLogger.logEvent("Coincore init complete")
            }
            .doOnError {
                crashLogger.logEvent("Coincore initialisation failed! $it")
            }

    val fiatAssets: Asset
        get() = fiatAsset

    fun validateSecondPassword(secondPassword: String) =
        payloadManager.validateSecondPassword(secondPassword)

    private fun allLoadedAssets() = assetLoader.loadedAssets + fiatAsset

    fun allWallets(includeArchived: Boolean = false): Single<AccountGroup> =
        Maybe.concat(
            allLoadedAssets().map {
                it.accountGroup().map { grp -> grp.accounts }
                    .map { list ->
                        list.filter { account ->
                            (includeArchived || account !is CryptoAccount) || !account.isArchived
                        }
                    }
            }
        ).reduce { a, l -> a + l }
            .map { list ->
                AllWalletsAccount(list, defaultLabels) as AccountGroup
            }.toSingle()

    fun allWalletsWithActions(
        actions: Set<AssetAction>,
        sorter: AccountsSorter = { Single.just(it) }
    ): Single<SingleAccountList> =
        allWallets()
            .flattenAsObservable { it.accounts }
            .flatMapMaybe { account ->
                account.actions.flatMapMaybe { availableActions ->
                    if (availableActions.containsAll(actions)) Maybe.just(account) else Maybe.empty()
                }
            }
            .toList()
            .flatMap { list ->
                sorter(list)
            }

    fun getTransactionTargets(
        sourceAccount: CryptoAccount,
        action: AssetAction
    ): Single<SingleAccountList> {
        val sameCurrencyTransactionTargets =
            get(sourceAccount.asset).transactionTargets(sourceAccount)

        val fiatTargets = fiatAsset.accountGroup(AssetFilter.All)
            .map {
                it.accounts
            }.defaultIfEmpty(emptyList())

        val sameCurrencyPlusFiat = sameCurrencyTransactionTargets
            .zipWith(fiatTargets) { crypto, fiat ->
                crypto + fiat
            }

        return allWallets().map { it.accounts }
            .flatMap { allWallets ->
                if (action != AssetAction.Swap) {
                    sameCurrencyPlusFiat
                } else {
                    Single.just(allWallets)
                }
            }.map {
                it.filter(getActionFilter(action, sourceAccount))
                    .filter { target -> target != sourceAccount }
            }
    }

    private fun getActionFilter(
        action: AssetAction,
        sourceAccount: CryptoAccount
    ): (SingleAccount) -> Boolean =
        when (action) {
            AssetAction.Sell -> {
                {
                    it is FiatAccount
                }
            }

            AssetAction.Swap -> {
                {
                    it is CryptoAccount &&
                        it.asset != sourceAccount.asset &&
                        it !is FiatAccount &&
                        it !is InterestAccount &&
                        if (sourceAccount.isCustodial()) it.isCustodial() else true
                }
            }
            AssetAction.Send -> {
                {
                    it !is FiatAccount
                }
            }
            AssetAction.InterestWithdraw -> {
                {
                    it is CryptoAccount && it.asset == sourceAccount.asset
                }
            }
            else -> {
                { true }
            }
        }

    fun findAccountByAddress(
        asset: AssetInfo,
        address: String
    ): Maybe<SingleAccount> =
        filterAccountsByAddress(
            this[asset].accountGroup(AssetFilter.All),
            address
        )

    private fun filterAccountsByAddress(
        accountGroup: Maybe<AccountGroup>,
        address: String
    ): Maybe<SingleAccount> =
        accountGroup.map {
            it.accounts
        }.flattenAsObservable { it }
            .flatMapSingle { account ->
                account.receiveAddress
                    .map { it as CryptoAddress }
                    .onErrorReturn { NullCryptoAddress }
                    .map { cryptoAccount ->
                        when {
                            cryptoAccount.address.equals(address, true) -> account
                            account.doesAddressBelongToWallet(address) -> account
                            else -> NullCryptoAccount()
                        }
                    }
            }.filter { it != NullCryptoAccount() }
            .toList()
            .flatMapMaybe {
                if (it.isEmpty())
                    Maybe.empty()
                else
                    Maybe.just(it.first())
            }

    fun createTransactionProcessor(
        source: BlockchainAccount,
        target: TransactionTarget,
        action: AssetAction
    ): Single<TransactionProcessor> =
        txProcessorFactory.createProcessor(
            source,
            target,
            action
        )

    // TODO: Fix this; we don't need to call exchangeRate any more, getPricesWith24hDelta() is enough
    fun getExchangePriceWithDelta(asset: AssetInfo): Single<ExchangePriceWithDelta> =
        this[asset].exchangeRate().zipWith(
            this[asset].getPricesWith24hDelta()
        ) { currentPrice, priceDelta ->
            ExchangePriceWithDelta(currentPrice.price(), priceDelta.delta24h)
        }

    @Suppress("SameParameterValue")
    private fun allAccounts(includeArchived: Boolean = false): Observable<SingleAccount> =
        allWallets(includeArchived).map { it.accounts }
            .flattenAsObservable { it }

    fun isLabelUnique(label: String): Single<Boolean> =
        allAccounts(true)
            .filter { a -> a.label.compareTo(label, true) == 0 }
            .toList()
            .map { it.isEmpty() }

    fun activeCryptoAssets(): List<CryptoAsset> = assetLoader.loadedAssets.toList()
    fun availableCryptoAssets(): List<AssetInfo> = assetCatalogue.supportedCryptoAssets

    fun supportedFiatAssets(): List<String> = assetCatalogue.supportedFiatAssets
}
