package moe.nea.firmament.features.debug

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack

class DyeTrace(
	private val startNanos: Long,
	private val startedAt: String,
	private val entityType: String,
	private val entityId: Int,
	val slot: String,
	private val dyeId: String? = null,
	private val dyeName: String? = null,
) {
	companion object {
		const val DURATION_SECONDS = 180L
		private const val NANOS_PER_SECOND = 1_000_000_000L
		private val safeDyeId = Regex("DYE_[A-Z0-9_]{1,64}")
	}

	data class Sample(
		val elapsedNanos: Long,
		val clientTick: Int,
		val source: String,
		val entityPresent: Boolean,
		val rgb: Int?,
		val itemId: String,
		val receivedElapsedNanos: Long?,
		val appliedElapsedNanos: Long?,
	)
	data class SavedFiles(val csv: Path, val metadata: Path)
	private val observations = mutableListOf<Sample>()
	val samples: List<Sample> get() = observations
	private var endNanos: Long? = null
	private var stopReason: String? = null

	fun record(nowNanos: Long, clientTick: Int, source: String, entityPresent: Boolean, rgb: Int?, itemId: String, receivedNanos: Long? = null) {
		if (endNanos != null) return
		val elapsed = nowNanos - startNanos
		val applied = elapsed.takeIf { source == "equipment" }
		val received = receivedNanos?.minus(startNanos)?.takeIf { applied != null && it in 0..applied }
		observations.add(Sample(elapsed, clientTick, source, entityPresent,
			if (entityPresent) rgb else null, if (entityPresent) itemId else "", received, applied))
	}

	fun recordItem(nowNanos: Long, clientTick: Int, source: String, entityPresent: Boolean, stack: ItemStack?, receivedNanos: Long? = null) {
		record(nowNanos, clientTick, source, entityPresent, stack?.get(DataComponents.DYED_COLOR)?.rgb,
			stack?.let { BuiltInRegistries.ITEM.getKey(it.item).toString() } ?: "", receivedNanos)
	}

	fun finish(nowNanos: Long, reason: String) {
		if (endNanos != null) return
		endNanos = nowNanos
		stopReason = reason
	}

	fun csv(): String = buildString {
		append("elapsed_ns,client_tick,source,entity_present,rgb,item_id,received_elapsed_ns,applied_elapsed_ns\n")
		for (sample in observations) {
			append(sample.elapsedNanos).append(',').append(sample.clientTick).append(',')
			append(sample.source).append(',').append(sample.entityPresent).append(',')
			append(sample.rgb?.toString() ?: "").append(',').append(sample.itemId).append(',')
			append(sample.receivedElapsedNanos?.toString().orEmpty()).append(',')
			append(sample.appliedElapsedNanos?.toString().orEmpty()).append('\n')
		}
	}

	fun save(directory: Path): SavedFiles {
		val finishedAt = checkNotNull(endNanos)
		Files.createDirectories(directory)
		val label = dyeId?.takeIf(safeDyeId::matches)?.let { "$it-" }.orEmpty()
		val fileStem = "dye-trace-$label${UUID.randomUUID()}"
		val csvPath = directory.resolve("$fileStem.csv")
		val metadataPath = directory.resolve("$fileStem.json")
		val metadata = JsonObject().apply {
			addProperty("formatVersion", 2)
			addProperty("durationLimitSeconds", DURATION_SECONDS)
			addProperty("startedAt", startedAt)
			addProperty("entityType", entityType)
			addProperty("entityId", entityId)
			addProperty("slot", slot)
			dyeId?.let { addProperty("dyeId", it) }
			dyeName?.let { addProperty("dyeName", it) }
			addProperty("stopReason", stopReason)
			addProperty("durationNanos", finishedAt - startNanos)
			addProperty("sampleCount", observations.size)
			addProperty("csvFile", csvPath.fileName.toString())
			addProperty("clock", "System.nanoTime")
		}
		Files.writeString(csvPath, csv(), StandardOpenOption.CREATE_NEW)
		Files.writeString(metadataPath, GsonBuilder().setPrettyPrinting().create().toJson(metadata) + "\n", StandardOpenOption.CREATE_NEW)
		return SavedFiles(csvPath, metadataPath)
	}

	fun remainingSeconds(nowNanos: Long): Long {
		val remainingNanos = DURATION_SECONDS * NANOS_PER_SECOND - (nowNanos - startNanos)
		return Math.ceilDiv(remainingNanos.coerceAtLeast(0L), NANOS_PER_SECOND)
	}

	fun hasReachedLimit(nowNanos: Long): Boolean = remainingSeconds(nowNanos) == 0L
}
