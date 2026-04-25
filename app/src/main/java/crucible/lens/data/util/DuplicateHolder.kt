package crucible.lens.data.util

/**
 * Transient store for pre-filling create screens when duplicating a resource.
 * Caller writes to the holder immediately before navigation; the create screen
 * reads (and clears) it once on first composition.
 */
object DuplicateHolder {

    data class SamplePrefill(
        val name: String,
        val type: String?,
        val description: String?,
        val timestamp: String?,
        val projectId: String?
    )

    data class DatasetPrefill(
        val name: String,
        val measurement: String?,
        val instrumentName: String?,
        val dataFormat: String?,
        val sessionName: String?,
        val timestamp: String?,
        val projectId: String?
    )

    private var samplePrefill: SamplePrefill? = null
    private var datasetPrefill: DatasetPrefill? = null

    fun putSample(p: SamplePrefill)   { samplePrefill  = p }
    fun putDataset(p: DatasetPrefill) { datasetPrefill = p }

    /** Returns and clears the pending prefill so it is only consumed once. */
    fun takeSample():  SamplePrefill?  = samplePrefill.also  { samplePrefill  = null }
    fun takeDataset(): DatasetPrefill? = datasetPrefill.also { datasetPrefill = null }
}
