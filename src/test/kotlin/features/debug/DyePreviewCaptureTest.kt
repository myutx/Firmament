package moe.nea.firmament.test.features.debug

import moe.nea.firmament.features.debug.DyePreviewCapture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DyePreviewCaptureTest {
	private val previewLore = listOf("Hex #301934", "Right-click to preview!")

	@Test
	fun identifiesTheDyeFromTheGuideSlot() {
		val dye = DyePreviewCapture.selection("(1/3) Dye Guide", true, "DYE_DARK_PURPLE", "Dark Purple Dye", previewLore)
		assertEquals(DyePreviewCapture.Dye("DYE_DARK_PURPLE", "Dark Purple Dye"), dye)
	}

	@Test
	fun retainsTheDisplayNameWhenTheGuideSlotHasNoDyeId() {
		val dye = DyePreviewCapture.selection("Dye Guide", true, null, "Beach Dye", previewLore)
		assertEquals(DyePreviewCapture.Dye(null, "Beach Dye"), dye)
	}

	@Test
	fun ignoresUnrelatedMenusClicksAndSlots() {
		assertNull(DyePreviewCapture.selection("Fire Sale", true, "DYE_BEACH", "Beach Dye", previewLore))
		assertNull(DyePreviewCapture.selection("Dye Guide", false, "DYE_BEACH", "Beach Dye", previewLore))
		assertNull(DyePreviewCapture.selection("Dye Guide", true, "DYE_BEACH", "Beach Dye", emptyList()))
		assertNull(DyePreviewCapture.selection("Dye Guide", true, null, "Next Page", previewLore))
	}

	@Test
	fun selectsTheNewNamedPreviewOverPreviouslySeenAndUnrelatedEntities() {
		val candidates = listOf(
			DyePreviewCapture.Candidate(1, namedPreview = true, plainDyedSet = true),
			DyePreviewCapture.Candidate(2, namedPreview = false, plainDyedSet = true),
			DyePreviewCapture.Candidate(3, namedPreview = true, plainDyedSet = false),
		)
		assertEquals(3, DyePreviewCapture.target(setOf(1), candidates))
	}

	@Test
	fun requiresAnUnambiguousPreviewTarget() {
		val plain = DyePreviewCapture.Candidate(1, namedPreview = false, plainDyedSet = true)
		val unrelated = DyePreviewCapture.Candidate(2, namedPreview = false, plainDyedSet = false)
		assertEquals(1, DyePreviewCapture.target(emptySet(), listOf(plain, unrelated)))
		assertNull(DyePreviewCapture.target(setOf(1), listOf(plain, unrelated)))
		assertNull(DyePreviewCapture.target(emptySet(), listOf(plain, plain.copy(id = 3))))
	}
}
