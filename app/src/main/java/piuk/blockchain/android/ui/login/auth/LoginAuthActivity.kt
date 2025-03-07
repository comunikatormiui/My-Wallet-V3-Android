package piuk.blockchain.android.ui.login.auth

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.text.method.LinkMovementMethod
import android.util.Base64
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.blockchain.signin.UnifiedSignInEventListener
import com.blockchain.extensions.exhaustive
import com.blockchain.koin.scopedInject
import com.blockchain.logging.CrashLogger
import com.blockchain.preferences.WalletStatus
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.ActivityLoginAuthBinding
import piuk.blockchain.android.ui.auth.PinEntryActivity
import piuk.blockchain.android.ui.base.mvi.MviActivity
import piuk.blockchain.android.ui.customviews.ToastCustom
import piuk.blockchain.android.ui.customviews.VerifyIdentityNumericBenefitItem
import piuk.blockchain.android.ui.customviews.toast
import piuk.blockchain.android.ui.login.LoginAnalytics
import piuk.blockchain.android.ui.login.auth.LoginAuthState.Companion.TWO_FA_COUNTDOWN
import piuk.blockchain.android.ui.login.auth.LoginAuthState.Companion.TWO_FA_STEP
import piuk.blockchain.android.ui.recover.AccountRecoveryActivity
import piuk.blockchain.android.ui.settings.SettingsAnalytics
import piuk.blockchain.android.ui.settings.SettingsAnalytics.Companion.TWO_SET_MOBILE_NUMBER_OPTION
import piuk.blockchain.android.ui.start.ManualPairingActivity
import piuk.blockchain.android.urllinks.RESET_2FA
import piuk.blockchain.android.urllinks.SECOND_PASSWORD_EXPLANATION
import piuk.blockchain.android.util.AfterTextChangedWatcher
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.android.util.ViewUtils
import piuk.blockchain.android.util.clearErrorState
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.setErrorState
import piuk.blockchain.android.util.visible
import piuk.blockchain.androidcore.utils.extensions.isValidGuid
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class LoginAuthActivity :
    MviActivity<LoginAuthModel, LoginAuthIntents, LoginAuthState, ActivityLoginAuthBinding>(),
    AccountUnificationBottomSheet.Host {

    override val alwaysDisableScreenshots: Boolean
        get() = true

    override val model: LoginAuthModel by scopedInject()

    private val crashLogger: CrashLogger by inject()
    private val walletPrefs: WalletStatus by inject()

    private lateinit var currentState: LoginAuthState

    private var willUnifyAccount: Boolean = false
    private var isAccountRecoveryEnabled: Boolean = false
    private var email: String = ""
    private var userId: String = ""
    private var recoveryToken: String = ""
    private val compositeDisposable = CompositeDisposable()

    private val isTwoFATimerRunning = AtomicBoolean(false)
    private val twoFATimer by lazy {
        object : CountDownTimer(TWO_FA_COUNTDOWN, TWO_FA_STEP) {
            override fun onTick(millisUntilFinished: Long) {
                isTwoFATimerRunning.set(true)
            }

            override fun onFinish() {
                isTwoFATimerRunning.set(false)
                model.process(LoginAuthIntents.Reset2FARetries)
            }
        }
    }

    private val analyticsInfo: LoginAuthInfo?
        get() = if (::currentState.isInitialized) currentState.authInfoForAnalytics else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initControls()
    }

    override fun onStart() {
        super.onStart()
        processIntentData()
    }

    override fun onStop() {
        compositeDisposable.clear()
        twoFATimer.cancel()
        super.onStop()
    }

    private fun processIntentData() {
        analytics.logEvent(LoginAnalytics.DeviceVerified(analyticsInfo))

        val fragment = intent.data?.fragment ?: kotlin.run {
            model.process(LoginAuthIntents.ShowAuthRequired)
            return
        }

        // Two possible cases here, string is either a GUID or a base64 that we need to decode.
        val data = fragment.substringAfterLast(LINK_DELIMITER)
        if (data.isValidGuid()) {
            model.process(LoginAuthIntents.ShowManualPairing(data))
        } else {
            decodeBase64Payload(data)
        }
    }

    private fun decodeBase64Payload(data: String) {
        val json: String = try {
            decodeToJsonString(data)
        } catch (ex: Exception) {
            Timber.e(ex)
            crashLogger.logException(ex)
            // Fall back to legacy manual pairing
            model.process(LoginAuthIntents.ShowManualPairing(null))
            return
        }
        model.process(LoginAuthIntents.InitLoginAuthInfo(json))
    }

    private fun initControls() {
        with(binding) {
            backButton.setOnClickListener { clearKeyboardAndFinish() }
            passwordText.addTextChangedListener(object : AfterTextChangedWatcher() {
                override fun afterTextChanged(s: Editable) {
                    passwordTextLayout.clearErrorState()
                }
            })
            codeText.addTextChangedListener(object : AfterTextChangedWatcher() {
                override fun afterTextChanged(s: Editable) {
                    codeTextLayout.clearErrorState()
                }
            })
            forgotPasswordButton.setOnClickListener {
                launchPasswordRecoveryFlow()
            }

            continueButton.setOnClickListener {
                analytics.logEvent(LoginAnalytics.LoginPasswordEntered(analyticsInfo))
                if (currentState.authMethod != TwoFAMethod.OFF) {
                    model.process(
                        LoginAuthIntents.SubmitTwoFactorCode(
                            password = passwordText.text.toString(),
                            code = codeText.text.toString()
                        )
                    )
                    analytics.logEvent(LoginAnalytics.LoginTwoFaEntered(analyticsInfo))
                    analytics.logEvent(SettingsAnalytics.TwoStepVerificationCodeSubmitted(TWO_SET_MOBILE_NUMBER_OPTION))
                } else {
                    model.process(LoginAuthIntents.VerifyPassword(passwordText.text.toString()))
                }
            }

            twoFaResend.text = getString(R.string.two_factor_resend_sms, walletPrefs.resendSmsRetries)
            twoFaResend.setOnClickListener {
                if (!isTwoFATimerRunning.get()) {
                    model.process(LoginAuthIntents.RequestNew2FaCode)
                } else {
                    ToastCustom.makeText(
                        this@LoginAuthActivity, getString(R.string.two_factor_retries_exceeded),
                        Toast.LENGTH_SHORT, ToastCustom.TYPE_ERROR
                    )
                }
            }
        }
    }

    override fun initBinding(): ActivityLoginAuthBinding = ActivityLoginAuthBinding.inflate(layoutInflater)

    override fun render(newState: LoginAuthState) {
        renderAuthStatus(newState)
        updateLoginData(newState)
        update2FALayout(newState.authMethod)

        newState.twoFaState?.let {
            renderRemainingTries(it)
        }

        currentState = newState
    }

    private fun renderAuthStatus(newState: LoginAuthState) {
        when (newState.authStatus) {
            AuthStatus.None,
            AuthStatus.InitAuthInfo -> binding.progressBar.visible()
            AuthStatus.GetSessionId,
            AuthStatus.AuthorizeApproval,
            AuthStatus.GetPayload -> binding.progressBar.gone()
            AuthStatus.Submit2FA,
            AuthStatus.VerifyPassword,
            AuthStatus.UpdateMobileSetup -> binding.progressBar.visible()
            AuthStatus.AskForAccountUnification -> showUnificationBottomSheet(newState.accountType)
            AuthStatus.Complete -> {
                analytics.logEvent(LoginAnalytics.LoginRequestApproved(analyticsInfo))
                startActivity(Intent(this, PinEntryActivity::class.java))
            }
            AuthStatus.PairingFailed -> showErrorToast(R.string.pairing_failed)
            AuthStatus.InvalidPassword -> {
                analytics.logEvent(LoginAnalytics.LoginPasswordDenied(analyticsInfo))
                binding.progressBar.gone()
                binding.passwordTextLayout.setErrorState(getString(R.string.invalid_password))
            }
            AuthStatus.AuthFailed -> {
                analytics.logEvent(LoginAnalytics.LoginRequestDenied(analyticsInfo))
                showErrorToast(R.string.auth_failed)
            }
            AuthStatus.InitialError -> showErrorToast(R.string.common_error)
            AuthStatus.AuthRequired -> showToast(getString(R.string.auth_required))
            AuthStatus.Invalid2FACode -> {
                analytics.logEvent(LoginAnalytics.LoginTwoFaDenied(analyticsInfo))
                binding.progressBar.gone()
                binding.codeTextLayout.setErrorState(getString(R.string.invalid_two_fa_code))
            }
            AuthStatus.ShowManualPairing -> {
                startActivity(ManualPairingActivity.newInstance(this, newState.guid))
                clearKeyboardAndFinish()
            }
            AuthStatus.AccountLocked -> {
                AlertDialog.Builder(this, R.style.AlertDialogStyle)
                    .setTitle(R.string.account_locked_title)
                    .setMessage(R.string.account_locked_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.common_go_back) { _, _ ->
                        clearKeyboardAndFinish()
                    }
                    .create()
                    .show()
            }
        }.exhaustive
    }

    private fun clearKeyboardAndFinish() {
        ViewUtils.hideKeyboard(this)
        finish()
    }

    private fun showUnificationBottomSheet(accountType: BlockchainAccountType) {
        val benefitsList = mutableListOf(
            VerifyIdentityNumericBenefitItem(
                title = getString(R.string.unification_sheet_item_one_title),
                subtitle = getString(R.string.unification_sheet_item_one_blurb)
            ),
            VerifyIdentityNumericBenefitItem(
                title = getString(R.string.unification_sheet_item_two_title),
                subtitle = getString(R.string.unification_sheet_item_two_blurb)
            )
        )
        when (accountType) {
            BlockchainAccountType.EXCHANGE -> {
                benefitsList.add(
                    VerifyIdentityNumericBenefitItem(
                        title = getString(R.string.unification_sheet_item_three_title),
                        subtitle = getString(R.string.unification_sheet_item_three_blurb)
                    )
                )
            }
            BlockchainAccountType.WALLET_EXCHANGE_NOT_LINKED -> {
                // TODO do we need anything for this case
            }
            else -> {
                // TODO this case should never happen
            }
        }

        showBottomSheet(
            AccountUnificationBottomSheet.newInstance(benefitsList as ArrayList<VerifyIdentityNumericBenefitItem>)
        )
    }

    override fun upgradeAccountClicked() {
        willUnifyAccount = true
    }

    override fun doLaterClicked() {
        willUnifyAccount = false
    }

    override fun onSheetClosed() {
        if (willUnifyAccount) {
            showUnificationUI()
        } else {
            model.process(LoginAuthIntents.ShowAuthComplete)
        }
    }

    private fun showUnificationUI() {
        with(binding) {
            unifiedSignInWebview.initWebView(object : UnifiedSignInEventListener {
                override fun onLoaded() {
                    progressBar.gone()
                }

                override fun onError(error: String) {
                    // TODO nothing for now
                }
            })

            unifiedSignInWebview.visible()
            progressBar.visible()
        }
    }

    private fun renderRemainingTries(state: TwoFaCodeState) =
        when (state) {
            is TwoFaCodeState.TwoFaRemainingTries ->
                binding.twoFaResend.text = getString(R.string.two_factor_resend_sms, state.remainingRetries)
            is TwoFaCodeState.TwoFaTimeLock -> {
                if (!isTwoFATimerRunning.get()) {
                    twoFATimer.start()
                    ToastCustom.makeText(
                        this@LoginAuthActivity, getString(R.string.two_factor_retries_exceeded),
                        Toast.LENGTH_SHORT, ToastCustom.TYPE_ERROR
                    )
                }
                binding.twoFaResend.text = getString(R.string.two_factor_resend_sms, 0)
            }
        }

    private fun updateLoginData(currentState: LoginAuthState) {
        userId = currentState.userId
        email = currentState.email
        recoveryToken = currentState.recoveryToken
        with(binding) {
            loginEmailText.setText(email)
            loginWalletLabel.text = getString(R.string.login_wallet_id_text, currentState.guid)
            forgotPasswordButton.isEnabled = email.isNotEmpty()
        }
    }

    private fun update2FALayout(authMethod: TwoFAMethod) {
        with(binding) {
            when (authMethod) {
                TwoFAMethod.OFF -> codeTextLayout.gone()
                TwoFAMethod.YUBI_KEY -> {
                    codeTextLayout.visible()
                    codeTextLayout.hint = getString(R.string.hardware_key_hint)
                    codeText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    codeLabel.visible()
                    codeLabel.text = getString(R.string.tap_hardware_key_label)
                }
                TwoFAMethod.SMS -> {
                    codeTextLayout.visible()
                    codeTextLayout.hint = getString(R.string.two_factor_code_hint)
                    twoFaResend.visible()
                    setup2FANotice(
                        textId = R.string.lost_2fa_notice,
                        annotationForLink = RESET_2FA_LINK_ANNOTATION,
                        url = RESET_2FA
                    )
                }
                TwoFAMethod.GOOGLE_AUTHENTICATOR -> {
                    codeTextLayout.visible()
                    codeTextLayout.hint = getString(R.string.two_factor_code_hint)
                    codeText.inputType = InputType.TYPE_NUMBER_VARIATION_NORMAL
                    codeText.keyListener = DigitsKeyListener.getInstance(DIGITS)
                    setup2FANotice(
                        textId = R.string.lost_2fa_notice,
                        annotationForLink = RESET_2FA_LINK_ANNOTATION,
                        url = RESET_2FA
                    )
                }
                TwoFAMethod.SECOND_PASSWORD -> {
                    codeTextLayout.visible()
                    codeTextLayout.hint = getString(R.string.second_password_hint)
                    forgotSecondPasswordButton.visible()
                    forgotSecondPasswordButton.setOnClickListener { launchPasswordRecoveryFlow() }
                    setup2FANotice(
                        textId = R.string.second_password_notice,
                        annotationForLink = SECOND_PASSWORD_LINK_ANNOTATION,
                        url = SECOND_PASSWORD_EXPLANATION
                    )
                }
            }.exhaustive
            continueButton.setOnClickListener {
                if (authMethod != TwoFAMethod.OFF) {
                    model.process(
                        LoginAuthIntents.SubmitTwoFactorCode(
                            password = passwordText.text.toString(),
                            code = codeText.text.toString().trim()
                        )
                    )
                    analytics.logEvent(SettingsAnalytics.TwoStepVerificationCodeSubmitted(TWO_SET_MOBILE_NUMBER_OPTION))
                } else {
                    model.process(LoginAuthIntents.VerifyPassword(passwordText.text.toString()))
                }
            }
        }
    }

    private fun setup2FANotice(@StringRes textId: Int, annotationForLink: String, url: String) {
        binding.twoFaNotice.apply {
            visible()
            val links = mapOf(annotationForLink to Uri.parse(url))
            text = StringUtils.getStringWithMappedAnnotations(context, textId, links) {
                analytics.logEvent(LoginAnalytics.LoginLearnMoreClicked(analyticsInfo))
            }
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun showErrorToast(@StringRes message: Int) {
        binding.progressBar.gone()
        binding.passwordText.setText("")
        toast(message, ToastCustom.TYPE_ERROR)
    }

    private fun showToast(message: String) {
        binding.progressBar.gone()
        binding.passwordText.setText("")
        toast(message, ToastCustom.TYPE_GENERAL)
    }

    private fun launchPasswordRecoveryFlow() {
        analytics.logEvent(LoginAnalytics.LoginHelpClicked(analyticsInfo))
        val intent = Intent(this, AccountRecoveryActivity::class.java).apply {
            putExtra(EMAIL, email)
            putExtra(USER_ID, userId)
            putExtra(RECOVERY_TOKEN, recoveryToken)
        }
        startActivity(intent)
    }

    private fun decodeToJsonString(payload: String): String {
        val urlSafeEncodedData = payload.apply {
            unEscapedCharactersMap.map { entry ->
                replace(entry.key, entry.value)
            }
        }
        val decodedData = tryDecode(urlSafeEncodedData.toByteArray(Charsets.UTF_8))
        return String(decodedData)
    }

    private fun tryDecode(urlSafeEncodedData: ByteArray): ByteArray {
        return try {
            Base64.decode(
                urlSafeEncodedData,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        } catch (ex: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // The getUrlDecoder() returns the URL_SAFE Base64 decoder
                java.util.Base64.getUrlDecoder().decode(urlSafeEncodedData)
            } else {
                throw ex
            }
        }
    }

    companion object {
        const val LINK_DELIMITER = "/login/"
        private const val EMAIL = "email"
        private const val USER_ID = "user_id"
        private const val RECOVERY_TOKEN = "recovery_token"
        private const val DIGITS = "1234567890"
        private const val SECOND_PASSWORD_LINK_ANNOTATION = "learn_more"
        private const val RESET_2FA_LINK_ANNOTATION = "reset_2fa"

        private val unEscapedCharactersMap = mapOf(
            "%2B" to "+",
            "%2F" to "/",
            "%2b" to "+",
            "%2f" to "/"
        )
    }
}