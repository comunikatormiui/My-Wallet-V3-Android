package piuk.blockchain.android.ui.dashboard.announcements.rule

import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.balance.CryptoCurrency
import io.reactivex.rxjava3.core.Single
import com.nhaarman.mockitokotlin2.mock
import org.junit.Before
import org.junit.Test
import com.blockchain.coincore.Coincore
import com.blockchain.coincore.btc.BtcCryptoWalletAccount
import com.blockchain.coincore.impl.CryptoAccountNonCustodialGroup
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder

class SendToDomainAnnouncementTest {
    private val dismissRecorder: DismissRecorder = mock()
    private val coincore: Coincore = mock()
    private val dismissEntry: DismissRecorder.DismissEntry = mock()

    private lateinit var subject: SendToDomainAnnouncement

    @Before
    fun setUp() {
        whenever(dismissRecorder[SendToDomainAnnouncement.DISMISS_KEY]).thenReturn(dismissEntry)
        whenever(dismissEntry.prefsKey).thenReturn(SendToDomainAnnouncement.DISMISS_KEY)

        subject =
            SendToDomainAnnouncement(
                dismissRecorder = dismissRecorder,
                coincore = coincore
            )
    }

    @Test
    fun `should not show, when already shown`() {
        whenever(dismissEntry.isDismissed).thenReturn(true)

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should show, when not already shown and a wallet is funded`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)

        val account: BtcCryptoWalletAccount = mock()
        whenever(account.isFunded).thenReturn(true)
        val acg = CryptoAccountNonCustodialGroup(
            CryptoCurrency.BTC, "label", listOf(account)
        )
        whenever(coincore.allWallets()).thenReturn(Single.just(acg))

        subject.shouldShow()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should not show, if not already shown and no wallet is funded`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)

        val account: BtcCryptoWalletAccount = mock()
        whenever(account.isFunded).thenReturn(false)
        val acg = CryptoAccountNonCustodialGroup(
            CryptoCurrency.BTC, "label", listOf(account)
        )
        whenever(coincore.allWallets()).thenReturn(Single.just(acg))

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }
}