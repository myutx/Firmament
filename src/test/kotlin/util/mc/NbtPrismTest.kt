package moe.nea.firmament.test.util.mc

import com.mojang.brigadier.StringReader
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.StringTag
import net.minecraft.world.item.component.DyedItemColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import moe.nea.firmament.util.mc.NbtPrism

class NbtPrismTest {

	@Test
	fun dotSelectsWholeDyeColorThroughCommandArgument() {
		val selector = NbtPrism.Argument.parse(StringReader("."))!!
		assertEquals(listOf(IntTag.valueOf(2172458)), selector.access(IntTag.valueOf(2172458)).toList())
	}

	@Test
	fun dotReadsColorFromMinecraftDyeComponent() {
		val encodedColor = DyedItemColor.CODEC.encodeStart(NbtOps.INSTANCE, DyedItemColor(2172458)).orThrow
		val selector = NbtPrism.Argument.parse(StringReader("."))!!
		assertEquals(listOf(IntTag.valueOf(2172458)), selector.access(encodedColor).toList())
	}

	@Test
	fun dotSelectsWholeCompoundThroughCommandArgument() {
		val root = CompoundTag().apply { putInt("rgb", 2172458) }
		val selector = NbtPrism.Argument.parse(StringReader("."))!!
		assertEquals(listOf(root), selector.access(root).toList())
	}

	@Test
	fun nestedHeadTexturePathStillSelectsTexture() {
		val root = CompoundTag().apply {
			put("properties", ListTag().apply {
				add(CompoundTag().apply { putString("value", "texture-frame") })
			})
		}
		val selector = NbtPrism.Argument.parse(StringReader("properties.0.value"))!!
		assertEquals(listOf(StringTag.valueOf("texture-frame")), selector.access(root).toList())
	}
}
