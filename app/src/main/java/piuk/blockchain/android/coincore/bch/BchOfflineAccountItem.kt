package piuk.blockchain.android.coincore.bch

import info.blockchain.balance.CryptoCurrency
import io.reactivex.Maybe
import kotlinx.serialization.Serializable
import piuk.blockchain.android.coincore.CachedAddress
import piuk.blockchain.android.coincore.OfflineCachedAccount
import piuk.blockchain.android.coincore.impl.OfflineBalanceCall

@Serializable
data class BchOfflineAccountItem(
    override val accountLabel: String,
    private val addressList: List<CachedAddress>
) : OfflineCachedAccount {
    override val networkTicker: String = CryptoCurrency.BCH.networkTicker

    override fun nextAddress(balanceCall: OfflineBalanceCall): Maybe<CachedAddress> =
        balanceCall.getBalanceOfAddresses(CryptoCurrency.BCH, addressList.map { it.address })
            .map {
                // If it has balance, it's been used, so drop it
                it.filterValues { v -> !v.isPositive }
                    .keys.toList()
            }.flatMapMaybe {
                if (it.isEmpty()) {
                    Maybe.empty()
                } else {
                    Maybe.just(it.first())
                }
            }.map { address ->
                addressList.find { it.address == address }
            }

    override val rawAddressList: List<String>
        get() = addressList.map { it.address }
}
