package piuk.blockchain.android.ui.sell

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.blockchain.koin.scopedInject
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.SimpleBuyEligibilityProvider
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.service.TierService
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.preferences.CurrencyPrefs
import piuk.blockchain.android.urllinks.URL_CONTACT_SUPPORT
import info.blockchain.balance.AssetInfo
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.kotlin.zipWith
import io.reactivex.rxjava3.schedulers.Schedulers
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.Coincore
import com.blockchain.coincore.CryptoAccount
import piuk.blockchain.android.databinding.SellIntroFragmentBinding
import piuk.blockchain.android.simplebuy.BuySellType
import piuk.blockchain.android.simplebuy.BuySellViewedEvent
import piuk.blockchain.android.ui.base.ViewPagerFragment
import piuk.blockchain.android.ui.customviews.ButtonOptions
import piuk.blockchain.android.ui.customviews.IntroHeaderView
import piuk.blockchain.android.ui.customviews.VerifyIdentityNumericBenefitItem
import piuk.blockchain.android.ui.customviews.account.CellDecorator
import piuk.blockchain.android.ui.home.HomeNavigator
import piuk.blockchain.android.ui.transactionflow.flow.TransactionFlowActivity
import piuk.blockchain.android.ui.transfer.AccountsSorting
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.trackProgress
import piuk.blockchain.android.util.visible

class SellIntroFragment : ViewPagerFragment() {
    interface SellIntroHost {
        fun onSellFinished()
        fun onSellInfoClicked()
        fun onSellListEmptyCta()
    }

    private val host: SellIntroHost by lazy {
        parentFragment as? SellIntroHost ?: throw IllegalStateException(
            "Host fragment is not a SellIntroHost"
        )
    }

    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        host.onSellFinished()
        loadSellDetails(showLoader = false)
    }

    private var _binding: SellIntroFragmentBinding? = null
    private val binding: SellIntroFragmentBinding
        get() = _binding!!

    private val tierService: TierService by scopedInject()
    private val coincore: Coincore by scopedInject()
    private val custodialWalletManager: CustodialWalletManager by scopedInject()
    private val eligibilityProvider: SimpleBuyEligibilityProvider by scopedInject()
    private val currencyPrefs: CurrencyPrefs by inject()
    private val analytics: Analytics by inject()
    private val accountsSorting: AccountsSorting by scopedInject()
    private val compositeDisposable = CompositeDisposable()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SellIntroFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSellDetails()
    }

    private fun loadSellDetails(showLoader: Boolean = true) {
        binding.accountsList.activityIndicator = if (showLoader) activityIndicator else null

        compositeDisposable += tierService.tiers()
            .zipWith(eligibilityProvider.isEligibleForSimpleBuy(forceRefresh = true))
            .subscribeOn(Schedulers.io())
            .doOnSubscribe {
                binding.sellEmpty.gone()
            }
            .observeOn(AndroidSchedulers.mainThread())
            .trackProgress(binding.accountsList.activityIndicator)
            .subscribeBy(onSuccess = { (kyc, eligible) ->
                when {
                    kyc.isApprovedFor(KycTierLevel.GOLD) && eligible -> {
                        renderKycedUserUi()
                    }
                    kyc.isRejectedFor(KycTierLevel.GOLD) -> {
                        renderRejectedKycedUserUi()
                    }
                    kyc.isApprovedFor(KycTierLevel.GOLD) && !eligible -> {
                        renderRejectedKycedUserUi()
                    }
                    else -> {
                        renderNonKycedUserUi()
                    }
                }
            }, onError = {
                renderSellError()
            })
    }

    private fun renderSellError() {
        with(binding) {
            accountsList.gone()
            sellEmpty.setDetails {
                loadSellDetails()
            }
            sellEmpty.visible()
        }
    }

    private fun renderSellEmpty() {
        with(binding) {
            accountsList.gone()
            sellEmpty.setDetails(
                R.string.sell_intro_empty_title,
                R.string.sell_intro_empty_label,
                ctaText = R.string.buy_now
            ) {
                host.onSellListEmptyCta()
            }
            sellEmpty.visible()
        }
    }

    private fun renderRejectedKycedUserUi() {
        with(binding) {
            kycBenefits.visible()
            accountsList.gone()

            kycBenefits.initWithBenefits(
                benefits = listOf(
                    VerifyIdentityNumericBenefitItem(
                        getString(R.string.invalid_id),
                        getString(R.string.invalid_id_description)
                    ), VerifyIdentityNumericBenefitItem(
                        getString(R.string.information_missmatch),
                        getString(R.string.information_missmatch_description)
                    ),
                    VerifyIdentityNumericBenefitItem(
                        getString(R.string.blocked_by_local_laws),
                        getString(R.string.sell_intro_kyc_subtitle_3)
                    )
                ),
                title = getString(R.string.unable_to_verify_id),
                description = getString(R.string.unable_to_verify_id_description),
                icon = R.drawable.ic_cart,
                secondaryButton = ButtonOptions(true, getString(R.string.contact_support)) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL_CONTACT_SUPPORT)))
                },
                primaryButton = ButtonOptions(false) {},
                showSheetIndicator = false,
                footerText = getString(R.string.error_contact_support)
            )
        }
    }

    private fun renderNonKycedUserUi() {
        with(binding) {
            kycBenefits.visible()
            accountsList.gone()

            kycBenefits.initWithBenefits(
                benefits = listOf(
                    VerifyIdentityNumericBenefitItem(
                        getString(R.string.sell_intro_kyc_title_1),
                        getString(R.string.sell_intro_kyc_subtitle_1)
                    ), VerifyIdentityNumericBenefitItem(
                        getString(R.string.sell_intro_kyc_title_2),
                        getString(R.string.sell_intro_kyc_subtitle_2)
                    ),
                    VerifyIdentityNumericBenefitItem(
                        getString(R.string.sell_intro_kyc_title_3),
                        getString(R.string.sell_intro_kyc_subtitle_3)
                    )
                ),
                title = getString(R.string.sell_crypto),
                description = getString(R.string.sell_crypto_subtitle),
                icon = R.drawable.ic_cart,
                secondaryButton = ButtonOptions(false) {},
                primaryButton = ButtonOptions(true) {
                    (activity as? HomeNavigator)?.launchKyc(CampaignType.SimpleBuy)
                },
                showSheetIndicator = false
            )
        }
    }

    private fun renderKycedUserUi() {
        with(binding) {
            kycBenefits.gone()
            accountsList.visible()

            compositeDisposable += supportedCryptoCurrencies()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .trackProgress(binding.accountsList.activityIndicator)
                .subscribeBy(onSuccess = { supportedCryptos ->
                    val introHeaderView = IntroHeaderView(requireContext())
                    introHeaderView.setDetails(
                        icon = R.drawable.ic_sell_minus,
                        label = R.string.select_wallet_to_sell,
                        title = R.string.sell_for_cash
                    )

                    accountsList.initialise(
                        coincore.allWalletsWithActions(
                            setOf(AssetAction.Sell),
                            accountsSorting.sorter()
                        ).map {
                            it.filterIsInstance<CryptoAccount>().filter { account ->
                                supportedCryptos.contains(account.asset)
                            }
                        },
                        status = ::statusDecorator,
                        introView = introHeaderView
                    )

                    renderSellInfo()

                    accountsList.onAccountSelected = { account ->
                        (account as? CryptoAccount)?.let {
                            startSellFlow(it)
                        }
                    }

                    accountsList.onListLoaded = {
                        if (it) renderSellEmpty()
                    }
                }, onError = {
                    renderSellError()
                })
        }
    }

    private fun renderSellInfo() {
        val sellInfoIntro = getString(R.string.sell_info_blurb_1)
        val sellInfoBold = getString(R.string.sell_info_blurb_2)
        val sellInfoEnd = getString(R.string.sell_info_blurb_3)

        val sb = SpannableStringBuilder()
            .append(sellInfoIntro)
            .append(sellInfoBold)
            .append(sellInfoEnd)
        sb.setSpan(
            StyleSpan(Typeface.BOLD), sellInfoIntro.length, sellInfoIntro.length + sellInfoBold.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun statusDecorator(account: BlockchainAccount): CellDecorator = SellCellDecorator(account)

    private fun startSellFlow(it: CryptoAccount) {
        analytics.logEvent(BuySellViewedEvent(BuySellType.SELL))

        startForResult.launch(
            TransactionFlowActivity.newInstance(
            context = requireActivity(),
            sourceAccount = it,
            action = AssetAction.Sell
            )
        )
    }

    private fun supportedCryptoCurrencies(): Single<List<AssetInfo>> {
        val availableFiats =
            custodialWalletManager.getSupportedFundsFiats(currencyPrefs.selectedFiatCurrency)
        return custodialWalletManager.getSupportedBuySellCryptoCurrencies()
            .zipWith(availableFiats) { supportedPairs, fiats ->
                supportedPairs.pairs.filter { fiats.contains(it.fiatCurrency) }
                    .map { it.cryptoCurrency }
            }
    }

    override fun onResumeFragment() {
        loadSellDetails(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        compositeDisposable.clear()
        _binding = null
    }

    companion object {
        private const val TX_FLOW_REQUEST = 123

        fun newInstance() = SellIntroFragment()
    }
}