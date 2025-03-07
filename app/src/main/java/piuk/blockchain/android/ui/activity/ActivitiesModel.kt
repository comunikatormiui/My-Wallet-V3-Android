package piuk.blockchain.android.ui.activity

import com.blockchain.logging.CrashLogger
import info.blockchain.balance.AssetInfo
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import com.blockchain.coincore.ActivitySummaryList
import com.blockchain.coincore.BlockchainAccount
import piuk.blockchain.android.ui.base.mvi.MviModel
import piuk.blockchain.android.ui.base.mvi.MviState
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import timber.log.Timber

enum class ActivitiesSheet {
    ACCOUNT_SELECTOR,
    CRYPTO_ACTIVITY_DETAILS,
    FIAT_ACTIVITY_DETAILS
}

enum class CryptoActivityType {
    NON_CUSTODIAL,
    CUSTODIAL_TRADING,
    CUSTODIAL_INTEREST,
    CUSTODIAL_TRANSFER,
    SWAP,
    SELL,
    RECURRING_BUY,
    UNKNOWN
}

data class ActivitiesState(
    val account: BlockchainAccount? = null,
    val activityList: ActivitySummaryList = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshRequested: Boolean = false,
    val bottomSheet: ActivitiesSheet? = null,
    val isError: Boolean = false,
    val selectedTxId: String = "",
    val selectedCryptoCurrency: AssetInfo? = null,
    val selectedFiatCurrency: String? = null,
    val activityType: CryptoActivityType = CryptoActivityType.UNKNOWN
) : MviState

class ActivitiesModel(
    initialState: ActivitiesState,
    uiScheduler: Scheduler,
    private val interactor: ActivitiesInteractor,
    environmentConfig: EnvironmentConfig,
    crashLogger: CrashLogger
) : MviModel<ActivitiesState, ActivitiesIntent>(
    initialState,
    uiScheduler,
    environmentConfig,
    crashLogger
) {

    private var fetchSubscription: Disposable? = null

    override fun performAction(
        previousState: ActivitiesState,
        intent: ActivitiesIntent
    ): Disposable? {
        Timber.d("***> performAction: ${intent.javaClass.simpleName}")

        return when (intent) {
            is AccountSelectedIntent -> {

                fetchSubscription?.dispose()

                fetchSubscription = interactor.getActivityForAccount(intent.account, intent.isRefreshRequested)
                    .subscribeBy(
                        onNext = {
                            process(ActivityListUpdatedIntent(it))
                        },
                        onComplete = {
                            // do nothing
                        },
                        onError = {
                            process(ActivityListUpdatedErrorIntent)
                        }
                    )

                fetchSubscription
            }
            is SelectDefaultAccountIntent ->
                interactor.getDefaultAccount()
                    .subscribeBy(
                        onSuccess = { process(AccountSelectedIntent(it, false)) },
                        onError = { process(ActivityListUpdatedErrorIntent) }
                    )
            is CancelSimpleBuyOrderIntent -> interactor.cancelSimpleBuyOrder(intent.orderId)
            else -> null
        }
    }
}
