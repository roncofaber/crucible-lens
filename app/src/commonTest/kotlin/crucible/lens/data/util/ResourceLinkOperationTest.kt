package crucible.lens.data.util

import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResourceLinkOperationTest {
    private val sampleA = Sample(uniqueId = "sample-a")
    private val sampleB = Sample(uniqueId = "sample-b")
    private val datasetA = Dataset(uniqueId = "dataset-a")
    private val datasetB = Dataset(uniqueId = "dataset-b")

    @Test
    fun sameTypeDirectionMapsParentsAndChildren() {
        assertEquals(
            ResourceLinkOperation.Samples("sample-b", "sample-a"),
            resourceLinkOperation(sampleA, sampleB, ResourceLinkDirection.THEY_ARE_PARENT)
        )
        assertEquals(
            ResourceLinkOperation.Samples("sample-a", "sample-b"),
            resourceLinkOperation(sampleA, sampleB, ResourceLinkDirection.THEY_ARE_CHILD)
        )
        assertEquals(
            ResourceLinkOperation.Datasets("dataset-b", "dataset-a"),
            resourceLinkOperation(datasetA, datasetB, ResourceLinkDirection.THEY_ARE_PARENT)
        )
        assertEquals(
            ResourceLinkOperation.Datasets("dataset-a", "dataset-b"),
            resourceLinkOperation(datasetA, datasetB, ResourceLinkDirection.THEY_ARE_CHILD)
        )
    }

    @Test
    fun crossTypeLinksAlwaysMapDatasetAndSample() {
        assertEquals(
            ResourceLinkOperation.DatasetSample("dataset-a", "sample-a"),
            resourceLinkOperation(sampleA, datasetA, ResourceLinkDirection.THEY_ARE_CHILD)
        )
        assertEquals(
            ResourceLinkOperation.DatasetSample("dataset-a", "sample-a"),
            resourceLinkOperation(datasetA, sampleA, ResourceLinkDirection.THEY_ARE_PARENT)
        )
    }

    @Test
    fun resourceCannotLinkToItself() {
        assertFailsWith<IllegalArgumentException> {
            resourceLinkOperation(sampleA, sampleA, ResourceLinkDirection.THEY_ARE_CHILD)
        }
    }
}
