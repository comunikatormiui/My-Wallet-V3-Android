package com.blockchain.coincore.impl.txEngine

import com.blockchain.coincore.AccountBalance
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.Product
import com.blockchain.nabu.models.data.CryptoWithdrawalFeeAndLimit
import com.blockchain.testutils.lumens
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Single
import org.junit.Before
import org.junit.Test
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.CryptoAddress
import com.blockchain.coincore.FeeLevel
import com.blockchain.coincore.FeeSelection
import com.blockchain.coincore.PendingTx
import com.blockchain.coincore.TransactionTarget
import com.blockchain.coincore.ValidationState
import com.blockchain.coincore.erc20.Erc20NonCustodialAccount
import com.blockchain.coincore.impl.CryptoAccountCompoundGroupTest.Companion.testValue
import com.blockchain.coincore.testutil.CoincoreTestBase
import com.blockchain.core.price.ExchangeRate
import io.reactivex.rxjava3.core.Observable
import java.math.BigInteger
import kotlin.test.assertEquals

class TradingToOnChainTxEngineTest : CoincoreTestBase() {

    private val isNoteSupported = false
    private val walletManager: CustodialWalletManager = mock()

    private val subject = TradingToOnChainTxEngine(
        walletManager = walletManager,
        isNoteSupported = isNoteSupported
    )

    @Before
    fun setup() {
        initMocks()
    }

    @Test
    fun `inputs validate when correct`() {
        val sourceAccount = mockSourceAccount()
        val txTarget: CryptoAddress = mock {
            on { asset }.thenReturn(ASSET)
        }

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        subject.assertInputsValid()

        // Assert
        verify(txTarget).asset
        verify(sourceAccount).asset

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test(expected = IllegalStateException::class)
    fun `inputs fail validation when source Asset incorrect`() {
        val sourceAccount = mock<Erc20NonCustodialAccount> {
            on { asset }.thenReturn(WRONG_ASSET)
        }

        val txTarget: CryptoAddress = mock {
            on { asset }.thenReturn(ASSET)
        }

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        subject.assertInputsValid()

        // Assert
        verify(txTarget).asset
        verify(sourceAccount).asset

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `asset is returned correctly`() {
        // Arrange
        val sourceAccount = mockSourceAccount()
        val txTarget: CryptoAddress = mock {
            on { asset }.thenReturn(ASSET)
        }

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val asset = subject.sourceAsset

        // Assert
        assertEquals(asset, ASSET)
        verify(sourceAccount).asset

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `PendingTx is correctly initialised`() {
        // Arrange
        val totalBalance = 21.lumens()
        val actionableBalance = 20.lumens()
        val sourceAccount = mockSourceAccount(totalBalance, actionableBalance)
        val txTarget: CryptoAddress = mock {
            on { asset }.thenReturn(ASSET)
        }

        val feesAndLimits = CryptoWithdrawalFeeAndLimit(minLimit = 5000.toBigInteger(), fee = BigInteger.ONE)
        whenever(walletManager.fetchCryptoWithdrawFeeAndMinLimit(ASSET, Product.BUY))
            .thenReturn(Single.just(feesAndLimits))

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        // Act
        subject.doInitialiseTx()
            .test()
            .assertValue {
                it.amount == CryptoValue.zero(txTarget.asset) &&
                    it.totalBalance == totalBalance &&
                    it.availableBalance ==
                    actionableBalance.minus(CryptoValue.fromMinor(txTarget.asset, feesAndLimits.fee)) &&
                    it.feeForFullAvailable == CryptoValue.fromMinor(txTarget.asset, feesAndLimits.fee) &&
                    it.feeAmount == CryptoValue.fromMinor(txTarget.asset, feesAndLimits.fee) &&
                    it.selectedFiat == TEST_USER_FIAT &&
                    it.confirmations.isEmpty() &&
                    it.minLimit == CryptoValue.fromMinor(ASSET, feesAndLimits.minLimit) &&
                    it.maxLimit == null &&
                    it.validationState == ValidationState.UNINITIALISED &&
                    it.engineState.isEmpty()
            }
            .assertValue { verifyFeeLevels(it.feeSelection) }
            .assertNoErrors()
            .assertComplete()

        verify(sourceAccount, atLeastOnce()).asset
    }

    @Test
    fun `update amount modifies the pendingTx correctly`() {
        // Arrange
        val totalBalance = 21.lumens()
        val actionableBalance = 20.lumens()
        val sourceAccount = mockSourceAccount(totalBalance, actionableBalance)
        val txTarget: CryptoAddress = mock {
            on { asset }.thenReturn(ASSET)
        }

        val feesAndLimits = CryptoWithdrawalFeeAndLimit(minLimit = 5000.toBigInteger(), fee = BigInteger.ONE)
        whenever(walletManager.fetchCryptoWithdrawFeeAndMinLimit(ASSET, Product.BUY))
            .thenReturn(Single.just(feesAndLimits))

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = CryptoValue.zero(txTarget.asset),
            totalBalance = totalBalance,
            availableBalance = actionableBalance.minus(
                CryptoValue.fromMinor(txTarget.asset, feesAndLimits.fee)
            ),
            feeForFullAvailable = CryptoValue.fromMinor(txTarget.asset, feesAndLimits.fee),
            feeAmount = CryptoValue.fromMinor(txTarget.asset, feesAndLimits.fee),
            selectedFiat = TEST_USER_FIAT,
            feeSelection = FeeSelection()
        )

        val inputAmount = 2.lumens()

        // Act
        subject.doUpdateAmount(
            inputAmount,
            pendingTx
        ).test()
            .assertValue {
                it.amount == inputAmount &&
                    it.totalBalance == totalBalance &&
                    it.availableBalance ==
                    actionableBalance.minus(CryptoValue.fromMinor(txTarget.asset, feesAndLimits.fee))
            }
            .assertValue { verifyFeeLevels(it.feeSelection) }
            .assertComplete()
            .assertNoErrors()

        verify(sourceAccount, atLeastOnce()).asset
        verify(sourceAccount).balance
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update fee level from NONE to PRIORITY is rejected`() {
        val totalBalance = 21.lumens()
        val actionableBalance = 20.lumens()
        val inputAmount = 2.lumens()
        val zeroPax = 0.lumens()

        val sourceAccount = mockSourceAccount(totalBalance, actionableBalance)
        val txTarget: CryptoAddress = mock {
            on { asset }.thenReturn(ASSET)
        }

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = actionableBalance,
            feeAmount = zeroPax,
            feeForFullAvailable = zeroPax,
            selectedFiat = TEST_USER_FIAT,
            feeSelection = FeeSelection()
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.Priority,
            -1
        ).test()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update fee level from NONE to REGULAR is rejected`() {
        val totalBalance = 21.lumens()
        val actionableBalance = 20.lumens()
        val inputAmount = 2.lumens()
        val zeroPax = 0.lumens()

        val sourceAccount = mockSourceAccount(totalBalance, actionableBalance)
        val txTarget: CryptoAddress = mock {
            on { asset }.thenReturn(ASSET)
        }

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = actionableBalance,
            feeForFullAvailable = zeroPax,
            feeAmount = zeroPax,
            selectedFiat = TEST_USER_FIAT,
            feeSelection = FeeSelection()
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.Regular,
            -1
        ).test()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update fee level from NONE to CUSTOM is rejected`() {
        val totalBalance = 21.lumens()
        val actionableBalance = 20.lumens()
        val inputAmount = 2.lumens()
        val zeroPax = 0.lumens()

        val sourceAccount = mockSourceAccount(totalBalance, actionableBalance)
        val txTarget: CryptoAddress = mock {
            on { asset }.thenReturn(ASSET)
        }

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = actionableBalance,
            feeForFullAvailable = zeroPax,
            feeAmount = zeroPax,
            selectedFiat = TEST_USER_FIAT,
            feeSelection = FeeSelection()
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.Custom,
            100
        ).test()
    }

    @Test
    fun `update fee level from NONE to NONE has no effect`() {
        val totalBalance = 21.lumens()
        val actionableBalance = 20.lumens()
        val inputAmount = 2.lumens()
        val zeroPax = 0.lumens()

        val sourceAccount = mockSourceAccount(totalBalance, actionableBalance)
        val txTarget: CryptoAddress = mock {
            on { asset }.thenReturn(ASSET)
        }

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val pendingTx = PendingTx(
            amount = inputAmount,
            totalBalance = totalBalance,
            availableBalance = actionableBalance,
            feeForFullAvailable = zeroPax,
            feeAmount = zeroPax,
            selectedFiat = TEST_USER_FIAT,
            feeSelection = FeeSelection()
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.None,
            -1
        ).test()
            .assertValue {
                it.amount == inputAmount &&
                    it.totalBalance == totalBalance &&
                    it.availableBalance == actionableBalance &&
                    it.feeAmount == zeroPax
            }
            .assertValue { verifyFeeLevels(it.feeSelection) }
            .assertComplete()
            .assertNoErrors()

        noMoreInteractions(sourceAccount, txTarget)
    }

    private fun mockSourceAccount(
        totalBalance: Money = CryptoValue.zero(ASSET),
        actionable: Money = CryptoValue.zero(ASSET)
    ): Erc20NonCustodialAccount {
        val accountBalance = AccountBalance(
            total = totalBalance,
            pending = 0.testValue(),
            actionable = actionable,
            exchangeRate = ExchangeRate.CryptoToFiat(
                from = TEST_ASSET,
                to = TEST_USER_FIAT,
                rate = 1.2.toBigDecimal()
            )
        )
        return mock {
            on { asset }.thenReturn(ASSET)
            on { balance }.thenReturn(
                Observable.just(
                    accountBalance
                )
            )
        }
    }

    private fun verifyFeeLevels(feeSelection: FeeSelection) =
        feeSelection.selectedLevel == FeeLevel.None &&
            feeSelection.availableLevels == setOf(FeeLevel.None) &&
            feeSelection.availableLevels.contains(feeSelection.selectedLevel) &&
            feeSelection.asset == null &&
            feeSelection.customAmount == -1L

    private fun noMoreInteractions(sourceAccount: BlockchainAccount, txTarget: TransactionTarget) {
        verifyNoMoreInteractions(txTarget)
        verifyNoMoreInteractions(sourceAccount)
        verifyNoMoreInteractions(walletManager)
        verifyNoMoreInteractions(currencyPrefs)
        verifyNoMoreInteractions(exchangeRates)
    }

    companion object {
        private val ASSET = CryptoCurrency.XLM
        private val WRONG_ASSET = CryptoCurrency.BTC
    }
}
