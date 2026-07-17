package crucible.lens.ui.common

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import crucible.lens.Res

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

// ---------------------------------------------------------------------------
// AppIcons — semantic mapping from concept to token.
// Populated once XML files are placed in commonMain/composeResources/drawable/.
// See docs/icon-downloads.md for the full download list.
// ---------------------------------------------------------------------------

object AppIcons {
    val accountTree                    = AppIconToken(Res.drawable.ic_account_tree)
    val add                            = AppIconToken(Res.drawable.ic_add)
    val addAPhoto                      = AppIconToken(Res.drawable.ic_add_a_photo)
    val arrowBack                      = AppIconToken(Res.drawable.ic_arrow_back)
    val arrowDownward                  = AppIconToken(Res.drawable.ic_arrow_downward)
    val arrowUpward                    = AppIconToken(Res.drawable.ic_arrow_upward)
    val attachFile                     = AppIconToken(Res.drawable.ic_attach_file)
    val autoAwesome                    = AppIconToken(Res.drawable.ic_auto_awesome)
    val badge                          = AppIconToken(Res.drawable.ic_badge)
    val biotech                        = AppIconToken(Res.drawable.ic_biotech)
    val bookmark                       = AppIconToken(Res.drawable.ic_bookmark)
    val bookmarkOutline                = AppIconToken(Res.drawable.ic_bookmark_outline)
    val bookmarkRemove                 = AppIconToken(Res.drawable.ic_bookmark_remove)
    val business                       = AppIconToken(Res.drawable.ic_business)
    val calendarToday                  = AppIconToken(Res.drawable.ic_calendar_today)
    val cancel                         = AppIconToken(Res.drawable.ic_cancel)
    val category                       = AppIconToken(Res.drawable.ic_category)
    val check                          = AppIconToken(Res.drawable.ic_check)
    val checkCircle                    = AppIconToken(Res.drawable.ic_check_circle)
    val chevronLeft                    = AppIconToken(Res.drawable.ic_chevron_left)
    val chevronRight                   = AppIconToken(Res.drawable.ic_chevron_right)
    val circle                         = AppIconToken(Res.drawable.ic_circle)
    val clear                          = AppIconToken(Res.drawable.ic_clear)
    val cloud                          = AppIconToken(Res.drawable.ic_cloud)
    val cloudOff                       = AppIconToken(Res.drawable.ic_cloud_off)
    val colorize                       = AppIconToken(Res.drawable.ic_colorize)
    val contentCopy                    = AppIconToken(Res.drawable.ic_content_copy)
    val copyAll                        = AppIconToken(Res.drawable.ic_copy_all)
    val darkMode                       = AppIconToken(Res.drawable.ic_dark_mode)
    val dataObject                     = AppIconToken(Res.drawable.ic_data_object)
    val dataset                        = AppIconToken(Res.drawable.ic_dataset)
    val delete                         = AppIconToken(Res.drawable.ic_delete)
    val deleteOutline                  = AppIconToken(Res.drawable.ic_delete_outline)
    val deleteSweep                    = AppIconToken(Res.drawable.ic_delete_sweep)
    val description                    = AppIconToken(Res.drawable.ic_description)
    val download                       = AppIconToken(Res.drawable.ic_download)
    val edit                           = AppIconToken(Res.drawable.ic_edit)
    val email                          = AppIconToken(Res.drawable.ic_email)
    val error                          = AppIconToken(Res.drawable.ic_error)
    val errorOutline                   = AppIconToken(Res.drawable.ic_error_outline)
    val expandMore                     = AppIconToken(Res.drawable.ic_expand_more)
    val factory                        = AppIconToken(Res.drawable.ic_factory)
    val filePresent                    = AppIconToken(Res.drawable.ic_file_present)
    val filterAlt                      = AppIconToken(Res.drawable.ic_filter_alt)
    val folder                         = AppIconToken(Res.drawable.ic_folder)
    val folderOpen                     = AppIconToken(Res.drawable.ic_folder_open)
    val folderZip                      = AppIconToken(Res.drawable.ic_folder_zip)
    val group                          = AppIconToken(Res.drawable.ic_group)
    val help                           = AppIconToken(Res.drawable.ic_help)
    val helpOutline                    = AppIconToken(Res.drawable.ic_help_outline)
    val history                        = AppIconToken(Res.drawable.ic_history)
    val historyToggleOff               = AppIconToken(Res.drawable.ic_history_toggle_off)
    val home                           = AppIconToken(Res.drawable.ic_home)
    val hourglassEmpty                 = AppIconToken(Res.drawable.ic_hourglass_empty)
    val image                          = AppIconToken(Res.drawable.ic_image)
    val info                           = AppIconToken(Res.drawable.ic_info)
    val insertDriveFile                = AppIconToken(Res.drawable.ic_insert_drive_file)
    val key                            = AppIconToken(Res.drawable.ic_key)
    val keyboardDoubleArrowUp          = AppIconToken(Res.drawable.ic_keyboard_double_arrow_up)
    val label                          = AppIconToken(Res.drawable.ic_label)
    val language                       = AppIconToken(Res.drawable.ic_language)
    val lightbulb                      = AppIconToken(Res.drawable.ic_lightbulb)
    val link                           = AppIconToken(Res.drawable.ic_link)
    val linkOff                        = AppIconToken(Res.drawable.ic_link_off)
    val listAlt                        = AppIconToken(Res.drawable.ic_list_alt)
    val locationOn                     = AppIconToken(Res.drawable.ic_location_on)
    val lock                           = AppIconToken(Res.drawable.ic_lock)
    val logout                         = AppIconToken(Res.drawable.ic_logout)
    val manageAccounts                 = AppIconToken(Res.drawable.ic_manage_accounts)
    val manageSearch                   = AppIconToken(Res.drawable.ic_manage_search)
    val moreVert                       = AppIconToken(Res.drawable.ic_more_vert)
    val notes                          = AppIconToken(Res.drawable.ic_notes)
    val openInNew                      = AppIconToken(Res.drawable.ic_open_in_new)
    val palette                        = AppIconToken(Res.drawable.ic_palette)
    val person                         = AppIconToken(Res.drawable.ic_person)
    val personAdd                      = AppIconToken(Res.drawable.ic_person_add)
    val personRemove                   = AppIconToken(Res.drawable.ic_person_remove)
    val place                          = AppIconToken(Res.drawable.ic_place)
    val psychology                     = AppIconToken(Res.drawable.ic_psychology)
    val public                         = AppIconToken(Res.drawable.ic_public)
    val qrCode                         = AppIconToken(Res.drawable.ic_qr_code)
    val qrCodeScanner                  = AppIconToken(Res.drawable.ic_qr_code_scanner)
    val refresh                        = AppIconToken(Res.drawable.ic_refresh)
    val restartAlt                     = AppIconToken(Res.drawable.ic_restart_alt)
    val restore                        = AppIconToken(Res.drawable.ic_restore)
    val save                           = AppIconToken(Res.drawable.ic_save)
    val schedule                       = AppIconToken(Res.drawable.ic_schedule)
    val science                        = AppIconToken(Res.drawable.ic_science)
    val search                         = AppIconToken(Res.drawable.ic_search)
    val searchOff                      = AppIconToken(Res.drawable.ic_search_off)
    val security                       = AppIconToken(Res.drawable.ic_security)
    val settings                       = AppIconToken(Res.drawable.ic_settings)
    val share                          = AppIconToken(Res.drawable.ic_share)
    val storage                        = AppIconToken(Res.drawable.ic_storage)
    val straighten                     = AppIconToken(Res.drawable.ic_straighten)
    val swapHoriz                      = AppIconToken(Res.drawable.ic_swap_horiz)
    val swapVert                       = AppIconToken(Res.drawable.ic_swap_vert)
    val tag                            = AppIconToken(Res.drawable.ic_tag)
    val tune                           = AppIconToken(Res.drawable.ic_tune)
    val update                         = AppIconToken(Res.drawable.ic_update)
    val visibility                     = AppIconToken(Res.drawable.ic_visibility)
    val visibilityOff                  = AppIconToken(Res.drawable.ic_visibility_off)
    val warning                        = AppIconToken(Res.drawable.ic_warning)
    val wifi                           = AppIconToken(Res.drawable.ic_wifi)
    val wifiOff                        = AppIconToken(Res.drawable.ic_wifi_off)
}
