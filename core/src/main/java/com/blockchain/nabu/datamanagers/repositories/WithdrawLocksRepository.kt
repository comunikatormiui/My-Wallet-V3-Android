package com.blockchain.nabu.datamanagers.repositories

import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.blockchain.caching.ParameteredSingleTimedCacheRequest
import io.reactivex.rxjava3.core.Single
import timber.log.Timber
import java.math.BigInteger

class WithdrawLocksRepository(custodialWalletManager: CustodialWalletManager) {

    private val cache = ParameteredSingleTimedCacheRequest<WithdrawalData, BigInteger>(
        cacheLifetimeSeconds = 100L,
        refreshFn = { data ->
            custodialWalletManager.fetchWithdrawLocksTime(
                data.paymentMethodType, data.fiatCurrency
            )
                .doOnSuccess { it1 -> Timber.d("Withdrawal lock: $it1") }
        }
    )

    fun getWithdrawLockTypeForPaymentMethod(
        paymentMethodType: PaymentMethodType,
        fiatCurrency: String
    ): Single<BigInteger> =
        cache.getCachedSingle(
            WithdrawalData(paymentMethodType, fiatCurrency)
        )
            .onErrorReturn { BigInteger.ZERO }

    private data class WithdrawalData(
        val paymentMethodType: PaymentMethodType,
        val fiatCurrency: String
    )
}