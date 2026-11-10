package moe.nea.firmament.test.features.debug

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import moe.nea.firmament.features.debug.DyeTrace

class DyeTraceTest {

	private fun trace() = DyeTrace(1_000_000_000L, "2026-11-07T23:00:00Z", "minecraft:player", 42, "chest")

	private fun labeledTrace(dyeId: String, dyeName: String) =
		DyeTrace(
			1_000_000_000L, "2026-11-07T23:00:00Z", "minecraft:player", 42, "chest", dyeId, dyeName,
		)

	@Test
	fun unchangedColorsRetainTheirObservationTimes() {
		val trace = trace()
		trace.record(1_000_000_000L, 10, "start", true, 255, "minecraft:leather_chestplate")
		trace.record(1_050_000_000L, 11, "tick", true, 255, "minecraft:leather_chestplate")
		trace.record(1_250_000_000L, 15, "tick", true, 65280, "minecraft:leather_chestplate")
		assertEquals(listOf(0L, 50_000_000L, 250_000_000L), trace.samples.map { it.elapsedNanos })
		assertEquals(listOf(255, 255, 65280), trace.samples.map { it.rgb })
	}

	@Test
	fun exportRetainsThreeCompleteRepeatedCycles() {
		val trace = trace()
		listOf(255, 65280, 255, 65280, 255, 65280).forEachIndexed { index, color ->
			trace.record(1_000_000_000L + index * 50_000_000L, index, "tick", true, color, "minecraft:leather_chestplate")
		}
		val rows = trace.csv().lineSequence().filter { it.isNotEmpty() }.toList()
		assertEquals(7, rows.size)
		assertEquals(listOf("255", "65280", "255", "65280", "255", "65280"), rows.drop(1).map { it.split(',')[4] })
	}

	@Test
	fun equipmentUpdatesAndTickSamplesRemainSeparate() {
		val trace = trace()
		trace.record(1_050_000_000L, 11, "equipment", true, 255, "minecraft:leather_chestplate", 1_040_000_000L)
		trace.record(1_050_000_000L, 11, "tick", true, 255, "minecraft:leather_chestplate")
		assertEquals(listOf("equipment", "tick"), trace.samples.map { it.source })
		assertEquals(listOf(40_000_000L, null), trace.samples.map { it.receivedElapsedNanos })
		assertEquals(listOf(50_000_000L, null), trace.samples.map { it.appliedElapsedNanos })
		assertTrue(trace.csv().contains("equipment,true,255,minecraft:leather_chestplate,40000000,50000000"))
		assertEquals(2, trace.samples.size)
	}

	@Test
	fun absentEntityProducesAnExplicitGap() {
		val trace = trace()
		trace.record(1_000_000_000L, 10, "tick", true, 255, "minecraft:leather_chestplate")
		trace.record(1_050_000_000L, 11, "tick", false, 255, "minecraft:leather_chestplate")
		assertEquals(listOf(true, false), trace.samples.map { it.entityPresent })
		assertEquals(listOf(255, null), trace.samples.map { it.rgb })
		assertTrue(trace.csv().contains("50000000,11,tick,false,,"))
	}

	@Test
	fun saveWritesTheTraceAndItsMetadata(@TempDir directory: Path) {
		val trace = trace()
		trace.record(1_000_000_000L, 10, "start", true, 255, "minecraft:leather_chestplate")
		trace.record(1_500_000_000L, 20, "stop", true, 255, "minecraft:leather_chestplate")
		trace.finish(1_500_000_000L, "manual")
		val paths = trace.save(directory)
		assertTrue(Files.exists(paths.csv))
		assertTrue(Files.exists(paths.metadata))
		val metadata = JsonParser.parseString(Files.readString(paths.metadata)).asJsonObject
		assertEquals(2, metadata["formatVersion"].asInt)
		assertEquals(180, metadata["durationLimitSeconds"].asInt)
		assertEquals("chest", metadata["slot"].asString)
		assertEquals("minecraft:player", metadata["entityType"].asString)
		assertEquals("manual", metadata["stopReason"].asString)
		assertEquals(500_000_000L, metadata["durationNanos"].asLong)
		assertEquals(2, metadata["sampleCount"].asInt)
		assertEquals(trace.csv(), Files.readString(paths.csv))
		assertTrue(Regex("dye-trace-[0-9a-f-]{36}\\.csv").matches(paths.csv.fileName.toString()))
		assertFalse(metadata.has("dyeId"))
		assertFalse(metadata.has("dyeName"))
	}

	@Test
	fun saveLabelsAPreviewCaptureWithItsDye(@TempDir directory: Path) {
		val trace = labeledTrace("DYE_BEACH", "Beach Dye")
		trace.finish(1_500_000_000L, "entity_lost")
		val paths = trace.save(directory)
		val metadata = JsonParser.parseString(Files.readString(paths.metadata)).asJsonObject
		assertTrue(paths.csv.fileName.toString().startsWith("dye-trace-DYE_BEACH-"))
		assertEquals(paths.csv.fileName.toString().removeSuffix(".csv") + ".json", paths.metadata.fileName.toString())
		assertEquals(paths.csv.fileName.toString(), metadata["csvFile"].asString)
		assertEquals("DYE_BEACH", metadata["dyeId"].asString)
		assertEquals("Beach Dye", metadata["dyeName"].asString)
		assertFalse(paths.csv == trace.save(directory).csv)
	}

	@Test
	fun unsafeDyeIdsAreExcludedFromTheFilename(@TempDir directory: Path) {
		val trace = labeledTrace("DYE_BEACH/../../unexpected", "Beach Dye")
		trace.finish(1_500_000_000L, "entity_lost")
		val paths = trace.save(directory)
		assertEquals(directory, paths.csv.parent)
		assertTrue(Regex("dye-trace-[0-9a-f-]{36}\\.csv").matches(paths.csv.fileName.toString()))
	}

	@Test
	fun finishedCaptureRejectsLaterObservations() {
		val trace = trace()
		trace.record(1_000_000_000L, 10, "start", true, 255, "minecraft:leather_chestplate")
		trace.finish(1_050_000_000L, "manual")
		trace.record(1_100_000_000L, 12, "tick", true, 65280, "minecraft:leather_chestplate")
		assertEquals(listOf(255), trace.samples.map { it.rgb })
	}

	@Test
	fun timeLimitUsesElapsedTime() {
		val trace = trace()
		assertFalse(trace.hasReachedLimit(180_999_999_999L))
		assertTrue(trace.hasReachedLimit(181_000_000_000L))
	}

	@Test
	fun countdownRoundsUpUntilTheTraceDeadline() {
		val trace = trace()
		assertEquals(180L, trace.remainingSeconds(1_000_000_000L))
		assertEquals(180L, trace.remainingSeconds(1_500_000_000L))
		assertEquals(179L, trace.remainingSeconds(2_000_000_000L))
		assertEquals(1L, trace.remainingSeconds(180_999_999_999L))
		assertEquals(0L, trace.remainingSeconds(181_000_000_000L))
		assertEquals(0L, trace.remainingSeconds(182_000_000_000L))
	}
}
