# Icon Download Manifest

Download all icons from https://fonts.google.com/icons  
**Style:** Rounded | **Weight:** 400 | **Grade:** 0 | **Size:** 24dp | **Format:** Android (XML)

Place all files in:
`app/src/commonMain/composeResources/drawable/`

Icons marked **FILLED** need to be downloaded twice:
- Once with Fill=0 → save as `ic_<name>_outline.xml`
- Once with Fill=1 → save as `ic_<name>.xml`

---

## Resources (what things are)

| Search for | Fill | Save as | AppIcons name |
|-----------|------|---------|---------------|
| science | 0 | `ic_science.xml` | `Sample` |
| dataset | 0 | `ic_dataset.xml` | `Dataset` |
| biotech | 0 | `ic_biotech.xml` | `Instrument` |
| folder | 0 | `ic_folder.xml` | `Project` |
| folder_open | 0 | `ic_folder_open.xml` | `SourceFolder` |
| folder_zip | 0 | `ic_folder_zip.xml` | `FileArchive` |
| attach_file | 0 | `ic_attach_file.xml` | `AttachFile` |
| insert_drive_file | 0 | `ic_insert_drive_file.xml` | `FileGeneric` |
| image | 0 | `ic_image.xml` | `FileImage` |
| description | 0 | `ic_description.xml` | `FilePdf` |
| data_object | 0 | `ic_data_object.xml` | `FileJson` |
| storage | 0 | `ic_storage.xml` | `FileStorage` |
| file_present | 0 | `ic_file_present.xml` | `DataFormat` |
| label | 0 | `ic_label.xml` | `DataType` |
| list_alt | 0 | `ic_list_alt.xml` | `ScientificMetadata` |

## Actions

| Search for | Fill | Save as | AppIcons name |
|-----------|------|---------|---------------|
| add | 0 | `ic_add.xml` | `Add` |
| edit | 0 | `ic_edit.xml` | `Edit` |
| save | 0 | `ic_save.xml` | `Save` |
| delete | 0 | `ic_delete.xml` | `Delete` |
| delete_outline | 0 | `ic_delete_outline.xml` | `RequestDeletion` |
| delete_sweep | 0 | `ic_delete_sweep.xml` | `ClearAll` |
| content_copy | 0 | `ic_content_copy.xml` | `CopyToClipboard` |
| copy_all | 0 | `ic_copy_all.xml` | `CopyResource` |
| share | 0 | `ic_share.xml` | `Share` |
| download | 0 | `ic_download.xml` | `Download` |
| link | 0 | `ic_link.xml` | `LinkResource` |
| link_off | 0 | `ic_link_off.xml` | `UnlinkResource` |
| swap_horiz | 0 | `ic_swap_horiz.xml` | `SwapResource` |
| open_in_new | 0 | `ic_open_in_new.xml` | `OpenExternal` |
| refresh | 0 | `ic_refresh.xml` | `Refresh` |
| restart_alt | 0 | `ic_restart_alt.xml` | `ResetToDefault` |
| add_a_photo | 0 | `ic_add_a_photo.xml` | `TakePhoto` |

## Navigation

| Search for | Fill | Save as | AppIcons name |
|-----------|------|---------|---------------|
| arrow_back | 0 | `ic_arrow_back.xml` | `Back` |
| home | 0 | `ic_home.xml` | `Home` |
| search | 0 | `ic_search.xml` | `Search` |
| search_off | 0 | `ic_search_off.xml` | `NoResults` |
| chevron_right | 0 | `ic_chevron_right.xml` | `NavigateNext` |
| chevron_left | 0 | `ic_chevron_left.xml` | `CarouselPrev` |
| expand_more | 0 | `ic_expand_more.xml` | *(internal — ExpandChevron only)* |
| keyboard_double_arrow_up | 0 | `ic_keyboard_double_arrow_up.xml` | `ScrollToTop` |
| more_vert | 0 | `ic_more_vert.xml` | `OverflowMenu` |
| filter_alt | 0 | `ic_filter_alt.xml` | `Filter` |
| swap_vert | 0 | `ic_swap_vert.xml` | `Sort` |
| tune | 0 | `ic_tune.xml` | `GroupBy` |
| manage_search | 0 | `ic_manage_search.xml` | `SearchFilters` |
| qr_code | 0 | `ic_qr_code.xml` | `ShowQrCode` |
| qr_code_scanner | 0 | `ic_qr_code_scanner.xml` | `ScanQr` |

## User & Identity

| Search for | Fill | Save as | AppIcons name |
|-----------|------|---------|---------------|
| person | 0 | `ic_person.xml` | `User` |
| person_add | 0 | `ic_person_add.xml` | `AddMember` |
| person_remove | 0 | `ic_person_remove.xml` | `RemoveMember` |
| manage_accounts | 0 | `ic_manage_accounts.xml` | `ManageMembers` |
| badge | 0 | `ic_badge.xml` | `Username` |
| email | 0 | `ic_email.xml` | `Email` |
| group | 0 | `ic_group.xml` | `Team` |
| logout | 0 | `ic_logout.xml` | `SignOut` |

## Status & Visibility

| Search for | Fill | Save as | AppIcons name |
|-----------|------|---------|---------------|
| check_circle | 0 | `ic_check_circle.xml` | `Success` |
| check | 0 | `ic_check.xml` | `Selected` |
| cancel | 0 | `ic_cancel.xml` | `UsernameTaken` |
| warning | 0 | `ic_warning.xml` | `Warning` |
| error | 0 | `ic_error.xml` | `Error` |
| error_outline | 0 | `ic_error_outline.xml` | `ErrorOutline` |
| cloud | 0 | `ic_cloud.xml` | `ApiEndpoint` |
| cloud_off | 0 | `ic_cloud_off.xml` | `Unreachable` |
| wifi | 0 | `ic_wifi.xml` | `TestConnection` |
| wifi_off | 0 | `ic_wifi_off.xml` | `Offline` |
| hourglass_empty | 0 | `ic_hourglass_empty.xml` | `Pending` |
| lock | 0 | `ic_lock.xml` | `Private` |
| public | 0 | `ic_public.xml` | `Public` |
| help_outline | 0 | `ic_help_outline.xml` | `UnknownVisibility` |
| visibility | 0 | `ic_visibility.xml` | `ShowContent` |
| visibility_off | 0 | `ic_visibility_off.xml` | `HideContent` |

## Stateful (download twice — fill=0 and fill=1)

| Search for | Fill | Save as | AppIcons name |
|-----------|------|---------|---------------|
| bookmark | **0** | `ic_bookmark_outline.xml` | `Pinned` (outlined state) |
| bookmark | **1** | `ic_bookmark.xml` | `Pinned` (filled state) |
| bookmark_remove | 0 | `ic_bookmark_remove.xml` | `RemovePin` |

## Time & History

| Search for | Fill | Save as | AppIcons name |
|-----------|------|---------|---------------|
| history | 0 | `ic_history.xml` | `History` |
| history_toggle_off | 0 | `ic_history_toggle_off.xml` | `HistoryEmpty` |
| schedule | 0 | `ic_schedule.xml` | `Timestamp` |
| calendar_today | 0 | `ic_calendar_today.xml` | `CreationDate` |
| update | 0 | `ic_update.xml` | `ModificationDate` |
| restore | 0 | `ic_restore.xml` | `RecentItems` |

## Metadata Fields

| Search for | Fill | Save as | AppIcons name |
|-----------|------|---------|---------------|
| tag | 0 | `ic_tag.xml` | `Tag` |
| category | 0 | `ic_category.xml` | `ResourceType` |
| business | 0 | `ic_business.xml` | `Organization` |
| place | 0 | `ic_place.xml` | `Location` |
| factory | 0 | `ic_factory.xml` | `Manufacturer` |
| straighten | 0 | `ic_straighten.xml` | `InstrumentModel` |
| security | 0 | `ic_security.xml` | `Hash` |
| account_tree | 0 | `ic_account_tree.xml` | `ResourceHierarchy` |
| notes | 0 | `ic_notes.xml` | `Description` |
| arrow_upward | 0 | `ic_arrow_upward.xml` | `ParentResource` |
| arrow_downward | 0 | `ic_arrow_downward.xml` | `ChildResource` |

## Settings & Configuration

| Search for | Fill | Save as | AppIcons name |
|-----------|------|---------|---------------|
| settings | 0 | `ic_settings.xml` | `Settings` |
| palette | 0 | `ic_palette.xml` | `Appearance` |
| dark_mode | 0 | `ic_dark_mode.xml` | `DarkTheme` |
| colorize | 0 | `ic_colorize.xml` | `ColorPicker` |
| key | 0 | `ic_key.xml` | `ApiKey` |
| language | 0 | `ic_language.xml` | `WebUrl` |
| psychology | 0 | `ic_psychology.xml` | `AiSettings` |
| auto_awesome | 0 | `ic_auto_awesome.xml` | `AiFeature` |
| info | 0 | `ic_info.xml` | `Info` |
| lightbulb | 0 | `ic_lightbulb.xml` | `Tip` |
| help | 0 | `ic_help.xml` | `Help` |
| storage | 0 | `ic_storage_cache.xml` | `Cache` |

## Misc

| Search for | Fill | Save as | AppIcons name |
|-----------|------|---------|---------------|
| circle | 0 | `ic_circle.xml` | `SelectionDot` |
| location_on | 0 | `ic_location_on.xml` | `LocationAlt` |

---

## Notes

- **`storage`** appears twice (file size field + cache settings). Download once as `ic_storage.xml` for file size and once as `ic_storage_cache.xml` for cache — or use the same file with different `AppIcons` tokens. Revisit when fixing the dual-use icon issue.
- **`expand_more`** is only used internally by `ExpandChevron` — not exposed in `AppIcons`.
- **`ExpandLess`** is no longer used — `ExpandChevron` handles both states by rotating `expand_more`.
- **`notes`** replaces both `Icons.AutoMirrored.Filled.Notes` and `Icons.Default.Notes`.
- **`help`** replaces both `Icons.AutoMirrored.Filled.Help` and `Icons.AutoMirrored.Filled.HelpOutline` — download `help_outline` variant separately if needed.

## Total: 101 XML files (99 icons + 2 extra for bookmark fill states + 1 duplicate for storage)
