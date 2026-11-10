package moe.nea.firmament.test.features.debug

import com.mojang.datafixers.util.Pair
import java.nio.file.Path
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.ClickEvent
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.DyedItemColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import moe.nea.firmament.features.debug.AnimatedClothingScanner
import moe.nea.firmament.features.debug.DyeTrace
import moe.nea.firmament.test.FirmTestBootstrap
import moe.nea.firmament.util.mc.NbtPrism

class AnimatedClothingScannerTest {

	init {
		FirmTestBootstrap.bootstrapMinecraft()
	}

	private val lens = AnimatedClothingScanner.LensOfFashionTheft(
		NbtPrism(emptyList()), DataComponents.DYED_COLOR,
	)

	private fun equipment(): List<Pair<EquipmentSlot, ItemStack>> = listOf(
		Pair.of(EquipmentSlot.HEAD, ItemStack(Items.LEATHER_HELMET).apply {
			set(DataComponents.DYED_COLOR, DyedItemColor(16711680))
		}),
		Pair.of(EquipmentSlot.CHEST, ItemStack(Items.LEATHER_CHESTPLATE).apply {
			set(DataComponents.DYED_COLOR, DyedItemColor(255))
		}),
		Pair.of(EquipmentSlot.LEGS, ItemStack(Items.LEATHER_LEGGINGS).apply {
			set(DataComponents.DYED_COLOR, DyedItemColor(65280))
		}),
	)

	@Test
	fun chestSelectionExcludesOtherArmorColors() {
		assertEquals(listOf("255"), lens.observeEquipment(equipment(), EquipmentSlot.CHEST))
	}

	@Test
	fun absentSelectedSlotProducesNoObservation() {
		assertEquals(emptyList<String>(), lens.observeEquipment(equipment(), EquipmentSlot.FEET))
	}

	@Test
	fun allSlotsRetainTheirOriginalOrder() {
		assertEquals(listOf("16711680", "255", "65280"), lens.observeEquipment(equipment(), null))
	}

	@Test
	fun timedSamplesReadCurrentArmorWithoutEquipmentEvents() {
		val trace = DyeTrace(1_000_000_000L, "2026-11-07T23:00:00Z", "minecraft:player", 42, "chest")
		val chestplate = equipment()[1].second
		trace.recordItem(1_000_000_000L, 10, "tick", true, chestplate)
		trace.recordItem(1_050_000_000L, 11, "tick", true, chestplate)
		chestplate.set(DataComponents.DYED_COLOR, DyedItemColor(65280))
		trace.recordItem(1_100_000_000L, 12, "tick", true, chestplate)
		assertEquals(listOf(255, 255, 65280), trace.samples.map { it.rgb })
		assertEquals(listOf("minecraft:leather_chestplate", "minecraft:leather_chestplate", "minecraft:leather_chestplate"), trace.samples.map { it.itemId })
	}

	@Test
	fun savedMessageCopiesTheAbsoluteCsvPath(@TempDir directory: Path) {
		val csv = directory.resolve("capture with spaces.csv")
		val message = AnimatedClothingScanner.savedTraceMessage(DyeTrace.SavedFiles(csv, directory.resolve("capture.json")))
		val actions = message.toFlatList().mapNotNull { it.style.clickEvent as? ClickEvent.CopyToClipboard }
		assertEquals(listOf(csv.toAbsolutePath().toString()), actions.map { it.value })
	}

	@Test
	fun savedMessageOpensTheCaptureDirectory(@TempDir directory: Path) {
		val csv = directory.resolve("capture with spaces.csv")
		val message = AnimatedClothingScanner.savedTraceMessage(DyeTrace.SavedFiles(csv, directory.resolve("capture.json")))
		val actions = message.toFlatList().mapNotNull { it.style.clickEvent as? ClickEvent.OpenFile }
		assertEquals(listOf(directory.toAbsolutePath().toString()), actions.map { it.path })
	}

	@Test
	fun savedMessageDoesNotAppendAPeriodToTheFilePath(@TempDir directory: Path) {
		val csv = directory.resolve("capture.csv")
		val message = AnimatedClothingScanner.savedTraceMessage(DyeTrace.SavedFiles(csv, directory.resolve("capture.json")))
		assertTrue(message.string.contains(csv.toAbsolutePath().toString()))
		assertFalse(message.string.contains("${csv.toAbsolutePath()}."))
	}
}
