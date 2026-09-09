/* Added in FitRegimen. Licensed under GPLv3 or later. */
package de.tadris.fitness.ui.updates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.tadris.fitness.BuildConfig
import de.tadris.fitness.update.AndroidUpdateInstaller
import de.tadris.fitness.update.UpdateError
import de.tadris.fitness.update.UpdateException
import de.tadris.fitness.update.UpdateRelease
import de.tadris.fitness.update.UpdateTransfer
import de.tadris.fitness.update.verifyUpdateFile
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class UpdatePhase { IDLE, CHECKING, DOWNLOADING, CANCELLING, VERIFYING }
internal data class UpdatesState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val checked: Boolean = false,
    val release: UpdateRelease? = null,
    val progress: Int = 0,
    val apkPath: String? = null,
    val error: UpdateError? = null,
    val permissionRequired: Boolean = false,
)

internal class UpdatesViewModel(application: Application) : AndroidViewModel(application) {
    val supported = application.packageName == "com.fitregimen.app"
    private val transfer = UpdateTransfer()
    private val installer = AndroidUpdateInstaller(application)
    private val mutable = MutableStateFlow(UpdatesState())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    init { if (supported) check() }

    fun check() {
        if (!supported || mutable.value.phase != UpdatePhase.IDLE) return
        mutable.update { it.copy(phase = UpdatePhase.CHECKING, error = null, permissionRequired = false) }
        job = viewModelScope.launch {
            try {
                val release = transfer.latest()
                val cached = withContext(Dispatchers.IO) {
                    val file = File(installer.directory, "FitRegimen-${release.versionCode}.apk")
                    if (release.versionCode > BuildConfig.VERSION_CODE && file.exists()) {
                        runCatching {
                            verifyUpdateFile(file, release)
                            installer.validate(file, release.versionCode)
                            file.path
                        }.getOrElse { file.delete(); null }
                    } else null
                }
                mutable.update { it.copy(checked = true, release = release, apkPath = cached) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                // Do not keep an earlier available release after a failed refresh.
                mutable.update { it.copy(checked = true, release = null, apkPath = null,
                    error = (error as? UpdateException)?.reason ?: UpdateError.NETWORK) }
            } finally { mutable.update { it.copy(phase = UpdatePhase.IDLE) } }
        }
    }

    fun download() {
        val release = mutable.value.release ?: return
        if (!supported || mutable.value.phase != UpdatePhase.IDLE || release.versionCode <= BuildConfig.VERSION_CODE) return
        mutable.update { it.copy(phase = UpdatePhase.DOWNLOADING, progress = 0, error = null,
            apkPath = null, permissionRequired = false) }
        job = viewModelScope.launch {
            try {
                val file = transfer.download(installer.directory, release,
                    progress = { percent -> mutable.update { it.copy(progress = percent) } },
                    validate = { installer.validate(it, release.versionCode) })
                mutable.update { it.copy(apkPath = file.path) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutable.update { it.copy(error = (error as? UpdateException)?.reason ?: UpdateError.NETWORK) }
            } finally { mutable.update { it.copy(phase = UpdatePhase.IDLE) } }
        }
    }

    fun cancel() {
        if (mutable.value.phase == UpdatePhase.DOWNLOADING) {
            mutable.update { it.copy(phase = UpdatePhase.CANCELLING) }
            job?.cancel()
        }
    }

    fun install() {
        val state = mutable.value
        val release = state.release ?: return
        val path = state.apkPath ?: return
        if (state.phase != UpdatePhase.IDLE) return
        mutable.update { it.copy(phase = UpdatePhase.VERIFYING, error = null, permissionRequired = false) }
        job = viewModelScope.launch {
            try {
                val file = File(path)
                withContext(Dispatchers.IO) {
                    verifyUpdateFile(file, release)
                    installer.validate(file, release.versionCode)
                }
                val opened = installer.launch(file)
                mutable.update { it.copy(permissionRequired = !opened) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val reason = (error as? UpdateException)?.reason ?: UpdateError.INSTALLER
                mutable.update { it.copy(error = reason,
                    apkPath = if (reason == UpdateError.INSTALLER) it.apkPath else null) }
            } finally { mutable.update { it.copy(phase = UpdatePhase.IDLE) } }
        }
    }
}
