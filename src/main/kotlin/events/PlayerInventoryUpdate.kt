package moe.nea.firmament.events

import net.minecraft.world.item.ItemStack

sealed class PlayerInventoryUpdate : FirmamentEvent() {
	companion object : FirmamentEventBus<PlayerInventoryUpdate>()
	data class Single(val slot: Int, val stack: ItemStack, override val receivedNanos: Long? = null) : PlayerInventoryUpdate() {
		override fun getOrNull(slot: Int): ItemStack? {
			if (slot == this.slot) return stack
			return null
		}

	}

	data class Multi(val contents: List<ItemStack>, override val receivedNanos: Long? = null) : PlayerInventoryUpdate() {
		override fun getOrNull(slot: Int): ItemStack? {
			return contents.getOrNull(slot)
		}
	}

	abstract val receivedNanos: Long?
	abstract fun getOrNull(slot: Int): ItemStack?
}
