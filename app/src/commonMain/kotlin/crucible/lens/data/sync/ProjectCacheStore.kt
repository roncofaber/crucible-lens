package crucible.lens.data.sync

import crucible.lens.data.cache.CachedProjectContent
import crucible.lens.data.cache.PersistentProjectCache
import crucible.lens.data.cache.ProjectCacheOwner
import crucible.lens.data.model.Project
import crucible.lens.platform.PlatformContext

internal interface ProjectCacheStore {
    suspend fun saveProjects(owner: ProjectCacheOwner, projects: List<Project>)
    suspend fun loadProjects(owner: ProjectCacheOwner): List<Project>?
    suspend fun saveContent(owner: ProjectCacheOwner, content: CachedProjectContent)
    suspend fun loadContent(owner: ProjectCacheOwner, projectMfid: String): CachedProjectContent?
    suspend fun loadContents(owner: ProjectCacheOwner, projectMfids: Set<String>): List<CachedProjectContent>
    suspend fun removeContent(owner: ProjectCacheOwner, projectMfid: String)
    suspend fun retainContents(owner: ProjectCacheOwner, projectMfids: Set<String>)
}

internal class PlatformProjectCacheStore(
    private val context: PlatformContext
) : ProjectCacheStore {
    override suspend fun saveProjects(owner: ProjectCacheOwner, projects: List<Project>) =
        PersistentProjectCache.save(context, owner, projects)

    override suspend fun loadProjects(owner: ProjectCacheOwner): List<Project>? =
        PersistentProjectCache.load(context, owner)

    override suspend fun saveContent(owner: ProjectCacheOwner, content: CachedProjectContent) =
        PersistentProjectCache.saveContent(context, owner, content)

    override suspend fun loadContent(owner: ProjectCacheOwner, projectMfid: String): CachedProjectContent? =
        PersistentProjectCache.loadContent(context, owner, projectMfid)

    override suspend fun loadContents(owner: ProjectCacheOwner, projectMfids: Set<String>): List<CachedProjectContent> =
        PersistentProjectCache.loadContents(context, owner, projectMfids)

    override suspend fun removeContent(owner: ProjectCacheOwner, projectMfid: String) =
        PersistentProjectCache.removeContent(context, owner, projectMfid)

    override suspend fun retainContents(owner: ProjectCacheOwner, projectMfids: Set<String>) =
        PersistentProjectCache.retainContents(context, owner, projectMfids)
}
