package crucible.lens.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class ImagePickerTest {
    @Test
    fun keepsSafeFilename() {
        assertEquals("image.png", normalizePickedImageFilename("image.png"))
    }

    @Test
    fun removesPathAndControlCharacters() {
        assertEquals("myimage.png", normalizePickedImageFilename("../folder/my\u0000image.png"))
        assertEquals("image.jpg", normalizePickedImageFilename("C:\\photos\\image.jpg"))
    }

    @Test
    fun replacesMissingOrTraversalOnlyFilename() {
        assertEquals("selected_image", normalizePickedImageFilename(null))
        assertEquals("selected_image", normalizePickedImageFilename(".."))
        assertEquals("selected_image", normalizePickedImageFilename("  "))
    }
}
