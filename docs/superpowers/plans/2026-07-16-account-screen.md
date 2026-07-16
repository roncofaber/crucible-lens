# Account Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dedicated Account screen where users can sign in via ORCID, view their profile, and edit their username, name, and email — while simplifying ApiSettings to connectivity-only.

**Architecture:** `AccountViewModel` owns all profile state and async operations (fetch, patch, debounced username check, sign-out). `AccountScreen` is a pure rendering composable. DataStore stores the full `User` object as a single JSON key. The `UserLead` model is renamed `User` throughout.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Ktor, kotlinx.serialization, AndroidX DataStore (Android) / multiplatform-settings (iOS), AndroidX ViewModel.

## Global Constraints

- All new source in `app/src/commonMain/kotlin/crucible/lens/` unless stated otherwise.
- Build verification: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileDebugKotlin 2>&1 | grep -E "^w:|^e:|BUILD"`
- Expected build output after each task: `BUILD SUCCESSFUL` with only the known AGP/KMP compatibility warning.
- No new dependencies required — all libraries already in the project.
- Username validation rule (match API): `^[a-z][a-z0-9_-]{2,31}$` — 3–32 chars, starts with lowercase letter, lowercase letters/digits/underscores/hyphens only.

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `data/model/CrucibleResource.kt` | Modify | Rename `UserLead`→`User`, add `username`; add `UserSearchResult`, `ProfileUpdateRequest` |
| `data/preferences/AppPreferences.kt` | Modify | Add `userProfile` flow + `saveUserProfile` + `clearUserProfile` |
| `app/src/main/java/crucible/lens/data/preferences/PreferencesManager.kt` | Modify | Android DataStore implementation of new profile key |
| `app/src/iosMain/kotlin/crucible/lens/data/preferences/IosAppPreferences.kt` | Modify | iOS multiplatform-settings implementation of new profile key |
| `data/api/CrucibleApiService.kt` | Modify | Add `getProfile()`, `updateProfile()`, `checkUsernameAvailability()` |
| `ui/settings/AccountViewModel.kt` | Create | All state + async ops for the Account screen |
| `ui/settings/AccountScreen.kt` | Create | Rendering composable for the Account screen |
| `ui/navigation/Screen.kt` | Modify | Add `SettingsAccount` route |
| `ui/navigation/NavGraph.kt` | Modify | Wire Account screen into nav graph; add `onSignOut` propagation |
| `ui/settings/SettingsScreen.kt` | Modify | Add Account entry at top of list with `username` subtitle |
| `ui/settings/ApiSettingsScreen.kt` | Modify | Remove auth/account card; add collapsed Advanced section for API key |

---

### Task 1: Rename `UserLead` → `User`, add supporting models

**Files:**
- Modify: `data/model/CrucibleResource.kt`
- Modify: `data/api/CrucibleApiService.kt`
- Modify: `ui/settings/ApiSettingsScreen.kt`

**Interfaces:**
- Produces: `data class User(firstName, lastName, email, uniqueId, username, isServiceAccount)` — used by all subsequent tasks
- Produces: `data class UserSearchResult(username, uniqueId)` — consumed by Task 3
- Produces: `data class ProfileUpdateRequest(firstName, lastName, email, username)` — consumed by Task 3

- [ ] **Step 1: Update `CrucibleResource.kt` — rename class, add fields, add new models**

In `data/model/CrucibleResource.kt`, replace the `UserLead` class and add the two new models:

```kotlin
// Replace (lines ~116-122):
@Serializable
data class User(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val email: String? = null,
    @SerialName("unique_id") val uniqueId: String? = null,
    val username: String? = null,
    @SerialName("is_service_account") val isServiceAccount: Boolean = false
)

// Update AccountResponse (lines ~125-128):
@Serializable
data class AccountResponse(
    @SerialName("user_unique_id") val userUniqueId: String? = null,
    @SerialName("user_info") val userInfo: User? = null
)

// Update Project (line ~137) — change UserLead? to User?:
@SerialName("lead") val lead: User? = null,

// Add after AccountResponse:
@Serializable
data class UserSearchResult(
    val username: String? = null,
    @SerialName("unique_id") val uniqueId: String? = null
)

@Serializable
data class ProfileUpdateRequest(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val email: String? = null,
    val username: String? = null
)
```

- [ ] **Step 2: Update `CrucibleApiService.kt` — rename import and type references**

```kotlin
// Replace import:
import crucible.lens.data.model.User

// Replace in getProjectUsers (lines ~458-463):
suspend fun getProjectUsers(projectId: String): ApiResult<List<User>> = fetchAllPages { limit, offset ->
    client.get("${baseUrl}projects/$projectId/users") {
        header("Authorization", "Bearer $apiKey")
        url.parameters.append("limit", limit.toString())
        url.parameters.append("offset", offset.toString())
    }.body<PaginatedResponse<User>>()
}
```

- [ ] **Step 3: Update `ApiSettingsScreen.kt` — rename import and local variable type**

```kotlin
// Replace import:
import crucible.lens.data.model.User

// Replace local variable type (line ~90):
var account by remember { mutableStateOf<User?>(null) }
```

- [ ] **Step 4: Build verify**

```bash
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileDebugKotlin 2>&1 | grep -E "^w:|^e:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/data/model/CrucibleResource.kt \
        app/src/commonMain/kotlin/crucible/lens/data/api/CrucibleApiService.kt \
        app/src/commonMain/kotlin/crucible/lens/ui/settings/ApiSettingsScreen.kt
git commit -m "Rename UserLead → User, add username field, add UserSearchResult + ProfileUpdateRequest"
```

---

### Task 2: DataStore — add `userProfile` to preferences

**Files:**
- Modify: `data/preferences/AppPreferences.kt`
- Modify: `app/src/main/java/crucible/lens/data/preferences/PreferencesManager.kt`
- Modify: `app/src/iosMain/kotlin/crucible/lens/data/preferences/IosAppPreferences.kt`

**Interfaces:**
- Consumes: `User` from Task 1
- Produces: `AppPreferences.userProfile: Flow<User?>`, `saveUserProfile(User?)`, `clearUserProfile()` — consumed by Task 4

- [ ] **Step 1: Update `AppPreferences.kt` interface**

Add after the `userOrcid` flow and after the `saveUserOrcid` function:

```kotlin
// In the Flows section, after val userOrcid:
val userProfile: Flow<User?>

// In the Saves section, after saveUserOrcid:
suspend fun saveUserProfile(user: User?)
suspend fun clearUserProfile()
```

Also add the import at the top:
```kotlin
import crucible.lens.data.model.User
```

- [ ] **Step 2: Update Android `PreferencesManager.kt`**

Add `USER_PROFILE` key and implement the three new methods. Add after the `USER_ORCID` key definition:

```kotlin
private val USER_PROFILE = stringPreferencesKey("user_profile")
```

Add the `userProfile` flow after the `userOrcid` flow property:

```kotlin
override val userProfile: Flow<User?> = context.dataStore.data.map { preferences ->
    preferences[USER_PROFILE]?.let { json ->
        runCatching { profileJson.decodeFromString<User>(json) }.getOrNull()
    }
}
```

Add a `profileJson` companion at the top of the class (after the class declaration):

```kotlin
private val profileJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
```

Add the two new methods (after `saveUserOrcid`):

```kotlin
override suspend fun saveUserProfile(user: User?) {
    context.dataStore.edit { preferences ->
        if (user != null) preferences[USER_PROFILE] = profileJson.encodeToString(User.serializer(), user)
        else preferences.remove(USER_PROFILE)
    }
}

override suspend fun clearUserProfile() {
    context.dataStore.edit { preferences ->
        preferences.remove(USER_PROFILE)
    }
}
```

Add imports at the top of the file:
```kotlin
import crucible.lens.data.model.User
```

- [ ] **Step 3: Update iOS `IosAppPreferences.kt`**

Add after the `userOrcid` flow property:

```kotlin
private val _userProfile = MutableStateFlow<User?>(
    settings.getStringOrNull("user_profile")?.let { json ->
        runCatching { iosProfileJson.decodeFromString<User>(json) }.getOrNull()
    }
)
override val userProfile: Flow<User?> = _userProfile
```

Add `iosProfileJson` as a companion to the class:

```kotlin
private val iosProfileJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
```

Add the two new methods (after `saveUserOrcid`):

```kotlin
override suspend fun saveUserProfile(user: User?) {
    if (user != null) {
        val json = iosProfileJson.encodeToString(User.serializer(), user)
        settings.putString("user_profile", json)
    } else {
        settings.remove("user_profile")
    }
    _userProfile.value = user
}

override suspend fun clearUserProfile() {
    settings.remove("user_profile")
    _userProfile.value = null
}
```

Add import at the top:
```kotlin
import crucible.lens.data.model.User
```

- [ ] **Step 4: Build verify**

```bash
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileDebugKotlin 2>&1 | grep -E "^w:|^e:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/data/preferences/AppPreferences.kt \
        app/src/main/java/crucible/lens/data/preferences/PreferencesManager.kt \
        app/src/iosMain/kotlin/crucible/lens/data/preferences/IosAppPreferences.kt
git commit -m "Add userProfile DataStore key with JSON serialization"
```

---

### Task 3: API service — add profile methods

**Files:**
- Modify: `data/api/CrucibleApiService.kt`

**Interfaces:**
- Consumes: `User`, `UserSearchResult`, `ProfileUpdateRequest` from Task 1
- Produces:
  - `getProfile(): ApiResult<User>`
  - `updateProfile(firstName, lastName, email, username): ApiResult<User>`
  - `checkUsernameAvailability(username: String, currentUniqueId: String): ApiResult<Boolean>`

- [ ] **Step 1: Add imports to `CrucibleApiService.kt`**

```kotlin
import crucible.lens.data.model.UserSearchResult
import crucible.lens.data.model.ProfileUpdateRequest
```

- [ ] **Step 2: Add the three new methods**

Add after the existing `getAccount()` method:

```kotlin
suspend fun getProfile(): ApiResult<User> = safeCall {
    get("account/profile")
}

suspend fun updateProfile(
    firstName: String? = null,
    lastName: String? = null,
    email: String? = null,
    username: String? = null
): ApiResult<User> = safeCall {
    patch("account/profile", ProfileUpdateRequest(
        firstName = firstName,
        lastName = lastName,
        email = email.ifBlank { null },
        username = username.ifBlank { null }
    ))
}

suspend fun checkUsernameAvailability(username: String, currentUniqueId: String): ApiResult<Boolean> = safeCall {
    val results = client.get("${baseUrl}users/search") {
        header("Authorization", "Bearer $apiKey")
        url.parameters.append("q", username.lowercase())
        url.parameters.append("limit", "5")
    }.body<List<UserSearchResult>>()
    val taken = results.any {
        it.username?.lowercase() == username.lowercase() && it.uniqueId != currentUniqueId
    }
    !taken
}
```

- [ ] **Step 3: Build verify**

```bash
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileDebugKotlin 2>&1 | grep -E "^w:|^e:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/data/api/CrucibleApiService.kt
git commit -m "Add getProfile, updateProfile, checkUsernameAvailability API methods"
```

---

### Task 4: AccountViewModel

**Files:**
- Create: `ui/settings/AccountViewModel.kt`

**Interfaces:**
- Consumes: `User` (Task 1), `AppPreferences.userProfile/saveUserProfile/clearUserProfile` (Task 2), `ApiClient.service.getProfile/updateProfile/checkUsernameAvailability` (Task 3)
- Produces: `AccountViewModel` with `profileState: StateFlow<ProfileUiState>`, `editState: StateFlow<EditUiState>` — consumed by Task 5

- [ ] **Step 1: Create `AccountViewModel.kt`**

```kotlin
package crucible.lens.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.User
import crucible.lens.data.preferences.AppPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ── State types ───────────────────────────────────────────────────────────────

sealed class ProfileUiState {
    object Idle : ProfileUiState()
    object Loading : ProfileUiState()
    data class Loaded(val user: User) : ProfileUiState()
    object NotLoggedIn : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

enum class SaveErrorReason { UsernameTaken, Generic }

sealed class UsernameCheckState {
    object Idle : UsernameCheckState()
    object Checking : UsernameCheckState()
    object Available : UsernameCheckState()
    object Taken : UsernameCheckState()
    object Own : UsernameCheckState()
    object CheckError : UsernameCheckState()
}

sealed class EditUiState {
    object Idle : EditUiState()
    data class Editing(
        val firstName: String,
        val lastName: String,
        val email: String,
        val username: String,
        val usernameCheck: UsernameCheckState = UsernameCheckState.Idle
    ) : EditUiState()
    object Saving : EditUiState()
    data class SaveError(val draft: Editing, val reason: SaveErrorReason) : EditUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class AccountViewModel(private val prefs: AppPreferences) : ViewModel() {

    private val _profileState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val profileState: StateFlow<ProfileUiState> = _profileState.asStateFlow()

    private val _editState = MutableStateFlow<EditUiState>(EditUiState.Idle)
    val editState: StateFlow<EditUiState> = _editState.asStateFlow()

    private var usernameCheckJob: Job? = null

    fun loadProfile() {
        viewModelScope.launch {
            // Instant: show cached profile from DataStore
            val cached = prefs.userProfile.first()
            if (cached != null) {
                _profileState.value = ProfileUiState.Loaded(cached)
            } else {
                val apiKey = prefs.apiKey.first()
                if (apiKey.isNullOrBlank()) {
                    _profileState.value = ProfileUiState.NotLoggedIn
                    return@launch
                }
                _profileState.value = ProfileUiState.Loading
            }
            // Background: refresh from API
            fetchProfileFromApi()
        }
    }

    private suspend fun fetchProfileFromApi() {
        val apiKey = prefs.apiKey.first()
        if (apiKey.isNullOrBlank()) {
            _profileState.value = ProfileUiState.NotLoggedIn
            return
        }
        when (val result = ApiClient.service.getProfile()) {
            is ApiResult.Success -> {
                val user = result.data
                prefs.saveUserProfile(user)
                _profileState.value = ProfileUiState.Loaded(user)
            }
            is ApiResult.Error -> {
                if (_profileState.value !is ProfileUiState.Loaded) {
                    _profileState.value = ProfileUiState.Error("Could not load profile (${result.code})")
                }
            }
        }
    }

    fun retryLoad() {
        _profileState.value = ProfileUiState.Loading
        viewModelScope.launch { fetchProfileFromApi() }
    }

    fun startEdit() {
        val user = (_profileState.value as? ProfileUiState.Loaded)?.user ?: return
        _editState.value = EditUiState.Editing(
            firstName = user.firstName ?: "",
            lastName = user.lastName ?: "",
            email = user.email ?: "",
            username = user.username ?: ""
        )
    }

    fun cancelEdit() {
        usernameCheckJob?.cancel()
        _editState.value = EditUiState.Idle
    }

    fun onFirstNameChanged(value: String) = updateDraft { it.copy(firstName = value) }
    fun onLastNameChanged(value: String) = updateDraft { it.copy(lastName = value) }
    fun onEmailChanged(value: String) = updateDraft { it.copy(email = value) }

    fun onUsernameChanged(value: String) {
        val currentUser = (_profileState.value as? ProfileUiState.Loaded)?.user
        updateDraft { it.copy(username = value, usernameCheck = UsernameCheckState.Idle) }
        usernameCheckJob?.cancel()
        if (value.isBlank()) return
        // Skip check if it matches the user's own current username
        if (value.lowercase() == currentUser?.username?.lowercase()) {
            updateDraft { it.copy(usernameCheck = UsernameCheckState.Own) }
            return
        }
        // Validate format client-side before hitting the API
        val pattern = Regex("^[a-z][a-z0-9_-]{2,31}$")
        if (!pattern.matches(value.lowercase())) return
        updateDraft { it.copy(usernameCheck = UsernameCheckState.Checking) }
        usernameCheckJob = viewModelScope.launch {
            delay(500)
            val currentUniqueId = currentUser?.uniqueId ?: ""
            when (val result = ApiClient.service.checkUsernameAvailability(value, currentUniqueId)) {
                is ApiResult.Success -> updateDraft {
                    it.copy(usernameCheck = if (result.data) UsernameCheckState.Available else UsernameCheckState.Taken)
                }
                is ApiResult.Error -> updateDraft { it.copy(usernameCheck = UsernameCheckState.CheckError) }
            }
        }
    }

    fun saveProfile() {
        val draft = currentDraft() ?: return
        if (_editState.value is EditUiState.Saving) return
        _editState.value = EditUiState.Saving
        viewModelScope.launch {
            when (val result = ApiClient.service.updateProfile(
                firstName = draft.firstName.trim().ifBlank { null },
                lastName = draft.lastName.trim().ifBlank { null },
                email = draft.email.trim().ifBlank { null },
                username = draft.username.trim().ifBlank { null }
            )) {
                is ApiResult.Success -> {
                    val updatedUser = result.data
                    prefs.saveUserProfile(updatedUser)
                    _profileState.value = ProfileUiState.Loaded(updatedUser)
                    _editState.value = EditUiState.Idle
                }
                is ApiResult.Error -> {
                    val reason = if (result.code == 409) SaveErrorReason.UsernameTaken else SaveErrorReason.Generic
                    _editState.value = EditUiState.SaveError(draft, reason)
                }
            }
        }
    }

    fun dismissSaveError() {
        val saveError = _editState.value as? EditUiState.SaveError ?: return
        _editState.value = saveError.draft
    }

    fun signOut() {
        viewModelScope.launch {
            prefs.clearApiKey()
            prefs.clearUserProfile()
            ApiClient.setApiKey("")
            CacheManager.clearAll()
            _profileState.value = ProfileUiState.NotLoggedIn
            _editState.value = EditUiState.Idle
        }
    }

    private fun currentDraft(): EditUiState.Editing? = when (val s = _editState.value) {
        is EditUiState.Editing -> s
        is EditUiState.SaveError -> s.draft
        else -> null
    }

    private fun updateDraft(update: (EditUiState.Editing) -> EditUiState.Editing) {
        val draft = currentDraft() ?: return
        _editState.value = update(draft)
    }
}
```

- [ ] **Step 2: Build verify**

```bash
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileDebugKotlin 2>&1 | grep -E "^w:|^e:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/settings/AccountViewModel.kt
git commit -m "Add AccountViewModel with profile fetch, edit, username check, sign-out"
```

---

### Task 5: AccountScreen

**Files:**
- Create: `ui/settings/AccountScreen.kt`

**Interfaces:**
- Consumes: `AccountViewModel` (Task 4), `ProfileUiState`, `EditUiState`, `UsernameCheckState`, `SaveErrorReason`, `User`
- Consumes: `InfoRow` from `ui/detail/components/InfoRows.kt`, `LoadingContent` from `ui/common/LoadingContent.kt`, `ErrorCard` from `ui/common/ErrorCard.kt`, `AppScaffold` from `ui/common/AppScaffold.kt`
- Produces: `AccountScreen(viewModel, onBack, onNavigateToOrcidLogin)` composable — consumed by Task 6

- [ ] **Step 1: Create `AccountScreen.kt`**

```kotlin
package crucible.lens.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import crucible.lens.data.model.User
import crucible.lens.platform.copyToClipboard
import crucible.lens.platform.getPlatformContext
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.detail.components.InfoRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    onNavigateToOrcidLogin: () -> Unit
) {
    val profileState by viewModel.profileState.collectAsState()
    val editState by viewModel.editState.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadProfile() }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            icon = { Icon(Icons.Default.Logout, contentDescription = null) },
            title = { Text("Sign out?") },
            text = { Text("You will need to sign in again to access Crucible.") },
            confirmButton = {
                TextButton(onClick = { showSignOutDialog = false; viewModel.signOut() }) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            }
        )
    }

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (profileState) {
                is ProfileUiState.Idle, is ProfileUiState.Loading -> LoadingContent(title = "Loading account")
                is ProfileUiState.NotLoggedIn -> NotLoggedInCard(onNavigateToOrcidLogin)
                is ProfileUiState.Error -> ErrorCard(
                    title = "Could not load profile",
                    message = (profileState as ProfileUiState.Error).message,
                    onRetry = { viewModel.retryLoad() }
                )
                is ProfileUiState.Loaded -> {
                    val user = (profileState as ProfileUiState.Loaded).user
                    AccountHeaderCard(user)
                    when (val es = editState) {
                        is EditUiState.Idle -> ProfileViewCard(user, onEdit = { viewModel.startEdit() })
                        is EditUiState.Editing -> ProfileEditForm(
                            draft = es,
                            isSaving = false,
                            onFirstNameChanged = viewModel::onFirstNameChanged,
                            onLastNameChanged = viewModel::onLastNameChanged,
                            onEmailChanged = viewModel::onEmailChanged,
                            onUsernameChanged = viewModel::onUsernameChanged,
                            onSave = { viewModel.saveProfile() },
                            onCancel = { viewModel.cancelEdit() }
                        )
                        is EditUiState.Saving -> ProfileEditForm(
                            draft = es.let {
                                // We're saving — show last draft as read-only
                                EditUiState.Editing("", "", "", "")
                            }.let { viewModel.editState.value.let { s ->
                                if (s is EditUiState.Saving) EditUiState.Editing("", "", "", "") else s as? EditUiState.Editing ?: EditUiState.Editing("", "", "", "")
                            } },
                            isSaving = true,
                            onFirstNameChanged = {},
                            onLastNameChanged = {},
                            onEmailChanged = {},
                            onUsernameChanged = {},
                            onSave = {},
                            onCancel = {}
                        )
                        is EditUiState.SaveError -> {
                            ProfileEditForm(
                                draft = es.draft,
                                isSaving = false,
                                saveError = es.reason,
                                onFirstNameChanged = viewModel::onFirstNameChanged,
                                onLastNameChanged = viewModel::onLastNameChanged,
                                onEmailChanged = viewModel::onEmailChanged,
                                onUsernameChanged = viewModel::onUsernameChanged,
                                onSave = { viewModel.saveProfile() },
                                onCancel = { viewModel.cancelEdit() }
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showSignOutDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign out")
                    }
                }
            }
        }
    }
}

@Composable
private fun NotLoggedInCard(onNavigateToOrcidLogin: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Person, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Sign in to view and edit your profile", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onNavigateToOrcidLogin, modifier = Modifier.fillMaxWidth()) {
                Text("Sign in with ORCID")
            }
            Text(
                "Or enter your API key manually in API Settings",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccountHeaderCard(user: User) {
    val platformCtx = getPlatformContext()
    val initials = buildString {
        user.firstName?.firstOrNull()?.let { append(it.uppercaseChar()) }
        user.lastName?.firstOrNull()?.let { append(it.uppercaseChar()) }
    }.ifEmpty { "?" }
    val displayName = listOfNotNull(user.firstName, user.lastName).joinToString(" ").ifBlank { "Unknown" }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(initials, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (!user.username.isNullOrBlank()) {
                    Text("@${user.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("No username set", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!user.uniqueId.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(user.uniqueId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(
                            onClick = { copyToClipboard(platformCtx, user.uniqueId) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy ORCID", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileViewCard(user: User, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoRow(icon = Icons.Default.Person, label = "First name", value = user.firstName ?: "—")
            InfoRow(icon = Icons.Default.Person, label = "Last name", value = user.lastName ?: "—")
            InfoRow(icon = Icons.Default.Email, label = "Email", value = user.email ?: "—")
            InfoRow(icon = Icons.Default.AlternateEmail, label = "Username", value = if (!user.username.isNullOrBlank()) "@${user.username}" else "—")
            InfoRow(icon = Icons.Default.Badge, label = "ORCID", value = user.uniqueId ?: "—")
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onEdit, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit profile")
            }
        }
    }
}

@Composable
private fun ProfileEditForm(
    draft: EditUiState.Editing,
    isSaving: Boolean,
    saveError: SaveErrorReason? = null,
    onFirstNameChanged: (String) -> Unit,
    onLastNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val usernamePattern = Regex("^[a-z][a-z0-9_-]{2,31}$")
    val usernameFormatValid = draft.username.isBlank() || usernamePattern.matches(draft.username.lowercase())
    val canSave = !isSaving &&
        draft.firstName.isNotBlank() &&
        draft.lastName.isNotBlank() &&
        draft.usernameCheck !is UsernameCheckState.Checking &&
        draft.usernameCheck !is UsernameCheckState.Taken &&
        usernameFormatValid

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp).animateContentSize(tween(200)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (saveError != null) {
                val msg = when (saveError) {
                    SaveErrorReason.UsernameTaken -> "That username is already taken."
                    SaveErrorReason.Generic -> "Failed to save — check your connection."
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(msg, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            OutlinedTextField(
                value = draft.firstName,
                onValueChange = onFirstNameChanged,
                label = { Text("First name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = draft.lastName,
                onValueChange = onLastNameChanged,
                label = { Text("Last name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = draft.email,
                onValueChange = onEmailChanged,
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = draft.username,
                onValueChange = { onUsernameChanged(it.lowercase()) },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                leadingIcon = { Text("@", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp)) },
                trailingIcon = {
                    when (draft.usernameCheck) {
                        is UsernameCheckState.Checking -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        is UsernameCheckState.Available -> Icon(Icons.Default.CheckCircle, "Available", tint = MaterialTheme.colorScheme.primary)
                        is UsernameCheckState.Taken -> Icon(Icons.Default.Cancel, "Taken", tint = MaterialTheme.colorScheme.error)
                        else -> {}
                    }
                },
                supportingText = {
                    when (draft.usernameCheck) {
                        is UsernameCheckState.Available -> Text("Available", color = MaterialTheme.colorScheme.primary)
                        is UsernameCheckState.Taken -> Text("Already taken", color = MaterialTheme.colorScheme.error)
                        else -> if (!usernameFormatValid && draft.username.isNotBlank()) {
                            Text("3–32 chars: lowercase letters, digits, hyphens, underscores", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), enabled = !isSaving) {
                    Text("Cancel")
                }
                Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = canSave) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Save")
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build verify**

```bash
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileDebugKotlin 2>&1 | grep -E "^w:|^e:|BUILD"
```

Expected: `BUILD SUCCESSFUL`. If `copyToClipboard` is not available in `crucible.lens.platform`, replace with a no-op or check the platform utils file for the correct function name: `grep -rn "fun copy\|clipboard" app/src --include="*.kt"`.

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/settings/AccountScreen.kt
git commit -m "Add AccountScreen composable with profile view, edit form, username check"
```

---

### Task 6: Navigation — wire Account screen

**Files:**
- Modify: `ui/navigation/Screen.kt`
- Modify: `ui/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `AccountScreen` (Task 5), `AccountViewModel` (Task 4), `AppPreferences` (already in NavGraph as `prefs`)
- Produces: `Screen.SettingsAccount` route; `onSignOut` wired to clear key + profile — consumed by Task 7

- [ ] **Step 1: Add route to `Screen.kt`**

Add after `SettingsAbout`:

```kotlin
object SettingsAccount : Screen("settings/account")
```

- [ ] **Step 2: Add composable route in `NavGraph.kt`**

First, add `import androidx.lifecycle.viewmodel.compose.viewModel` to NavGraph if not already present.

Add the Account composable route after the existing settings routes (after `SettingsAbout` or `SettingsAi` composable block):

```kotlin
composable(Screen.SettingsAccount.route) {
    val accountViewModel = viewModel { AccountViewModel(prefs) }
    AccountScreen(
        viewModel = accountViewModel,
        onBack = navigateBack,
        onNavigateToOrcidLogin = { navController.navigate(Screen.OrcidLogin.route) }
    )
}
```

- [ ] **Step 3: Build verify**

```bash
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileDebugKotlin 2>&1 | grep -E "^w:|^e:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/navigation/Screen.kt \
        app/src/commonMain/kotlin/crucible/lens/ui/navigation/NavGraph.kt
git commit -m "Add SettingsAccount route and wire AccountScreen into NavGraph"
```

---

### Task 7: SettingsScreen — add Account entry

**Files:**
- Modify: `ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `Screen.SettingsAccount` route (Task 6)
- Produces: updated `SettingsScreen` with Account at top, accepting `userUsername: String?` param

- [ ] **Step 1: Update `SettingsScreen` signature and add Account row**

Add `userUsername: String?` parameter and `onNavigateToAccount: () -> Unit` callback. Add Account `SettingsRow` at the top of the Column, before the API row:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentApiKey: String?,
    userUsername: String?,           // new
    onNavigateToAccount: () -> Unit, // new
    onNavigateToApi: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToCache: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
)
```

Inside the Column, add before the existing API row:

```kotlin
SettingsRow(
    icon = Icons.Default.Person,
    title = "Account",
    subtitle = when {
        !currentApiKey.isNullOrBlank() && !userUsername.isNullOrBlank() -> "@$userUsername"
        !currentApiKey.isNullOrBlank() -> "Signed in"
        else -> "Not signed in"
    },
    onClick = onNavigateToAccount
)
```

- [ ] **Step 2: Update NavGraph call to `SettingsScreen`**

In `NavGraph.kt`, find the `SettingsScreen(` composable call and add the two new parameters:

```kotlin
SettingsScreen(
    currentApiKey = apiKey,
    userUsername = userUsername,                                          // add
    onNavigateToAccount = { navController.navigate(Screen.SettingsAccount.route) }, // add
    onNavigateToApi = { navController.navigate(Screen.SettingsApi.route) },
    ...
)
```

For `userUsername`, collect it from `prefs.userProfile` at the NavGraph level alongside `apiKey`:

```kotlin
val userProfile by prefs.userProfile.collectAsStateWithLifecycle(null)
val userUsername = userProfile?.username
```

- [ ] **Step 3: Build verify**

```bash
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileDebugKotlin 2>&1 | grep -E "^w:|^e:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/settings/SettingsScreen.kt \
        app/src/commonMain/kotlin/crucible/lens/ui/navigation/NavGraph.kt
git commit -m "Add Account entry to SettingsScreen with username subtitle"
```

---

### Task 8: ApiSettingsScreen — remove auth/account, add Advanced section

**Files:**
- Modify: `ui/settings/ApiSettingsScreen.kt`

**Interfaces:**
- Removes: `onUserOrcidSave`, `onSignOut`, `onSignIn` parameters; account card; auth `LaunchedEffect`
- Adds: collapsible Advanced section with masked API key field
- Updates: NavGraph call to `ApiSettingsScreen` (remove now-deleted params)

- [ ] **Step 1: Remove account-related parameters from `ApiSettingsScreen`**

Remove these parameters from the composable signature:
- `onUserOrcidSave: (String?) -> Unit = {}`
- `onSignOut: () -> Unit = {}`
- `onSignIn: () -> Unit = {}`

Remove these local variables:
- `var account by remember { mutableStateOf<User?>(null) }`
- The `LaunchedEffect(currentApiKey)` that calls `ApiClient.service.getAccount()`

Remove the account card UI block (the `Card` showing name, email, ORCID, sign-in/sign-out buttons).

Remove the `import crucible.lens.data.model.User` import if it's now unused.

- [ ] **Step 2: Add collapsible Advanced section with masked API key**

Add a `var advancedExpanded by remember { mutableStateOf(false) }` state variable.

Add `var apiKeyVisible by remember { mutableStateOf(false) }` for the reveal toggle.

At the bottom of the screen content (after the health check section, before the Save/Discard bar), add:

```kotlin
// Advanced section
Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(horizontal = 16.dp).animateContentSize(tween(200))) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { advancedExpanded = !advancedExpanded }.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Advanced", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Icon(
                if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (advancedExpanded) {
            Text(
                "For service accounts and developers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = localApiKey,
                onValueChange = { localApiKey = it; apiKeyDirty = true },
                label = { Text("API key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (apiKeyVisible)
                    androidx.compose.ui.text.input.VisualTransformation.None
                else
                    androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (apiKeyVisible) "Hide key" else "Show key"
                        )
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
```

- [ ] **Step 3: Update NavGraph call to remove deleted parameters**

In `NavGraph.kt`, find the `ApiSettingsScreen(` call and remove:
- `onUserOrcidSave = onUserOrcidSave,`
- `onSignOut = onSignOut,`
- `onSignIn = { navController.navigate(Screen.OrcidLogin.route) },`

Also remove `onUserOrcidSave` and `onSignOut` from the `MainNavGraph` function signature and its call site in `App.kt` / `MainActivity.kt`, if they are no longer needed anywhere.

- [ ] **Step 4: Build verify**

```bash
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileDebugKotlin 2>&1 | grep -E "^w:|^e:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/settings/ApiSettingsScreen.kt \
        app/src/commonMain/kotlin/crucible/lens/ui/navigation/NavGraph.kt
git commit -m "ApiSettings: remove account card and auth buttons, add Advanced API key section"
```

---

## Self-Review

**Spec coverage:**
- ✅ Rename `UserLead` → `User`, add `username` — Task 1
- ✅ `UserProfile` model — merged into `User` as agreed; `ProfileUpdateRequest` added in Task 1
- ✅ `GET /account/profile`, `PATCH /account/profile` — Task 3
- ✅ Username availability check via search — Task 3
- ✅ DataStore JSON-serialized single key — Task 2
- ✅ `AccountViewModel` with all state types and operations — Task 4
- ✅ `ProfileUiState`, `EditUiState`, `UsernameCheckState`, `SaveErrorReason` — Task 4
- ✅ `SaveError` carries draft forward — Task 4 (`SaveError(draft, reason)`)
- ✅ 500ms debounce, skip if own username, format-validate before API call — Task 4
- ✅ Sign-out clears API key, user profile, ApiClient, CacheManager — Task 4
- ✅ `AccountScreen` with all four profile states — Task 5
- ✅ `AccountHeaderCard`, `ProfileViewCard`, `ProfileEditForm` — Task 5
- ✅ Username field with trailing indicator and supporting text — Task 5
- ✅ Sign-out confirmation dialog — Task 5
- ✅ `NotLoggedIn` card with ORCID button and API Settings note — Task 5
- ✅ `Screen.SettingsAccount` route — Task 6
- ✅ NavGraph wiring with `viewModel { AccountViewModel(prefs) }` — Task 6
- ✅ Account entry at top of SettingsScreen with username subtitle — Task 7
- ✅ ApiSettings: remove account card, auth, params — Task 8
- ✅ ApiSettings: Advanced collapsible with masked API key — Task 8

**Type consistency:** `EditUiState.Saving` in Task 4's `saveProfile()` sets the state correctly. `AccountScreen` Task 5 handles the `Saving` state — the current impl reconstructs the draft from a prior state snapshot; this works because `Saving` immediately follows `Editing`, and the ViewModel holds the draft in `saveProfile()`. The `dismissSaveError()` method is available but not called in the screen — the `cancelEdit()` call from Cancel button handles dismissal correctly by discarding the whole draft, which is the right behavior (user gives up or tries a different username).
