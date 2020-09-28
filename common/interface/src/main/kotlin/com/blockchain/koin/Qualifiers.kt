package com.blockchain.koin

import org.koin.core.qualifier.StringQualifier
import org.koin.core.qualifier.named

val pitFeatureFlag = StringQualifier("ff_pit_linking")
val simpleBuyFeatureFlag = StringQualifier("ff_simple_buy")
val simpleBuyFundsFeatureFlag = StringQualifier("ff_simple_buy_funds")
val sellFeatureFlag = StringQualifier("ff_sell")
val cardPaymentsFeatureFlag = StringQualifier("ff_card_payments")
val coinifyUsersToKyc = StringQualifier("ff_notify_coinify_users_to_kyc")
val coinifyFeatureFlag = StringQualifier("ff_coinify")
val pitAnnouncementFeatureFlag = StringQualifier("ff_pit_announcement")
val smsVerifFeatureFlag = StringQualifier("ff_sms_verification")
val sunriver = StringQualifier("sunriver")
val interestAccountFeatureFlag = StringQualifier("ff_interest_account")
val btcStrategy = StringQualifier("BTCStrategy")
val moshiExplorerRetrofit = StringQualifier("moshi_explorer")
val nabu = StringQualifier("nabu")
val kotlinApiRetrofit = StringQualifier("kotlin-api")
val explorerRetrofit = StringQualifier("explorer")
val everypayRetrofit = StringQualifier("everypay")
val apiRetrofit = StringQualifier("api")
val explorerUrl = StringQualifier("explorer-url")
val gbp = StringQualifier("GBP")
val eur = StringQualifier("EUR")
val btc = StringQualifier("BTC")
val bch = StringQualifier("BCH")
val xlm = StringQualifier("XLM")
val eth = StringQualifier("ETH")
val pax = StringQualifier("PAX")
val usdt = StringQualifier("USDT")
val priorityFee = StringQualifier("Priority")
val regularFee = StringQualifier("Regular")
val bigDecimal = StringQualifier("BigDecimal")
val bigInteger = StringQualifier("BigInteger")
val interestLimits = StringQualifier("InterestLimits")
val kyc = StringQualifier("kyc")
val uniqueId = StringQualifier("unique_id")
val uniqueUserAnalytics = StringQualifier("unique_user_analytics")
val userAnalytics = StringQualifier("user_analytics")
val walletAnalytics = StringQualifier("wallet_analytics")
val lockbox = StringQualifier("lockbox")
val payloadScopeQualifier = named("Payload")
val paxAccount = StringQualifier("paxAccount")
val usdtAccount = StringQualifier("usdtAccount")
