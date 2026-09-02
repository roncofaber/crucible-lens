package crucible.lens.data.util

import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample

enum class ResourceLinkDirection { THEY_ARE_PARENT, THEY_ARE_CHILD }

sealed interface ResourceLinkOperation {
    data class Samples(val parentUuid: String, val childUuid: String) : ResourceLinkOperation
    data class Datasets(val parentUuid: String, val childUuid: String) : ResourceLinkOperation
    data class DatasetSample(val datasetUuid: String, val sampleUuid: String) : ResourceLinkOperation
}

fun resourceLinkOperation(
    current: CrucibleResource,
    target: CrucibleResource,
    direction: ResourceLinkDirection
): ResourceLinkOperation {
    require(current.uniqueId != target.uniqueId)
    return when {
        current is Sample && target is Sample -> {
            if (direction == ResourceLinkDirection.THEY_ARE_PARENT) {
                ResourceLinkOperation.Samples(target.uniqueId, current.uniqueId)
            } else {
                ResourceLinkOperation.Samples(current.uniqueId, target.uniqueId)
            }
        }
        current is Dataset && target is Dataset -> {
            if (direction == ResourceLinkDirection.THEY_ARE_PARENT) {
                ResourceLinkOperation.Datasets(target.uniqueId, current.uniqueId)
            } else {
                ResourceLinkOperation.Datasets(current.uniqueId, target.uniqueId)
            }
        }
        current is Sample && target is Dataset -> {
            ResourceLinkOperation.DatasetSample(target.uniqueId, current.uniqueId)
        }
        current is Dataset && target is Sample -> {
            ResourceLinkOperation.DatasetSample(current.uniqueId, target.uniqueId)
        }
        else -> error("Unsupported resource link")
    }
}
