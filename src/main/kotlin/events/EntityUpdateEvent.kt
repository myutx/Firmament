package moe.nea.firmament.events

import com.mojang.datafixers.util.Pair
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.item.ItemStack
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket
import moe.nea.firmament.annotations.Subscribe
import moe.nea.firmament.util.MC

/**
 * This event is fired when some entity properties are updated.
 * It is not fired for common changes like position, but is for less common ones,
 * like health, tracked data, names, equipment. It is always fired
 * *after* the values have been applied to the entity.
 */
sealed class EntityUpdateEvent : FirmamentEvent() {
	companion object : FirmamentEventBus<EntityUpdateEvent>() {
		private val inventoryEquipmentSlots = mapOf(
			39 to EquipmentSlot.HEAD,
			38 to EquipmentSlot.CHEST,
			37 to EquipmentSlot.LEGS,
			36 to EquipmentSlot.FEET,
			40 to EquipmentSlot.OFFHAND,
		)

		fun onDirectInventoryUpdate(player: Player, inventoryIndex: Int, stack: ItemStack, receivedNanos: Long?) {
			val slot = inventoryEquipmentSlots[inventoryIndex]
				?: EquipmentSlot.MAINHAND.takeIf { inventoryIndex == player.inventory.selectedSlot }
				?: return
			publish(EquipmentUpdate(player, listOf(Pair.of(slot, stack)), receivedNanos))
		}

		@Subscribe
		fun onPlayerInventoryUpdate(event: PlayerInventoryUpdate) {
			val p = MC.player ?: return
			val equipmentSlots = inventoryEquipmentSlots + (p.inventory.selectedSlot to EquipmentSlot.MAINHAND)
			val updatedSlots = equipmentSlots.mapNotNull { (stackIndex, slot) ->
				val slotIndex = p.inventoryMenu.findSlot(p.inventory, stackIndex).asInt
				event.getOrNull(slotIndex)?.let {
					Pair.of(slot, it)
				}
			}
			if (updatedSlots.isNotEmpty())
				publish(EquipmentUpdate(p, updatedSlots, event.receivedNanos))
		}
	}

	abstract val entity: Entity

	data class AttributeUpdate(
        override val entity: LivingEntity,
        val attributes: List<ClientboundUpdateAttributesPacket.AttributeSnapshot>,
	) : EntityUpdateEvent()

	data class TrackedDataUpdate(
        override val entity: Entity,
        val trackedValues: List<SynchedEntityData.DataValue<*>>,
	) : EntityUpdateEvent()

	data class EquipmentUpdate(
        override val entity: Entity,
        val newEquipment: List<Pair<EquipmentSlot, ItemStack>>,
		val receivedNanos: Long? = null,
	) : EntityUpdateEvent()

// TODO: onEntityPassengersSet, onEntityAttach?, onEntityStatusEffect
}
