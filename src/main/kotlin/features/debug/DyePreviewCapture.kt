package moe.nea.firmament.features.debug

internal object DyePreviewCapture {
	data class Dye(val id: String?, val name: String)
	data class Candidate(val id: Int, val namedPreview: Boolean, val plainDyedSet: Boolean)

	private val guideTitle = Regex("""(?:\(\d+/\d+\)\s*)?Dye Guide(?:\s*\(\d+/\d+\))?""")
	private val dyeId = Regex("DYE_[A-Z0-9_]{1,64}")

	fun selection(title: String, rightClick: Boolean, id: String?, name: String, lore: List<String>): Dye? {
		if (!rightClick || !guideTitle.matches(title.trim())) return null
		if (!name.endsWith(" Dye") || lore.none { it.trim() == "Right-click to preview!" }) return null
		return Dye(id?.takeIf(dyeId::matches), name)
	}

	fun target(previousIds: Set<Int>, candidates: List<Candidate>): Int? {
		val fresh = candidates.filterNot { it.id in previousIds }
		val named = fresh.filter { it.namedPreview }
		return if (named.isEmpty()) fresh.singleOrNull { it.plainDyedSet }?.id
		else named.singleOrNull()?.id
	}
}
