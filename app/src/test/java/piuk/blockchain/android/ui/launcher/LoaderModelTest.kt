package piuk.blockchain.android.ui.launcher

import com.blockchain.android.testutils.rxInit
import com.blockchain.nabu.Feature
import com.blockchain.nabu.UserIdentity
import com.blockchain.preferences.AuthPrefs
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import piuk.blockchain.android.ui.launcher.loader.LoaderActivity
import piuk.blockchain.android.ui.launcher.loader.LoaderIntents
import piuk.blockchain.android.ui.launcher.loader.LoaderInteractor
import piuk.blockchain.android.ui.launcher.loader.LoaderModel
import piuk.blockchain.android.ui.launcher.loader.LoaderState
import piuk.blockchain.android.ui.launcher.loader.LoaderStep
import piuk.blockchain.android.ui.launcher.loader.ProgressStep
import piuk.blockchain.android.ui.launcher.loader.ToastType
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.PersistentPrefs

@RunWith(MockitoJUnitRunner::class)
class LoaderModelTest {
    private lateinit var model: LoaderModel

    private val environmentConfig: EnvironmentConfig = mock {
        on { isRunningInDebugMode() }.thenReturn(false)
    }

    private val interactor: LoaderInteractor = mock()
    private val appUtil: AppUtil = mock()
    private val payloadDataManager: PayloadDataManager = mock()
    private val userIdentity: UserIdentity = mock()
    private val prerequisites: Prerequisites = mock()
    private val prefs: PersistentPrefs = mock()
    private val authPrefs: AuthPrefs = mock()

    @get:Rule
    val rx = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    @Before
    fun setUp() {
        model = LoaderModel(
            initialState = LoaderState(),
            mainScheduler = Schedulers.io(),
            environmentConfig = environmentConfig,
            crashLogger = mock(),
            interactor = interactor,
            analytics = mock(),
            appUtil = appUtil,
            payloadDataManager = payloadDataManager,
            prerequisites = prerequisites,
            userIdentity = userIdentity,
            prefs = prefs,
            authPrefs = authPrefs
        )
    }

    @Test
    fun `initSettings if accessState is logged in`() {
        // Arrange
        val isPinValidated = true
        val isAfterWalletCreation = false
        whenever(authPrefs.walletGuid).thenReturn(WALLET_GUID)
        whenever(prefs.pinId).thenReturn(PIN_ID)

        model.process(LoaderIntents.CheckIsLoggedIn(isPinValidated, isAfterWalletCreation))

        // Assert
        verify(interactor).initSettings(isAfterWalletCreation)
    }

    @Test
    fun `start LauncherActivity if not logged in and PIN not validated`() {
        // Arrange
        val isPinValidated = false
        val isAfterWalletCreation = false
        val testState = model.state.test()

        whenever(authPrefs.walletGuid).thenReturn("")

        model.process(LoaderIntents.CheckIsLoggedIn(isPinValidated, isAfterWalletCreation))

        // Assert
        testState
            .assertValues(
                LoaderState(),
                LoaderState(nextLoaderStep = LoaderStep.Launcher)
            )
    }

    @Test
    fun `OnEmailVerificationFinished launches MainActivity with isUserEligible as false`() {
        // Arrange
        val isUserEligible = false
        whenever(userIdentity.isEligibleFor(Feature.SimplifiedDueDiligence)).thenReturn(Single.just(isUserEligible))
        val testState = model.state.test()

        model.process(LoaderIntents.OnEmailVerificationFinished)

        // Assert
        verify(userIdentity).isEligibleFor(Feature.SimplifiedDueDiligence)
        testState
            .assertValues(
                LoaderState(),
                LoaderState(nextLoaderStep = LoaderStep.Main(null, isUserEligible))
            )
    }

    @Test
    fun `OnEmailVerificationFinished launches MainActivity with isUserEligible as true`() {
        // Arrange
        val isUserEligible = true
        whenever(userIdentity.isEligibleFor(Feature.SimplifiedDueDiligence)).thenReturn(Single.just(isUserEligible))
        val testState = model.state.test()

        model.process(LoaderIntents.OnEmailVerificationFinished)

        // Assert
        verify(userIdentity).isEligibleFor(Feature.SimplifiedDueDiligence)
        testState
            .assertValues(
                LoaderState(),
                LoaderState(nextLoaderStep = LoaderStep.Main(null, isUserEligible))
            )
    }

    @Test
    fun `DecryptAndSetupMetadata with invalid second password loads invalid password toast and shows dialog again`() {
        // Arrange
        val secondPassword = "test"
        whenever(payloadDataManager.validateSecondPassword(secondPassword)).thenReturn(false)
        val testState = model.state.test()

        model.process(LoaderIntents.DecryptAndSetupMetadata(secondPassword))

        // Assert
        verify(payloadDataManager).validateSecondPassword(secondPassword)
        testState
            .assertValues(
                LoaderState(),
                LoaderState(toastType = ToastType.INVALID_PASSWORD),
                LoaderState(toastType = null),
                LoaderState(shouldShowSecondPasswordDialog = true),
                LoaderState(shouldShowSecondPasswordDialog = false)
            )
    }

    @Test
    fun `DecryptAndSetupMetadata with valid second password calls decrypt and setup metadata successfully`() {
        // Arrange
        val secondPassword = "test"
        whenever(payloadDataManager.validateSecondPassword(secondPassword)).thenReturn(true)
        whenever(prerequisites.decryptAndSetupMetadata(secondPassword)).thenReturn(Completable.complete())
        val testState = model.state.test()

        model.process(LoaderIntents.DecryptAndSetupMetadata(secondPassword))

        // Assert
        verify(payloadDataManager).validateSecondPassword(secondPassword)
        verify(prerequisites).decryptAndSetupMetadata(secondPassword)
        verify(appUtil).loadAppWithVerifiedPin(LoaderActivity::class.java)

        testState
            .assertValues(
                LoaderState(),
                LoaderState(nextProgressStep = ProgressStep.DECRYPTING_WALLET),
                LoaderState(nextProgressStep = ProgressStep.FINISH)
            )
    }

    @Test
    fun `DecryptAndSetupMetadata with valid second password calls decrypt and setup metadata with error`() {
        // Arrange
        val secondPassword = "test"
        whenever(payloadDataManager.validateSecondPassword(secondPassword)).thenReturn(true)
        whenever(prerequisites.decryptAndSetupMetadata(secondPassword)).thenReturn(Completable.error(Throwable()))
        val testState = model.state.test()

        model.process(LoaderIntents.DecryptAndSetupMetadata(secondPassword))

        // Assert
        verify(payloadDataManager).validateSecondPassword(secondPassword)
        verify(prerequisites).decryptAndSetupMetadata(secondPassword)
        verify(appUtil, never()).loadAppWithVerifiedPin(LoaderActivity::class.java)

        testState
            .assertValues(
                LoaderState(),
                LoaderState(nextProgressStep = ProgressStep.DECRYPTING_WALLET),
                LoaderState(nextProgressStep = ProgressStep.FINISH)
            )
    }

    companion object {
        private const val WALLET_GUID = "0000-0000-0000-0000-0000"
        private const val PIN_ID = "1234"
    }
}