package crucible.lens.di

import crucible.lens.data.api.ApiClient
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.sync.DataSyncManager
import crucible.lens.ui.create.CreateDatasetViewModel
import crucible.lens.ui.create.CreateProjectViewModel
import crucible.lens.ui.create.CreateSampleViewModel
import crucible.lens.ui.create.EditResourceViewModel
import crucible.lens.ui.detail.ResourceDetailViewModel
import crucible.lens.ui.home.HomeViewModel
import crucible.lens.ui.instruments.InstrumentDetailViewModel
import crucible.lens.ui.instruments.InstrumentListViewModel
import crucible.lens.ui.instruments.ManageInstrumentViewModel
import crucible.lens.ui.projects.ManageProjectViewModel
import crucible.lens.ui.projects.ProjectDetailViewModel
import crucible.lens.ui.projects.ProjectsListViewModel
import crucible.lens.ui.settings.AccountViewModel
import crucible.lens.ui.settings.UserProfileViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Shared dependency graph. AppPreferences is intentionally NOT declared here —
 * it needs a platform-specific PlatformContext to construct on Android, so it
 * is provided by each platform's own module (see MainActivity.kt / App.kt)
 * and combined with this module in initKoin().
 */
val appModule = module {
    single { ApiClient() }
    single { CrucibleRepository(get()) }
    single { DataSyncManager(get()) }

    viewModelOf(::ResourceDetailViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::AccountViewModel)
    viewModelOf(::UserProfileViewModel)
    viewModelOf(::ProjectsListViewModel)
    viewModelOf(::ProjectDetailViewModel)
    viewModelOf(::ManageProjectViewModel)
    viewModelOf(::InstrumentListViewModel)
    viewModelOf(::InstrumentDetailViewModel)
    viewModelOf(::ManageInstrumentViewModel)
    viewModelOf(::CreateSampleViewModel)
    viewModelOf(::CreateDatasetViewModel)
    viewModelOf(::CreateProjectViewModel)
    viewModelOf(::EditResourceViewModel)
}
