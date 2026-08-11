@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.settings
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIconToken
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crucible.lens.ui.common.AppScaffold

private val RESULT_LIMIT_OPTIONS = listOf(5, 10, 15, 20)

@Composable
fun SearchSettingsScreen(
    currentPeopleResultLimit: Int,
    currentProjectResultLimit: Int,
    onPeopleResultLimitSave: (Int) -> Unit,
    onProjectResultLimitSave: (Int) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    AppScaffold(
        topBar = {
            AppTopBar(
                title = "Search",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onHome) { AppIcon(AppIcons.Home) }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ResultLimitCard(
                icon = AppIcons.User,
                title = "People result limit",
                description = "How many People to show per search, so they don't crowd out samples and datasets.",
                currentValue = currentPeopleResultLimit,
                onSave = onPeopleResultLimitSave
            )
            ResultLimitCard(
                icon = AppIcons.Project,
                title = "Project result limit",
                description = "How many Projects to show per search, so they don't crowd out samples and datasets.",
                currentValue = currentProjectResultLimit,
                onSave = onProjectResultLimitSave
            )
        }
    }
}

@Composable
private fun ResultLimitCard(
    icon: AppIconToken,
    title: String,
    description: String,
    currentValue: Int,
    onSave: (Int) -> Unit
) {
    var input by remember(currentValue) { mutableStateOf(currentValue) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(icon, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RESULT_LIMIT_OPTIONS.forEach { value ->
                    FilterChip(
                        selected = input == value,
                        onClick = { input = value; onSave(value) },
                        label = { Text(value.toString()) },
                        leadingIcon = if (input == value) {
                            { AppIcon(AppIcons.Selected, modifier = Modifier.size(18.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f),
                        colors = settingsChipColors(),
                        border = settingsChipBorder(selected = input == value)
                    )
                }
            }
        }
    }
}
