#!/bin/bash
# Downloads all Material Symbols (Rounded, 24dp) needed by Crucible Lens.
# Place the resulting XML files in commonMain/composeResources/drawable/.

set -e

BASE_URL="https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded"
OUT_DIR="$(dirname "$0")/../app/src/commonMain/composeResources/drawable"

mkdir -p "$OUT_DIR"

download() {
  local name="$1"   # Material Symbol name (e.g. "science")
  local fill="$2"   # "default" or "fill1"
  local file="$3"   # Output filename (e.g. "ic_science.xml")
  local url="$BASE_URL/$name/$fill/24px.xml"
  echo "  $file"
  curl -sf -o "$OUT_DIR/$file" "$url" || echo "  WARN: failed to download $name ($fill)"
}

echo "Downloading Material Symbols (Rounded, 24dp)..."
echo ""

echo "Resources"
download science        default  ic_science.xml
download dataset        default  ic_dataset.xml
download biotech        default  ic_biotech.xml
download folder         default  ic_folder.xml
download folder_open    default  ic_folder_open.xml
download folder_zip     default  ic_folder_zip.xml
download attach_file    default  ic_attach_file.xml
download insert_drive_file default ic_insert_drive_file.xml
download image          default  ic_image.xml
download description    default  ic_description.xml
download data_object    default  ic_data_object.xml
download storage        default  ic_storage.xml
download file_present   default  ic_file_present.xml
download label          default  ic_label.xml
download list_alt       default  ic_list_alt.xml

echo "Actions"
download add            default  ic_add.xml
download edit           default  ic_edit.xml
download save           default  ic_save.xml
download delete         default  ic_delete.xml
download delete_outline default  ic_delete_outline.xml
download delete_sweep   default  ic_delete_sweep.xml
download content_copy   default  ic_content_copy.xml
download copy_all       default  ic_copy_all.xml
download share          default  ic_share.xml
download download       default  ic_download.xml
download link           default  ic_link.xml
download link_off       default  ic_link_off.xml
download swap_horiz     default  ic_swap_horiz.xml
download open_in_new    default  ic_open_in_new.xml
download refresh        default  ic_refresh.xml
download restart_alt    default  ic_restart_alt.xml
download add_a_photo    default  ic_add_a_photo.xml

echo "Navigation"
download arrow_back     default  ic_arrow_back.xml
download home           default  ic_home.xml
download search         default  ic_search.xml
download search_off     default  ic_search_off.xml
download chevron_right  default  ic_chevron_right.xml
download chevron_left   default  ic_chevron_left.xml
download expand_more    default  ic_expand_more.xml
download keyboard_double_arrow_up default ic_keyboard_double_arrow_up.xml
download more_vert      default  ic_more_vert.xml
download filter_alt     default  ic_filter_alt.xml
download swap_vert      default  ic_swap_vert.xml
download tune           default  ic_tune.xml
download manage_search  default  ic_manage_search.xml
download qr_code        default  ic_qr_code.xml
download qr_code_scanner default ic_qr_code_scanner.xml

echo "User & Identity"
download person         default  ic_person.xml
download person_add     default  ic_person_add.xml
download person_remove  default  ic_person_remove.xml
download manage_accounts default ic_manage_accounts.xml
download badge          default  ic_badge.xml
download email          default  ic_email.xml
download group          default  ic_group.xml
download logout         default  ic_logout.xml

echo "Status & Visibility"
download check_circle   default  ic_check_circle.xml
download check          default  ic_check.xml
download cancel         default  ic_cancel.xml
download warning        default  ic_warning.xml
download error          default  ic_error.xml
download error_outline  default  ic_error_outline.xml
download cloud          default  ic_cloud.xml
download cloud_off      default  ic_cloud_off.xml
download wifi           default  ic_wifi.xml
download wifi_off       default  ic_wifi_off.xml
download hourglass_empty default  ic_hourglass_empty.xml
download lock           default  ic_lock.xml
download public         default  ic_public.xml
download help_outline   default  ic_help_outline.xml
download visibility     default  ic_visibility.xml
download visibility_off default  ic_visibility_off.xml

echo "Stateful (bookmark — fill 0 and fill 1)"
download bookmark       default  ic_bookmark_outline.xml
download bookmark       fill1    ic_bookmark.xml
download bookmark_remove default  ic_bookmark_remove.xml

echo "Time & History"
download history        default  ic_history.xml
download history_toggle_off default ic_history_toggle_off.xml
download schedule       default  ic_schedule.xml
download calendar_today default  ic_calendar_today.xml
download update         default  ic_update.xml
download restore        default  ic_restore.xml

echo "Metadata Fields"
download tag            default  ic_tag.xml
download category       default  ic_category.xml
download business       default  ic_business.xml
download place          default  ic_place.xml
download factory        default  ic_factory.xml
download straighten     default  ic_straighten.xml
download security       default  ic_security.xml
download account_tree   default  ic_account_tree.xml
download notes          default  ic_notes.xml
download arrow_upward   default  ic_arrow_upward.xml
download arrow_downward default  ic_arrow_downward.xml

echo "Settings & Configuration"
download settings       default  ic_settings.xml
download palette        default  ic_palette.xml
download dark_mode      default  ic_dark_mode.xml
download colorize       default  ic_colorize.xml
download key            default  ic_key.xml
download language       default  ic_language.xml
download psychology     default  ic_psychology.xml
download auto_awesome   default  ic_auto_awesome.xml
download info           default  ic_info.xml
download lightbulb      default  ic_lightbulb.xml
download help           default  ic_help.xml

echo "Misc"
download circle         default  ic_circle.xml
download location_on    default  ic_location_on.xml

echo ""
echo "Done. $(ls "$OUT_DIR"/ic_*.xml 2>/dev/null | wc -l) icon files in $OUT_DIR"
