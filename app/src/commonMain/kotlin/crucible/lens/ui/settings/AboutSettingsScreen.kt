@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.settings
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import crucible.lens.composeapp.generated.resources.Res
import crucible.lens.platform.appVersionName
import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.openUrl
import crucible.lens.ui.common.AppScaffold

@Composable
fun AboutSettingsScreen(
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val context = getPlatformContext()
    val lensIcon = Res.getUri("files/${if (isDarkTheme) "crucible_icon_dark.svg" else "crucible_icon_light.svg"}")

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "About",
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
            // App identity
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AboutCardHeader(title = "Crucible Lens") {
                        AsyncImage(
                            model = lensIcon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        "Version ${appVersionName()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    Text(
                        "Crucible Lens is the mobile client for the Molecular Foundry's Crucible research data platform at Lawrence Berkeley National Laboratory.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Team
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AboutCardHeader(title = "Crucible Team") {
                        AppIcon(AppIcons.Team, tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TeamMember(
                            name = "Edward Barnard",
                            role = "Molecular Foundry Data and Analytics Lead Scientist"
                        )
                        TeamMember(
                            name = "Morgan Wall",
                            role = "Scientific Software Engineer and Crucible Lead Developer"
                        )
                        TeamMember(
                            name = "Fabrice Roncoroni",
                            role = "Postdoctoral Researcher, Data Science and Computational Materials Science"
                        )
                    }
                }
            }

            // Links — ordered: platform → app → tooling → contact → community → institution
            ResourceLink(
                title = "Crucible",
                subtitle = "crucible.lbl.gov",
                url = "https://crucible.lbl.gov/"
            ) {
                AsyncImage(
                    model = Res.getUri("files/crucible_web.svg"),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }

            ResourceLink(
                title = "Crucible Lens",
                subtitle = "github.com/roncofaber/crucible-lens",
                url = "https://github.com/roncofaber/crucible-lens"
            ) {
                AsyncImage(
                    model = lensIcon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }

            ResourceLink(
                title = "nano-crucible",
                subtitle = "Python client library",
                url = "https://github.com/MolecularFoundryCrucible/nano-crucible"
            ) {
                AppIcon(
                    AppIcons.Python,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            ResourceLink(
                title = "Contact",
                subtitle = "crucible-dev@lbl.gov",
                url = "mailto:crucible-dev@lbl.gov"
            ) {
                AppIcon(
                    AppIcons.Email,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            ResourceLink(
                title = "Discord",
                subtitle = "Join the Crucible community",
                url = "https://discord.com/invite/Wrepphsgbx"
            ) {
                AppIcon(
                    AppIcons.Discord,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            ResourceLink(
                title = "Molecular Foundry",
                subtitle = "Lawrence Berkeley National Laboratory",
                url = "https://foundry.lbl.gov/"
            ) {
                AppIcon(
                    AppIcons.Organization,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                "Licensed under BSD-3-Clause",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = TextAlign.Center
            )

            Text(
                "Privacy Policy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        openUrl(context, "https://github.com/roncofaber/crucible-lens/blob/main/PRIVACY.md")
                    },
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Developed by ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "@roncofaber",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { openUrl(context, "https://github.com/roncofaber") }
                )
                Text(
                    " with the help of Claude Code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AboutCardHeader(
    title: String,
    leadingIcon: @Composable () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingIcon()
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ResourceLink(
    title: String,
    subtitle: String,
    url: String,
    leadingContent: @Composable () -> Unit
) {
    val context = getPlatformContext()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openUrl(context, url) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leadingContent()
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AppIcon(
                AppIcons.OpenExternal,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TeamMember(name: String, role: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
