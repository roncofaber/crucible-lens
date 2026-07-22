package crucible.lens.ui.common

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import crucible.lens.composeapp.generated.resources.Res
import crucible.lens.composeapp.generated.resources.*

/**
 * Descriptor for a single icon. Carries the drawable resource, an optional
 * filled variant for stateful icons (active/inactive), and the accessibility
 * content description (null = decorative, surrounding label provides context).
 */
data class AppIconToken(
    val resource: DrawableResource,
    val filledResource: DrawableResource? = null,
    val contentDescription: String? = null
)

/**
 * Single composable for all icons in the app. Use [AppIcons] to look up
 * the right token by semantic name rather than icon name.
 *
 * [filled] switches to [AppIconToken.filledResource] when true and a filled
 * variant exists — used for active/inactive states (e.g. pinned bookmark).
 */
@Composable
fun AppIcon(
    icon: AppIconToken,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = LocalContentColor.current
) {
    val resource = if (filled && icon.filledResource != null) icon.filledResource else icon.resource
    Icon(
        painter = painterResource(resource),
        contentDescription = icon.contentDescription,
        modifier = modifier,
        tint = tint
    )
}

object AppIcons {
    // Resources (what things are)
    val Sample        = AppIconToken(Res.drawable.ic_science)
    val Dataset       = AppIconToken(Res.drawable.ic_dataset)
    val Instrument    = AppIconToken(Res.drawable.ic_biotech)
    val Project       = AppIconToken(Res.drawable.ic_folder)
    val SourceFolder  = AppIconToken(Res.drawable.ic_folder_open)
    val FolderOpen    = AppIconToken(Res.drawable.ic_folder_open)
    val FileArchive   = AppIconToken(Res.drawable.ic_folder_zip)
    val AttachFile    = AppIconToken(Res.drawable.ic_attach_file)
    val FileGeneric   = AppIconToken(Res.drawable.ic_insert_drive_file)
    val FileImage     = AppIconToken(Res.drawable.ic_image)
    val FilePdf       = AppIconToken(Res.drawable.ic_description)
    val FileJson      = AppIconToken(Res.drawable.ic_data_object)
    val FileStorage   = AppIconToken(Res.drawable.ic_storage)
    val DataFormat    = AppIconToken(Res.drawable.ic_file_present)
    val DataType      = AppIconToken(Res.drawable.ic_label)
    val ScientificMetadata = AppIconToken(Res.drawable.ic_list_alt)

    // Actions
    val Add           = AppIconToken(Res.drawable.ic_add, contentDescription = "Add")
    val Edit          = AppIconToken(Res.drawable.ic_edit, contentDescription = "Edit")
    val Save          = AppIconToken(Res.drawable.ic_save, contentDescription = "Save")
    val Delete        = AppIconToken(Res.drawable.ic_delete, contentDescription = "Delete")
    val RequestDeletion = AppIconToken(Res.drawable.ic_delete_outline, contentDescription = "Request deletion")
    val ClearAll      = AppIconToken(Res.drawable.ic_delete_sweep, contentDescription = "Clear all")
    val ClearInput    = AppIconToken(Res.drawable.ic_clear, contentDescription = "Clear")
    val CopyToClipboard = AppIconToken(Res.drawable.ic_content_copy, contentDescription = "Copy")
    val CopyResource  = AppIconToken(Res.drawable.ic_copy_all, contentDescription = "Copy resource")
    val Share         = AppIconToken(Res.drawable.ic_share, contentDescription = "Share")
    val Download      = AppIconToken(Res.drawable.ic_download, contentDescription = "Download")
    val LinkResource  = AppIconToken(Res.drawable.ic_link, contentDescription = "Link")
    val UnlinkResource = AppIconToken(Res.drawable.ic_link_off, contentDescription = "Unlink")
    val SwapResource  = AppIconToken(Res.drawable.ic_swap_horiz, contentDescription = "Swap")
    val OpenExternal  = AppIconToken(Res.drawable.ic_open_in_new, contentDescription = "Open in browser")
    val OpenInNew     = AppIconToken(Res.drawable.ic_open_in_new, contentDescription = "Open in browser")
    val Orcid         = AppIconToken(Res.drawable.ic_orcid, contentDescription = "ORCID")
    val Discord       = AppIconToken(Res.drawable.ic_discord, contentDescription = "Discord")
    val Python        = AppIconToken(Res.drawable.ic_python, contentDescription = "Python")
    val Refresh       = AppIconToken(Res.drawable.ic_refresh, contentDescription = "Refresh")
    val ResetToDefault = AppIconToken(Res.drawable.ic_restart_alt, contentDescription = "Reset to default")
    val TakePhoto     = AppIconToken(Res.drawable.ic_add_a_photo, contentDescription = "Take photo")

    // Navigation
    val Back          = AppIconToken(Res.drawable.ic_arrow_back, contentDescription = "Back")
    val Home          = AppIconToken(Res.drawable.ic_home, contentDescription = "Home")
    val Search        = AppIconToken(Res.drawable.ic_search)
    val NoResults     = AppIconToken(Res.drawable.ic_search_off)
    val SearchOff     = AppIconToken(Res.drawable.ic_search_off)
    val NavigateNext  = AppIconToken(Res.drawable.ic_chevron_right)
    val CarouselPrev  = AppIconToken(Res.drawable.ic_chevron_left)
    val MoreVert      = AppIconToken(Res.drawable.ic_more_vert, contentDescription = "More options")
    val Filter        = AppIconToken(Res.drawable.ic_filter_alt, contentDescription = "Filter")
    val Sort          = AppIconToken(Res.drawable.ic_swap_vert, contentDescription = "Sort")
    val GroupBy       = AppIconToken(Res.drawable.ic_tune)
    val SearchFilters = AppIconToken(Res.drawable.ic_manage_search)
    val ShowQrCode    = AppIconToken(Res.drawable.ic_qr_code, contentDescription = "Show QR code")
    val ScanQr        = AppIconToken(Res.drawable.ic_qr_code_scanner, contentDescription = "Scan QR code")
    val ScrollToTop   = AppIconToken(Res.drawable.ic_keyboard_double_arrow_up, contentDescription = "Scroll to top")
    // ExpandMore/ExpandLess: prefer ExpandChevron composable; these are provided for edge cases
    val ExpandMore    = AppIconToken(Res.drawable.ic_expand_more)
    val ExpandLess    = AppIconToken(Res.drawable.ic_expand_more) // rotated via ExpandChevron

    // User & Identity
    val User          = AppIconToken(Res.drawable.ic_person)
    val Person        = AppIconToken(Res.drawable.ic_person)
    val PersonAdd     = AppIconToken(Res.drawable.ic_person_add, contentDescription = "Add member")
    val PersonRemove  = AppIconToken(Res.drawable.ic_person_remove, contentDescription = "Remove member")
    val AddMember     = AppIconToken(Res.drawable.ic_person_add, contentDescription = "Add member")
    val RemoveMember  = AppIconToken(Res.drawable.ic_person_remove, contentDescription = "Remove member")
    val ManageMembers = AppIconToken(Res.drawable.ic_manage_accounts)
    val Username      = AppIconToken(Res.drawable.ic_badge)
    val Email         = AppIconToken(Res.drawable.ic_email)
    val Team          = AppIconToken(Res.drawable.ic_group)
    val SignOut       = AppIconToken(Res.drawable.ic_logout, contentDescription = "Sign out")

    // Status & Visibility
    val Success       = AppIconToken(Res.drawable.ic_check_circle)
    val Selected      = AppIconToken(Res.drawable.ic_check)
    val Check         = AppIconToken(Res.drawable.ic_check)
    val UsernameTaken = AppIconToken(Res.drawable.ic_cancel)
    val Warning       = AppIconToken(Res.drawable.ic_warning)
    val Error         = AppIconToken(Res.drawable.ic_error)
    val ErrorOutline  = AppIconToken(Res.drawable.ic_error_outline)
    val ApiEndpoint   = AppIconToken(Res.drawable.ic_cloud)
    val Unreachable   = AppIconToken(Res.drawable.ic_cloud_off)
    val TestConnection = AppIconToken(Res.drawable.ic_wifi, contentDescription = "Test connection")
    val Offline       = AppIconToken(Res.drawable.ic_wifi_off)
    val Pending       = AppIconToken(Res.drawable.ic_hourglass_empty)
    val Private       = AppIconToken(Res.drawable.ic_lock)
    val Public        = AppIconToken(Res.drawable.ic_public)
    val UnknownVisibility = AppIconToken(Res.drawable.ic_help_outline)
    val ShowContent   = AppIconToken(Res.drawable.ic_visibility, contentDescription = "Show")
    val HideContent   = AppIconToken(Res.drawable.ic_visibility_off, contentDescription = "Hide")

    // Stateful (bookmark — filled = pinned, outlined = unpinned)
    val Pinned        = AppIconToken(
        resource       = Res.drawable.ic_bookmark_outline,
        filledResource = Res.drawable.ic_bookmark
    )
    val PinnedEmpty   = AppIconToken(Res.drawable.ic_bookmark_outline) // always outlined
    val RemovePin     = AppIconToken(Res.drawable.ic_bookmark_remove, contentDescription = "Remove pin")

    // Time & History
    val History       = AppIconToken(Res.drawable.ic_history)
    val HistoryEmpty  = AppIconToken(Res.drawable.ic_history_toggle_off)
    val Timestamp     = AppIconToken(Res.drawable.ic_schedule)
    val CreationDate  = AppIconToken(Res.drawable.ic_calendar_today)
    val ModificationDate = AppIconToken(Res.drawable.ic_update)
    val RecentItems   = AppIconToken(Res.drawable.ic_restore)

    // Metadata Fields
    val Tag           = AppIconToken(Res.drawable.ic_tag)
    val ResourceType  = AppIconToken(Res.drawable.ic_category)
    val Category      = AppIconToken(Res.drawable.ic_category)
    val Organization  = AppIconToken(Res.drawable.ic_business)
    val Business      = AppIconToken(Res.drawable.ic_business)
    val Location      = AppIconToken(Res.drawable.ic_place)
    val Place         = AppIconToken(Res.drawable.ic_place)
    val Manufacturer  = AppIconToken(Res.drawable.ic_factory)
    val Factory       = AppIconToken(Res.drawable.ic_factory)
    val InstrumentModel = AppIconToken(Res.drawable.ic_straighten)
    val Straighten    = AppIconToken(Res.drawable.ic_straighten)
    val Hash          = AppIconToken(Res.drawable.ic_security)
    val ResourceHierarchy = AppIconToken(Res.drawable.ic_account_tree)
    val Notes         = AppIconToken(Res.drawable.ic_notes)
    val Description   = AppIconToken(Res.drawable.ic_notes)
    val ParentResource = AppIconToken(Res.drawable.ic_arrow_upward)
    val ChildResource  = AppIconToken(Res.drawable.ic_arrow_downward)

    // Settings & Configuration
    val Settings      = AppIconToken(Res.drawable.ic_settings)
    val Appearance    = AppIconToken(Res.drawable.ic_palette)
    val DarkTheme     = AppIconToken(Res.drawable.ic_dark_mode)
    val ColorPicker   = AppIconToken(Res.drawable.ic_colorize)
    val ApiKey        = AppIconToken(Res.drawable.ic_key)
    val Key           = AppIconToken(Res.drawable.ic_key)
    val WebUrl        = AppIconToken(Res.drawable.ic_language)
    val AiSettings    = AppIconToken(Res.drawable.ic_psychology)
    val AiFeature     = AppIconToken(Res.drawable.ic_auto_awesome)
    val Info          = AppIconToken(Res.drawable.ic_info)
    val Tip           = AppIconToken(Res.drawable.ic_lightbulb)
    val Help          = AppIconToken(Res.drawable.ic_help)
    val Cache         = AppIconToken(Res.drawable.ic_storage)

    // Misc
    val SelectionDot  = AppIconToken(Res.drawable.ic_circle)
    val LocationAlt   = AppIconToken(Res.drawable.ic_location_on)
}
