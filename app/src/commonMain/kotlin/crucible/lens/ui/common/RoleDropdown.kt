@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package crucible.lens.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <T> RoleDropdownField(
    selectedRole: T,
    roles: List<T>,
    roleKey: (T) -> String,
    roleLabel: (T) -> String,
    onRoleSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Role",
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = roleLabel(selectedRole),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            roles.forEach { role ->
                DropdownMenuItem(
                    text = { RoleBadge(roleKey(role), label = roleLabel(role)) },
                    trailingIcon = {
                        if (role == selectedRole) AppIcon(AppIcons.Check)
                    },
                    onClick = {
                        expanded = false
                        onRoleSelected(role)
                    }
                )
            }
        }
    }
}

@Composable
fun <T> CompactRoleDropdown(
    selectedRole: T,
    roles: List<T>,
    roleKey: (T) -> String,
    roleLabel: (T) -> String,
    onRoleSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        Box(modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)) {
            RoleBadge(
                role = roleKey(selectedRole),
                label = roleLabel(selectedRole),
                trailingIcon = AppIcons.ExpandMore
            )
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            matchAnchorWidth = false,
            modifier = Modifier.widthIn(min = 200.dp, max = 240.dp)
        ) {
            roles.forEach { role ->
                DropdownMenuItem(
                    text = { RoleBadge(roleKey(role), label = roleLabel(role)) },
                    trailingIcon = {
                        if (role == selectedRole) AppIcon(AppIcons.Check)
                    },
                    onClick = {
                        expanded = false
                        onRoleSelected(role)
                    }
                )
            }
        }
    }
}
