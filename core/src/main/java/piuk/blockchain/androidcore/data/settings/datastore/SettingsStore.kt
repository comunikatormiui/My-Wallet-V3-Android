package piuk.blockchain.androidcore.data.settings.datastore

import info.blockchain.wallet.api.data.Settings
import io.reactivex.rxjava3.core.Observable
import com.blockchain.utils.Optional

interface SettingsStore {

    fun getSettings(): Observable<Optional<Settings>>
}