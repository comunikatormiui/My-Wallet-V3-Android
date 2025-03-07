package com.blockchain.core.chains.erc20

import com.blockchain.core.chains.erc20.call.Erc20BalanceCallCache
import com.blockchain.core.chains.erc20.call.Erc20HistoryCallCache
import com.blockchain.core.chains.erc20.model.Erc20Balance
import com.blockchain.core.chains.erc20.model.Erc20HistoryList
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.isErc20
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import org.web3j.abi.TypeEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.crypto.RawTransaction
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import java.math.BigInteger

interface Erc20DataManager {
    val accountHash: String
    val requireSecondPassword: Boolean

    fun getEthBalance(): Single<CryptoValue>

    fun getErc20History(asset: AssetInfo): Single<Erc20HistoryList>

    fun createErc20Transaction(
        asset: AssetInfo,
        to: String,
        amount: BigInteger,
        gasPriceWei: BigInteger,
        gasLimitGwei: BigInteger
    ): Single<RawTransaction>

    fun signErc20Transaction(
        rawTransaction: RawTransaction,
        secondPassword: String = ""
    ): Single<ByteArray>

    fun pushErc20Transaction(signedTxBytes: ByteArray): Single<String>

    fun supportsErc20TxNote(asset: AssetInfo): Boolean
    fun getErc20TxNote(asset: AssetInfo, txHash: String): String?
    fun putErc20TxNote(asset: AssetInfo, txHash: String, note: String): Completable

    fun hasUnconfirmedTransactions(): Single<Boolean>
    fun latestBlockNumber(): Single<BigInteger>
    fun isContractAddress(address: String): Single<Boolean>

    fun getErc20Balance(asset: AssetInfo): Observable<Erc20Balance>
    fun getActiveAssets(): Single<Set<AssetInfo>>

    // TODO: Get assets with balance
    fun flushCaches(asset: AssetInfo)
}

internal class Erc20DataManagerImpl(
    private val ethDataManager: EthDataManager,
    private val balanceCallCache: Erc20BalanceCallCache,
    private val historyCallCache: Erc20HistoryCallCache
) : Erc20DataManager {

    override val accountHash: String
        get() = ethDataManager.accountAddress

    override val requireSecondPassword: Boolean
        get() = ethDataManager.requireSecondPassword

    override fun getEthBalance(): Single<CryptoValue> =
        ethDataManager.fetchEthAddress()
            .firstOrError()
            .map { CryptoValue(CryptoCurrency.ETHER, it.getTotalBalance()) }
            .map { it }

    override fun getErc20Balance(asset: AssetInfo): Observable<Erc20Balance> {
        require(asset.isErc20())
        requireNotNull(asset.l2identifier)

        return balanceCallCache.getBalances(accountHash)
            .map { it.getOrDefault(asset, Erc20Balance.zero(asset)) }
            .toObservable()
    }

    override fun getActiveAssets(): Single<Set<AssetInfo>> =
        balanceCallCache.getBalances(accountHash)
            .map { it.keys }

    override fun getErc20History(asset: AssetInfo): Single<Erc20HistoryList> {
        require(asset.isErc20())
        return historyCallCache.fetch(accountHash, asset)
    }

    override fun supportsErc20TxNote(asset: AssetInfo): Boolean {
        require(asset.isErc20())
        return ethDataManager.getErc20TokenData(asset) != null
    }

    override fun getErc20TxNote(asset: AssetInfo, txHash: String): String? {
        require(asset.isErc20())
        return ethDataManager.getErc20TokenData(asset)?.txNotes?.get(txHash)
    }

    override fun putErc20TxNote(asset: AssetInfo, txHash: String, note: String): Completable {
        require(asset.isErc20())
        return ethDataManager.updateErc20TransactionNotes(asset, txHash, note)
    }

    override fun isContractAddress(address: String): Single<Boolean> =
        ethDataManager.isContractAddress(address)

    override fun createErc20Transaction(
        asset: AssetInfo,
        to: String,
        amount: BigInteger,
        gasPriceWei: BigInteger,
        gasLimitGwei: BigInteger
    ): Single<RawTransaction> {
        require(asset.isErc20())

        return ethDataManager.getNonce()
            .map { nonce ->
                val contractAddress = asset.l2identifier
                checkNotNull(contractAddress)

                RawTransaction.createTransaction(
                    nonce,
                    gasPriceWei,
                    gasLimitGwei,
                    contractAddress,
                    0.toBigInteger(),
                    erc20TransferMethod(to, amount)
                )
            }
    }

    private fun erc20TransferMethod(to: String, amount: BigInteger): String {
        val transferMethodHex = "0xa9059cbb"

        return transferMethodHex + TypeEncoder.encode(Address(to)) +
            TypeEncoder.encode(org.web3j.abi.datatypes.generated.Uint256(amount))
    }

    override fun signErc20Transaction(
        rawTransaction: RawTransaction,
        secondPassword: String
    ): Single<ByteArray> =
        ethDataManager.signEthTransaction(rawTransaction, secondPassword)

    override fun pushErc20Transaction(signedTxBytes: ByteArray): Single<String> =
        ethDataManager.pushEthTx(signedTxBytes).singleOrError()

    override fun hasUnconfirmedTransactions(): Single<Boolean> =
        ethDataManager.isLastTxPending()

    override fun latestBlockNumber(): Single<BigInteger> =
        ethDataManager.getLatestBlockNumber().map { it.number }

    override fun flushCaches(asset: AssetInfo) {
        require(asset.isErc20())

        balanceCallCache.flush(asset)
        historyCallCache.flush(asset)
    }
}
