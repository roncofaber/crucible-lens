package crucible.lens.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiResult
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.repository.ResourceResult
import crucible.lens.ui.navigation.DeepLinkTarget
import crucible.lens.ui.navigation.parseScannedTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScannerResolutionState {
    data object Scanning : ScannerResolutionState
    data object Resolving : ScannerResolutionState
    data class Resolved(val target: DeepLinkTarget) : ScannerResolutionState
    data class Error(val message: String) : ScannerResolutionState
}

class ScannerViewModel(
    private val repository: CrucibleRepository
) : ViewModel() {
    private val _state = MutableStateFlow<ScannerResolutionState>(ScannerResolutionState.Scanning)
    val state: StateFlow<ScannerResolutionState> = _state.asStateFlow()

    private var resolutionJob: Job? = null

    fun scan(value: String): Boolean {
        if (_state.value is ScannerResolutionState.Resolving) return true
        resolutionJob?.cancel()
        val target = parseScannedTarget(value)
        if (target == null) {
            _state.value = ScannerResolutionState.Error("This is not a recognized Crucible resource or project code")
            return true
        }
        _state.value = ScannerResolutionState.Resolving
        resolutionJob = viewModelScope.launch {
            try {
                _state.value = when (target) {
                    is DeepLinkTarget.Resource -> when (
                        val result = repository.fetchResourceByUuid(target.resourceReference, forceRefresh = true)
                    ) {
                        is ResourceResult.Success -> ScannerResolutionState.Resolved(
                            DeepLinkTarget.Resource(result.resource.uniqueId)
                        )
                        is ResourceResult.Error -> ScannerResolutionState.Error(result.message)
                        ResourceResult.Loading -> ScannerResolutionState.Resolving
                    }
                    is DeepLinkTarget.Project -> when (
                        val result = repository.fetchProject(target.projectReference, forceRefresh = true)
                    ) {
                        is ApiResult.Success -> ScannerResolutionState.Resolved(
                            DeepLinkTarget.Project(result.data.uniqueId)
                        )
                        is ApiResult.Error -> ScannerResolutionState.Error(projectError(result.code))
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = ScannerResolutionState.Error("Connection error. Check your network and try again")
            }
        }
        return true
    }

    fun reset() {
        resolutionJob?.cancel()
        resolutionJob = null
        _state.value = ScannerResolutionState.Scanning
    }
}

internal fun projectError(code: Int): String = when (code) {
    401 -> "Sign in again to open this project"
    403 -> "You do not have permission to open this project"
    404 -> "Project not found"
    in 500..599 -> "Crucible service error ($code)"
    else -> "Could not open project ($code)"
}
