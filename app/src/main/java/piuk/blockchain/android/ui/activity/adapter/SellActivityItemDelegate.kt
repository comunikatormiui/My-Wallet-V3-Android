package piuk.blockchain.android.ui.activity.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.nabu.datamanagers.CurrencyPair
import info.blockchain.balance.AssetInfo
import com.blockchain.utils.toFormattedDate
import piuk.blockchain.android.R
import com.blockchain.coincore.TradeActivitySummaryItem
import piuk.blockchain.android.databinding.DialogActivitiesTxItemBinding
import piuk.blockchain.android.ui.activity.CryptoActivityType
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.context
import piuk.blockchain.android.util.getResolvedColor
import piuk.blockchain.android.util.setAssetIconColoursWithTint
import piuk.blockchain.android.util.setTransactionHasFailed
import piuk.blockchain.android.util.setTransactionIsConfirming
import java.util.Date

class SellActivityItemDelegate<in T>(
    private val onItemClicked: (AssetInfo, String, CryptoActivityType) -> Unit // crypto, txID, type
) : AdapterDelegate<T> {

    override fun isForViewType(items: List<T>, position: Int): Boolean =
        (items[position] as? TradeActivitySummaryItem)?.let {
            it.currencyPair is CurrencyPair.CryptoToFiatCurrencyPair
        } ?: false

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        SellActivityItemViewHolder(
            DialogActivitiesTxItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as SellActivityItemViewHolder).bind(
        items[position] as TradeActivitySummaryItem,
        onItemClicked
    )
}

private class SellActivityItemViewHolder(
    private val binding: DialogActivitiesTxItemBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(
        tx: TradeActivitySummaryItem,
        onAccountClicked: (AssetInfo, String, CryptoActivityType) -> Unit
    ) {
        with(binding) {
            statusDate.text = Date(tx.timeStampMs).toFormattedDate()
            (tx.currencyPair as? CurrencyPair.CryptoToFiatCurrencyPair)?.let { pair ->
                txType.text = context.resources.getString(
                    R.string.tx_title_sold,
                    pair.source.displayTicker
                )
                when {
                    tx.state.isPending -> icon.setTransactionIsConfirming()
                    tx.state.hasFailed -> icon.setTransactionHasFailed()
                    else -> {
                        icon.apply {
                            setImageResource(R.drawable.ic_tx_sell)
                            setAssetIconColoursWithTint(pair.source)
                        }
                    }
                }
                txRoot.setOnClickListener {
                    onAccountClicked(pair.source, tx.txId, CryptoActivityType.SELL)
                }
            }
            setTextColours(tx.state.isPending)
            assetBalanceCrypto.text = tx.value.toStringWithSymbol()
            assetBalanceFiat.text = tx.receivingValue.toStringWithSymbol()
        }
    }

    private fun setTextColours(isPending: Boolean) {
        with(binding) {
            if (!isPending) {
                txType.setTextColor(context.getResolvedColor(R.color.black))
                statusDate.setTextColor(context.getResolvedColor(R.color.grey_600))
                assetBalanceFiat.setTextColor(context.getResolvedColor(R.color.grey_600))
                assetBalanceCrypto.setTextColor(context.getResolvedColor(R.color.black))
            } else {
                txType.setTextColor(context.getResolvedColor(R.color.grey_400))
                statusDate.setTextColor(context.getResolvedColor(R.color.grey_400))
                assetBalanceFiat.setTextColor(context.getResolvedColor(R.color.grey_400))
                assetBalanceCrypto.setTextColor(context.getResolvedColor(R.color.grey_400))
            }
        }
    }
}