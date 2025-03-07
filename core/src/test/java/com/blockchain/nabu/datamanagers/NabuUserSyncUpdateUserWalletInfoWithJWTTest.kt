package com.blockchain.nabu.datamanagers

import com.blockchain.testutils.rxInit
import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.NabuUserSync
import com.blockchain.nabu.getBlankNabuUser
import com.blockchain.nabu.models.responses.tokenresponse.NabuOfflineTokenResponse
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.reactivex.rxjava3.core.Single

import org.junit.Rule
import org.junit.Test

class NabuUserSyncUpdateUserWalletInfoWithJWTTest {

    @get:Rule
    val initSchedulers = rxInit {
        ioTrampoline()
    }

    @Test
    fun `no interactions until subscribe`() {
        val nabuToken = mock<NabuToken>()
        val nabuDataManager = mock<NabuDataManager>()
        val nabuUserSync = givenSyncInstance(nabuDataManager, nabuToken)
        nabuUserSync
            .syncUser()
        verifyZeroInteractions(nabuToken)
        verifyZeroInteractions(nabuDataManager)
    }

    @Test
    fun `on sync user`() {
        val jwt = "JWT"
        val offlineToken = NabuOfflineTokenResponse("", "")
        val nabuToken: NabuToken = mock {
            on { fetchNabuToken() }.thenReturn(Single.just(offlineToken))
        }
        val nabuDataManager: NabuDataManager = mock {
            on { requestJwt() }.thenReturn(Single.just(jwt))
                on { updateUserWalletInfo(offlineToken, jwt) }.thenReturn(Single.just(getBlankNabuUser()))
        }

        val nabuUserSync = givenSyncInstance(nabuDataManager, nabuToken)

        nabuUserSync
            .syncUser()
            .test()
            .assertComplete()

        verify(nabuToken).fetchNabuToken()
        verifyNoMoreInteractions(nabuToken)

        verify(nabuDataManager).updateUserWalletInfo(offlineToken, jwt)
        verify(nabuDataManager).requestJwt()
        verifyNoMoreInteractions(nabuDataManager)
    }

    private fun givenSyncInstance(
        nabuDataManager: NabuDataManager,
        nabuToken: NabuToken
    ): NabuUserSync =
        NabuUserSyncUpdateUserWalletInfoWithJWT(nabuDataManager, nabuToken)
}
