package crucible.lens.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.UserAvatar

/**
 * The editable first/last/email/username block shared by [AccountScreen]'s edit mode and
 * `CompleteProfileScreen` - avatar + name fields, a divider, an optional save-error banner, then
 * email and username (with the same live-availability check UI in both places).
 */
@Composable
fun ProfileEditFields(
    draft: EditUiState.Editing,
    orcid: String?,
    isSaving: Boolean,
    saveError: SaveErrorReason?,
    onFirstNameChanged: (String) -> Unit,
    onLastNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val usernameFormatValid = draft.username.isBlank() || USERNAME_PATTERN.matches(draft.username.lowercase())

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            UserAvatar(
                firstName = draft.firstName,
                lastName = draft.lastName,
                size = 48.dp,
                orcid = orcid,
                modifier = Modifier.padding(top = 8.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
            }
        }

        HorizontalDivider()

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (saveError != null) {
                val msg = when (saveError) {
                    SaveErrorReason.UsernameTaken -> "That username is already taken."
                    SaveErrorReason.Generic -> "Failed to save — check your connection."
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        msg,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            OutlinedTextField(
                value = draft.email,
                onValueChange = onEmailChanged,
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                leadingIcon = {
                    AppIcon(AppIcons.Email, modifier = Modifier.size(20.dp))
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )
            OutlinedTextField(
                value = draft.username,
                onValueChange = { onUsernameChanged(it.lowercase()) },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                leadingIcon = {
                    Text(
                        "@",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                },
                trailingIcon = {
                    when (draft.usernameCheck) {
                        is UsernameCheckState.Checking -> CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        is UsernameCheckState.Available -> AppIcon(
                            AppIcons.Success,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        is UsernameCheckState.Taken -> AppIcon(
                            AppIcons.UsernameTaken,
                            tint = MaterialTheme.colorScheme.error
                        )
                        else -> {}
                    }
                },
                supportingText = {
                    when (draft.usernameCheck) {
                        is UsernameCheckState.Available -> Text(
                            "Available",
                            color = MaterialTheme.colorScheme.primary
                        )
                        is UsernameCheckState.Taken -> Text(
                            "Already taken",
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> if (!usernameFormatValid && draft.username.isNotBlank()) {
                            Text(
                                "3–24 chars: lowercase letters, digits, hyphens, underscores",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
        }
    }
}
