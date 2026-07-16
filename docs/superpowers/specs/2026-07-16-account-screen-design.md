# Account Screen Design

**Date:** 2026-07-16
**Status:** Approved

## Overview

Add a dedicated Account screen to the Settings section of Crucible Lens. It lets users sign in via ORCID, view their profile, and edit their username, name, and email. ApiSettings is simplified to connectivity-only. Sign-out clears all local data.

---

## Constraints & Assumptions

- All users are already provisioned by a Molecular Foundry administrator. Account creation from the app is not supported (`POST /users` is admin-only).
- If `GET /account/profile` returns 401/404, the screen shows "Account not found — contact your Molecular Foundry administrator" rather than a setup form.
- ORCID is the primary login path. Manual API key entry remains available in ApiSettings as an advanced option for service accounts and developers.
- The app targets Android and iOS via KMP/CMP. All new code lives in `commonMain`.

---

## Data & API Layer

### Model: rename `UserLead` → `User`

`UserLead` is renamed to `User` throughout the codebase. `username: String?` is added. Email remains nullable — the API omits it when returning other users' profiles.

```kotlin
@Serializable
data class User(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val email: String? = null,
    @SerialName("unique_id") val uniqueId: String? = null,
    val username: String? = null,
    @SerialName("is_service_account") val isServiceAccount: Boolean = false
)
```

`AccountResponse` updates its `userInfo` field to `User`.

### New API service methods

```
GET  /account/profile  → ApiResult<User>
PATCH /account/profile → ApiResult<User>
  body: { first_name?, last_name?, email?, username? }
```

Username search for availability check: `GET /users/search?q={username}&limit=5` — check if any result has `username == query` and `uniqueId != currentUser.uniqueId`.

### DataStore storage

One new key: `user_profile` (`StringPreferencesKey`). Stores the full `User` object as a JSON string serialized with `kotlinx.serialization`. Written after every successful profile fetch or save. Cleared on sign-out.

Migration: on first launch, if `user_profile` is absent but `user_orcid` is present, use `user_orcid` as a fallback for display until the next successful profile fetch overwrites it with the full object.

---

## AccountViewModel

**Location:** `ui/settings/AccountViewModel.kt`

### States

```kotlin
sealed class ProfileUiState {
    object Idle : ProfileUiState()
    object Loading : ProfileUiState()
    data class Loaded(val user: User) : ProfileUiState()
    object NotLoggedIn : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
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
    data class SaveError(val reason: SaveErrorReason) : EditUiState()
}

enum class SaveErrorReason { UsernameTaken, Generic }

sealed class UsernameCheckState {
    object Idle : UsernameCheckState()
    object Checking : UsernameCheckState()
    object Available : UsernameCheckState()
    object Taken : UsernameCheckState()
    object Own : UsernameCheckState()   // user's current username — skip check
    object CheckError : UsernameCheckState()
}
```

### Exposed state

```kotlin
val profileState: StateFlow<ProfileUiState>
val editState: StateFlow<EditUiState>
```

### Operations

| Function | Description |
|---|---|
| `loadProfile()` | Read DataStore (instant), then fetch `GET /account/profile` in background. Update both states and DataStore on success. |
| `startEdit()` | Populate `EditUiState.Editing` with current user values. |
| `cancelEdit()` | Discard draft, flip to `EditUiState.Idle`. |
| `onFieldChanged(field, value)` | Update draft field. For username: cancel previous check job, start 500ms debounced check (skip if value matches `user.username`). |
| `saveProfile()` | Call `PATCH /account/profile`. On success: update DataStore + flip to `ProfileUiState.Loaded`. On 409: set `SaveError(UsernameTaken)`. On other error: set `SaveError(Generic)`. |
| `signOut()` | Clear API key, clear `user_profile` DataStore key, reset `ApiClient`, call `CacheManager.clearAll()`, emit navigation effect to pop to Settings. |

Username debounce: 500ms, cancels previous `Job` on each keystroke. Calls `GET /users/search?q={username}&limit=5`, marks `Own` if value matches current username (no request sent).

---

## AccountScreen

**Location:** `ui/settings/AccountScreen.kt`

Single file. All helper composables are private to the file.

### State rendering

| Profile state | Edit state | What is shown |
|---|---|---|
| `NotLoggedIn` | — | Sign-in card with ORCID button + note about API Settings |
| `Loading` | — | `LoadingContent` |
| `Error` | — | `ErrorCard` with retry |
| `Loaded` | `Idle` | Header card + profile view card + sign-out button |
| `Loaded` | `Editing` / `Saving` | Header card + edit form + sign-out button |
| `Loaded` | `SaveError` | Same as Editing with error banner |

### Screen structure

```
AppScaffold
  TopAppBar("Account", navigationIcon = back arrow)
  
  Column (scrollable, 16dp padding)
    [NotLoggedIn]
      Card
        Text("Sign in to view and edit your profile")
        Button("Sign in with ORCID") → onNavigateToOrcidLogin
        Text("Or enter your API key manually in API Settings", labelSmall, muted)
    
    [Loaded]
      AccountHeaderCard(user)        ← always read-only
      Spacer(16dp)
      [Idle]  → ProfileViewCard(user, onEdit)
      [Editing/Saving/SaveError] → ProfileEditForm(editState, onField, onSave, onCancel)
      Spacer(16dp)
      SignOutButton(onSignOut)       ← destructive, with confirmation dialog
```

### AccountHeaderCard

Avatar circle (48dp) showing initials (first letter of first + last name), `titleLarge` display name, `@username` in `bodyMedium` (greyed "No username set" if null), ORCID in `labelSmall` with copy button.

### ProfileViewCard

`InfoRow`s (reusing `detail/components/InfoRows.kt`) for: First name, Last name, Email, Username (with `@` prefix), ORCID (read-only). "Edit profile" `TextButton` at the bottom.

### ProfileEditForm

`OutlinedTextField`s for first name, last name, email, username. Username field:
- Prefix `@` via `leadingIcon`
- Trailing indicator: nothing (empty/Own) → `CircularProgressIndicator` 16dp (Checking) → green `Icons.Default.CheckCircle` (Available) → red `Icons.Default.Cancel` (Taken)
- `supportingText`: "Available" / "Already taken" / validation rule hint
- Save disabled while `Checking` or `Taken` or both name fields empty

`SaveError(UsernameTaken)` shows an error banner above buttons: "That username is already taken."
`SaveError(Generic)` shows: "Failed to save — check your connection."

Save / Cancel row at the bottom. Save shows `CircularProgressIndicator` while `Saving`.

### Sign-out

`OutlinedButton` with `MaterialTheme.colorScheme.error` color, full width, outside the cards. Tapping shows an `AlertDialog` ("Sign out?", "You will need to sign in again to access Crucible.") with Cancel / Sign out (destructive) buttons.

---

## ApiSettings Changes

**Removed:**
- Account card (name, email, ORCID display)
- "Sign in with ORCID" button
- Sign out button
- `onUserOrcidSave` callback
- Account-fetch `LaunchedEffect`

**Stays:**
- API base URL field
- Graph explorer URL field
- Health check indicator

**New:**
- Collapsible "Advanced" section at the bottom (collapsed by default, chevron toggle). Contains the API key field as a password input (masked, with reveal toggle icon). Labelled: "For service accounts and developers."

---

## Navigation & Settings List

**New route:** `Screen.Account` in `NavGraph.kt`, wired to `AccountScreen`. Passes `onNavigateToOrcidLogin` so the ORCID WebView opens from Account.

**SettingsScreen list:** "Account" entry added at the top of the list, icon `Icons.Default.Person`. Subtitle shows `@{username}` if set, "Signed in" if profile loaded but no username, or "Not signed in" if no API key.

---

## Sign-out Sequence

Executed in `AccountViewModel.signOut()`:

1. `preferencesManager.clearApiKey()`
2. `preferencesManager.clearUserProfile()`
3. `ApiClient.setApiKey("")`
4. `CacheManager.clearAll()`
5. Emit navigation effect → pop back to Settings root

---

## Files Created / Modified

| File | Change |
|---|---|
| `data/model/CrucibleResource.kt` | Rename `UserLead` → `User`, add `username` field |
| `data/api/CrucibleApiService.kt` | Add `getProfile()`, `updateProfile()`, `checkUsernameAvailability()` |
| `data/preferences/AppPreferences.kt` | Add `userProfile: Flow<User?>`, `saveUserProfile()`, `clearUserProfile()` |
| `data/preferences/PreferencesManager.kt` | Implement new profile key with JSON serialization |
| `ui/settings/AccountViewModel.kt` | New file |
| `ui/settings/AccountScreen.kt` | New file |
| `ui/settings/ApiSettingsScreen.kt` | Remove account card/auth; add Advanced collapsible for API key |
| `ui/settings/SettingsScreen.kt` | Add Account entry at top of list |
| `ui/navigation/NavGraph.kt` | Add `Screen.Account` route |
| All files referencing `UserLead` | Update to `User` |
