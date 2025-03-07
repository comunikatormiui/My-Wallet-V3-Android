package piuk.blockchain.android.simplebuy

import com.blockchain.banking.BankPartnerCallbackProvider
import com.blockchain.banking.BankTransferAction
import com.blockchain.nabu.models.data.BankPartner
import piuk.blockchain.android.BuildConfig
import java.lang.IllegalStateException

class BankPartnerCallbackProviderImpl : BankPartnerCallbackProvider {
    override fun callback(partner: BankPartner, action: BankTransferAction): String =
        when (partner) {
            BankPartner.YODLEE -> throw IllegalStateException("Partner $partner doesn't support deeplink callbacks")
            BankPartner.YAPILY -> yapilyCallback(action)
        }

    private fun yapilyCallback(action: BankTransferAction): String =
        when (action) {
            BankTransferAction.LINK -> "https://${BuildConfig.DEEPLINK_HOST}/oblinking"
            BankTransferAction.PAY -> "https://${BuildConfig.DEEPLINK_HOST}/obapproval"
        }
}