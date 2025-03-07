package piuk.blockchain.android.ui.transfer.send

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.LaunchOrigin
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.CryptoAccount
import io.reactivex.rxjava3.disposables.CompositeDisposable
import piuk.blockchain.android.simplebuy.BuySellClicked
import piuk.blockchain.android.simplebuy.BuySellType
import piuk.blockchain.android.ui.customviews.account.CellDecorator
import piuk.blockchain.android.ui.customviews.account.DefaultCellDecorator
import piuk.blockchain.android.ui.home.HomeNavigator
import piuk.blockchain.android.ui.transactionflow.analytics.SendAnalyticsEvent
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalyticsAccountType
import piuk.blockchain.android.ui.transactionflow.flow.TransactionFlowActivity
import piuk.blockchain.android.ui.transfer.AccountSelectorFragment
import piuk.blockchain.android.ui.transfer.analytics.TransferAnalyticsEvent

class TransferSendFragment : AccountSelectorFragment() {

    private val analytics: Analytics by inject()
    private val compositeDisposable = CompositeDisposable()
    private val startActivityForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshItems(showLoader = false)
    }

    override val fragmentAction: AssetAction
        get() = AssetAction.Send

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderList()
    }

    override fun onPause() {
        compositeDisposable.clear()
        super.onPause()
    }

    private fun renderList() {
        setEmptyStateDetails(
            R.string.transfer_wallets_empty_title,
            R.string.transfer_wallets_empty_details,
            R.string.transfer_wallet_buy_crypto
        ) {
            analytics.logEvent(TransferAnalyticsEvent.NoBalanceCtaClicked)
            analytics.logEvent(BuySellClicked(origin = LaunchOrigin.SEND, type = BuySellType.BUY))
            (activity as? HomeNavigator)?.launchBuySell()
        }

        initialiseAccountSelectorWithHeader(
            statusDecorator = ::statusDecorator,
            onAccountSelected = ::doOnAccountSelected,
            title = R.string.transfer_send_crypto_title,
            label = R.string.transfer_send_crypto_label,
            icon = R.drawable.ic_send_blue_circle
        )
    }

    private fun statusDecorator(account: BlockchainAccount): CellDecorator =
        if (account is CryptoAccount) {
            SendCellDecorator(account)
        } else {
            DefaultCellDecorator()
        }

    private fun doOnAccountSelected(account: BlockchainAccount) {
        require(account is CryptoAccount)

        analytics.logEvent(TransferAnalyticsEvent.SourceWalletSelected(account))
        analytics.logEvent(
            SendAnalyticsEvent.SendSourceAccountSelected(
                currency = account.asset.networkTicker,
                fromAccountType = TxFlowAnalyticsAccountType.fromAccount(
                    account
                )
            )
        )
        startTransactionFlow(account)
    }

    private fun startTransactionFlow(fromAccount: CryptoAccount) {
        startActivityForResult.launch(
            TransactionFlowActivity.newInstance(
                context = requireActivity(),
                sourceAccount = fromAccount,
                action = AssetAction.Send
            )
        )
    }

    override fun doOnEmptyList() {
        super.doOnEmptyList()
        analytics.logEvent(TransferAnalyticsEvent.NoBalanceViewDisplayed)
    }

    companion object {
        fun newInstance() = TransferSendFragment()
    }
}
