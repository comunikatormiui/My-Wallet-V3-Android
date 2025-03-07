package com.blockchain.core.user

import com.blockchain.api.services.Geolocation
import com.blockchain.api.services.NabuUserService
import com.blockchain.auth.AuthHeaderProvider
import com.blockchain.caching.TimedCacheRequest
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.service.TierService
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

interface NabuUserDataManager {

    fun getUserGeolocation(): Single<Geolocation>

    fun tiers(): Single<KycTiers>

    fun saveUserInitialLocation(countryIsoCode: String, stateIsoCode: String?): Completable
}

class NabuUserDataManagerImpl(
    private val nabuUserService: NabuUserService,
    private val authenticator: AuthHeaderProvider,
    private val tierService: TierService
) : NabuUserDataManager {

    private val refresh: () -> Single<KycTiers> = {
        tierService.tiers()
    }

    private val cacheRequest: TimedCacheRequest<KycTiers> by lazy {
        TimedCacheRequest(
            cacheLifetimeSeconds = TIERS_CACHING_LIFETIME_SECS,
            refreshFn = refresh
        )
    }

    override fun tiers(): Single<KycTiers> = cacheRequest.getCachedSingle()

    override fun getUserGeolocation(): Single<Geolocation> =
        nabuUserService.getGeolocation()

    override fun saveUserInitialLocation(countryIsoCode: String, stateIsoCode: String?): Completable =
        authenticator.getAuthHeader().map {
            nabuUserService.saveUserInitialLocation(
                it,
                countryIsoCode,
                stateIsoCode
            )
        }.flatMapCompletable { it }
}

private const val TIERS_CACHING_LIFETIME_SECS = 100L