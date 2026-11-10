package moe.nea.firmament.features.debug

import java.time.Instant
import net.minecraft.commands.arguments.ResourceKeyArgument
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import com.mojang.datafixers.util.Pair
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.nbt.Tag
import net.minecraft.nbt.NbtOps
import net.minecraft.core.registries.Registries
import moe.nea.firmament.annotations.Subscribe
import moe.nea.firmament.Firmament
import moe.nea.firmament.commands.get
import moe.nea.firmament.commands.thenArgument
import moe.nea.firmament.commands.thenExecute
import moe.nea.firmament.commands.thenLiteral
import moe.nea.firmament.events.CommandEvent
import moe.nea.firmament.events.EntityUpdateEvent
import moe.nea.firmament.events.HudRenderEvent
import moe.nea.firmament.events.SlotClickEvent
import moe.nea.firmament.events.TickEvent
import moe.nea.firmament.events.WorldKeyboardEvent
import moe.nea.firmament.events.WorldReadyEvent
import moe.nea.firmament.util.ClipboardUtils
import moe.nea.firmament.util.MC
import moe.nea.firmament.util.aqua
import moe.nea.firmament.util.math.GChainReconciliation
import moe.nea.firmament.util.math.GChainReconciliation.shortenCycle
import moe.nea.firmament.util.mc.accessor
import moe.nea.firmament.util.mc.displayNameAccordingToNbt
import moe.nea.firmament.util.mc.loreAccordingToNbt
import moe.nea.firmament.util.mc.NbtPrism
import moe.nea.firmament.util.rawSkyBlockId
import moe.nea.firmament.util.tr
import moe.nea.firmament.util.unformattedString

object AnimatedClothingScanner {

	data class LensOfFashionTheft<T : Any>(
		val prism: NbtPrism,
		val component: DataComponentType<T>,
	) {
		fun observe(itemStack: ItemStack): Collection<Tag> {
			val x = itemStack.get(component) ?: return listOf()
			val nbt = component.codecOrThrow().encodeStart(NbtOps.INSTANCE, x).orThrow
			return prism.access(nbt)
		}

		fun observeEquipment(
			equipment: List<Pair<EquipmentSlot, ItemStack>>,
			selectedSlot: EquipmentSlot?,
		): List<String> {
			return equipment
				.filter { selectedSlot == null || it.first == selectedSlot }
				.map { observe(it.second).joinToString() }
		}
	}

	var lens: LensOfFashionTheft<*>? = null
	var subject: Entity? = null
	var selectedSlot: EquipmentSlot? = null
	var history: MutableList<String> = mutableListOf()
	val metaHistory: MutableList<List<String>> = mutableListOf()
	private data class ActiveTrace(val capture: DyeTrace, val entity: LivingEntity, val slot: EquipmentSlot, val automatic: Boolean)
	private data class PendingPreview(val dye: DyePreviewCapture.Dye, val previousIds: Set<Int>, val clickedAt: Long)
	private var activeTrace: ActiveTrace? = null
	private var lastTrace: DyeTrace? = null
	private var lastTraceNeedsSave = false
	private var pendingPreview: PendingPreview? = null

	private val previewArmor by lazy {
		mapOf(
			EquipmentSlot.HEAD to Items.LEATHER_HELMET,
			EquipmentSlot.CHEST to Items.LEATHER_CHESTPLATE,
			EquipmentSlot.LEGS to Items.LEATHER_LEGGINGS,
			EquipmentSlot.FEET to Items.LEATHER_BOOTS,
		)
	}

	private fun nearbyEntities(): List<Entity> {
		val player = MC.player ?: return emptyList()
		val world = MC.world ?: return emptyList()
		return world.getEntitiesOfClass(Entity::class.java, player.boundingBox.inflate(8.0)) { it !== player }
	}

	@Subscribe
	fun onPreviewClick(event: SlotClickEvent) {
		if (!DeveloperFeatures.TConfig.autoRecordDyePreviews || activeTrace?.automatic == false || lastTraceNeedsSave) return
		if (event.slot.container === MC.player?.inventory) return
		val title = MC.screenName ?: return
		if (event.actionType != ContainerInput.PICKUP || event.button != 1) return
		val item = event.stack.accessor()
		val dye = DyePreviewCapture.selection(
			title,
			true,
			item.rawSkyBlockId,
			item.displayNameAccordingToNbt.unformattedString.trim(),
			item.loreAccordingToNbt.map { it.unformattedString },
		) ?: return
		pendingPreview = PendingPreview(dye, nearbyEntities().mapTo(mutableSetOf()) { it.id }, System.nanoTime())
	}

	private fun Entity.hasPreviewLabel(expected: String): Boolean {
		val textDisplayLabel = (this as? Display.TextDisplay)?.textRenderState()?.text()?.unformattedString
		return listOf(customName?.unformattedString, displayName.unformattedString, textDisplayLabel)
			.any { it?.trim().equals(expected, ignoreCase = true) }
	}

	private fun pollPreview(now: Long) {
		val pending = pendingPreview ?: return
		if (lastTraceNeedsSave || !DeveloperFeatures.TConfig.autoRecordDyePreviews) {
			pendingPreview = null
			return
		}
		val elapsed = now - pending.clickedAt
		if (elapsed >= 5_000_000_000L) {
			pendingPreview = null
			MC.sendChat(tr("firmament.fitstealer.trace.preview.missing", "Could not identify the ${pending.dye.name} preview."))
			return
		}
		val entities = nearbyEntities()
		val expectedLabel = "${pending.dye.name} Preview"
		val labels = entities.filter { it.hasPreviewLabel(expectedLabel) }
		val dyedEntities = entities.filterIsInstance<LivingEntity>().filter {
			it.id !in pending.previousIds && it.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.DYED_COLOR)
		}
		val candidates = dyedEntities.map { entity ->
			val namedPreview = labels.any { it === entity || it.distanceToSqr(entity) <= 9.0 }
			// Give the name tag time to arrive before falling back to the preview's plain leather set.
			val plainDyedSet = elapsed >= 250_000_000L && previewArmor.all { (slot, expected) ->
				val stack = entity.getItemBySlot(slot)
				stack.item === expected && stack.has(DataComponents.DYED_COLOR) && stack.accessor().rawSkyBlockId == null
			}
			DyePreviewCapture.Candidate(entity.id, namedPreview, plainDyedSet)
		}
		val target = DyePreviewCapture.target(pending.previousIds, candidates) ?: return
		pendingPreview = null
		startTrace(dyedEntities.first { it.id == target }, pending.dye)
	}

	private fun traceEntityPresent(trace: ActiveTrace): Boolean =
		!trace.entity.isRemoved && MC.world?.getEntity(trace.entity.id) === trace.entity

	private fun recordTrace(trace: ActiveTrace, source: String, nowNanos: Long, tick: Int) {
		val present = traceEntityPresent(trace)
		trace.capture.recordItem(nowNanos, tick, source, present,
			if (present) trace.entity.getItemBySlot(trace.slot) else null)
	}

	private fun startTrace(entity: Entity?, dye: DyePreviewCapture.Dye? = null) {
		if (activeTrace != null) {
			MC.sendChat(tr("firmament.fitstealer.trace.active", "Stop the current trace before a new capture."))
			return
		}
		if (entity !is LivingEntity) {
			MC.sendChat(tr("firmament.fitstealer.trace.noentity", "Aim at a player or a mannequin."))
			return
		}
		val slot = if (dye == null) selectedSlot ?: EquipmentSlot.CHEST else EquipmentSlot.CHEST
		if (entity.getItemBySlot(slot).get(DataComponents.DYED_COLOR) == null) {
			MC.sendChat(tr("firmament.fitstealer.trace.nocolor", "The selected slot has no dye color. Select a dyed armor slot."))
			return
		}
		val now = System.nanoTime()
		val capture = DyeTrace(
			now, Instant.now().toString(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString(),
			entity.id, slot.name.lowercase(), dyeId = dye?.id, dyeName = dye?.name,
		)
		val trace = ActiveTrace(capture, entity, slot, automatic = dye != null)
		activeTrace = trace
		pendingPreview = null
		recordTrace(trace, "start", now, MC.currentTick)
		if (dye == null) {
			MC.sendChat(tr("firmament.fitstealer.trace.started", "The timed dye capture has started. It stops after ${DyeTrace.DURATION_SECONDS} seconds."))
		} else {
			MC.sendChat(tr("firmament.fitstealer.trace.preview.started", "Recording the ${dye.name} preview."))
		}
	}

	private fun saveTrace(capture: DyeTrace) {
		try {
			val files = capture.save(Firmament.DATA_DIR.resolve("dye-captures"))
			lastTraceNeedsSave = false
			MC.sendChat(savedTraceMessage(files))
		} catch (exception: Exception) {
			lastTraceNeedsSave = true
			Firmament.logger.error("Could not save the timed dye capture.", exception)
			MC.sendChat(tr("firmament.fitstealer.trace.savefailed", "The save failed. Use trace save to retry."))
		}
	}

	fun savedTraceMessage(files: DyeTrace.SavedFiles): MutableComponent {
		val csvPath = files.csv.toAbsolutePath()
		return tr("firmament.fitstealer.trace.saved", "Saved the timed capture to ")
			.append(Component.literal(csvPath.toString()))
			.append(" ")
			.append(tr("firmament.fitstealer.trace.openfolder", "[Open folder]").aqua().withStyle {
				it.withUnderlined(true).withClickEvent(ClickEvent.OpenFile(csvPath.parent))
			})
			.append(" ")
			.append(tr("firmament.fitstealer.trace.copypath", "[Copy path]").aqua().withStyle {
				it.withUnderlined(true).withClickEvent(ClickEvent.CopyToClipboard(csvPath.toString()))
			})
	}

	private fun stopTrace(reason: String) {
		val trace = activeTrace
		if (trace == null) {
			MC.sendChat(tr("firmament.fitstealer.trace.inactive", "No timed capture is active."))
			return
		}
		val now = System.nanoTime()
		recordTrace(trace, "stop", now, MC.currentTick)
		trace.capture.finish(now, reason)
		activeTrace = null
		lastTrace = trace.capture
		saveTrace(trace.capture)
	}

	private fun stopManualTrace() {
		pendingPreview = null
		stopTrace("manual")
	}

	@Subscribe
	fun onTraceKey(event: WorldKeyboardEvent) {
		val player = MC.player ?: return
		if (MC.screen != null) return
		val config = DeveloperFeatures.TConfig
		val self = event.matches(config.traceSelfKey)
		if (!self && !event.matches(config.traceTargetKey)) return
		event.cancel()
		if (event.isRepeat) return

		if (activeTrace != null) stopManualTrace()
		else startTrace(if (self) player else MC.instance.crosshairPickEntity)
	}

	@Subscribe
	fun onTraceHud(event: HudRenderEvent) {
		val trace = activeTrace ?: return
		val seconds = trace.capture.remainingSeconds(System.nanoTime())
		val countdown = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
		val label = tr("firmament.fitstealer.trace.countdown", "Dye capture $countdown")
		val font = MC.font
		val width = font.width(label)
		val x = (MC.window.guiScaledWidth - width) / 2
		val y = 8
		event.context.fill(x - 4, y - 3, x + width + 4, y + font.lineHeight + 3, 0xAA000000.toInt())
		event.context.text(font, label, x, y, -1, true)
	}

	@Subscribe
	fun onTraceTick(event: TickEvent) {
		val now = System.nanoTime()
		val trace = activeTrace
		if (trace != null) {
			if (trace.automatic && !DeveloperFeatures.TConfig.autoRecordDyePreviews) stopTrace("auto_disabled")
			else if (trace.automatic && pendingPreview != null) stopTrace("preview_changed")
			else {
				recordTrace(trace, "tick", now, event.tickCount)
				if (!traceEntityPresent(trace)) stopTrace("entity_lost")
				else if (trace.capture.hasReachedLimit(now)) stopTrace("time_limit")
			}
		}
		pollPreview(now)
	}

	@OptIn(ExperimentalStdlibApi::class)
	@Subscribe
	fun onUpdate(event: EntityUpdateEvent) {
		val trace = activeTrace
		if (trace != null && event is EntityUpdateEvent.EquipmentUpdate && event.entity === trace.entity) {
			event.newEquipment.filter { it.first == trace.slot }.forEach {
				trace.capture.recordItem(System.nanoTime(), MC.currentTick, "equipment", traceEntityPresent(trace), it.second, event.receivedNanos)
			}
		}
		val s = subject ?: return
		if (event.entity != s) return
		val l = lens ?: return
		if (event is EntityUpdateEvent.EquipmentUpdate) {
			history.addAll(l.observeEquipment(event.newEquipment, selectedSlot))
		}
	}

	fun reduceHistory(reducer: (List<String>, List<String>) -> List<String>): List<String> {
		return metaHistory.fold(history, reducer).shortenCycle()
	}

	@Subscribe
	fun onSubCommand(event: CommandEvent.SubCommand) {
		event.subcommand(DeveloperFeatures.DEVELOPER_SUBCOMMAND) {
			thenLiteral("stealthisfit") {
				thenLiteral("trace") {
					thenLiteral("auto") {
						thenExecute {
							val config = DeveloperFeatures.TConfig
							config.autoRecordDyePreviews = !config.autoRecordDyePreviews
							MC.sendChat(if (config.autoRecordDyePreviews)
								tr("firmament.fitstealer.trace.preview.enabled", "Automatic dye capture is on. Right-click a dye in Vincent's Dye Guide.")
							else tr("firmament.fitstealer.trace.preview.disabled", "Automatic dye capture is off."))
						}
					}
					thenLiteral("self") { thenExecute { startTrace(MC.player) } }
					thenLiteral("target") { thenExecute { startTrace(MC.instance.crosshairPickEntity) } }
					thenLiteral("stop") { thenExecute { stopManualTrace() } }
					thenLiteral("save") {
						thenExecute {
							val capture = lastTrace
							if (capture == null) MC.sendChat(tr("firmament.fitstealer.trace.nosaved", "No completed timed capture is available."))
							else saveTrace(capture)
						}
					}
				}
				thenLiteral("clear") {
					thenExecute {
						subject = null
						metaHistory.clear()
						history.clear()
						MC.sendChat(tr("firmament.fitstealer.clear", "Cleared fit stealing history"))
					}
				}
				thenLiteral("copy") {
					thenExecute {
						val history = reduceHistory { a, b -> a + b }
						copyHistory(history)
						MC.sendChat(tr("firmament.fitstealer.copied", "Copied the history"))
					}
					thenLiteral("deduplicated") {
						thenExecute {
							val history = reduceHistory { a, b ->
								(a.toMutableSet() + b).toList()
							}
							copyHistory(history)
							MC.sendChat(
								tr(
									"firmament.fitstealer.copied.deduplicated",
									"Copied the deduplicated history"
								)
							)
						}
					}
					thenLiteral("merged") {
						thenExecute {
							val history = reduceHistory(GChainReconciliation::reconcileCycles)
							copyHistory(history)
							MC.sendChat(tr("firmament.fitstealer.copied.merged", "Copied the merged history"))
						}
					}
				}
				thenLiteral("target") {
					thenLiteral("self") {
						thenExecute {
							toggleObserve(MC.player!!)
						}
					}
					thenLiteral("pet") {
						thenExecute {
							source.sendFeedback(
								tr(
									"firmament.fitstealer.stealingpet",
									"Observing nearest marker armourstand"
								)
							)
							val p = MC.player!!
							val nearestPet = p.level.getEntitiesOfClass(
								ArmorStand::class.java,
								p.boundingBox.inflate(10.0),
								{ it.isMarker })
								.minBy { it.distanceToSqr(p) }
							toggleObserve(nearestPet)
						}
					}
					thenExecute {
						val ent = MC.instance.crosshairPickEntity
						if (ent == null) {
							source.sendFeedback(
								tr(
									"firmament.fitstealer.notargetundercursor",
									"No entity under cursor"
								)
							)
						} else {
							toggleObserve(ent)
						}
					}
				}
				thenLiteral("slot") {
					thenLiteral("all") {
						thenExecute {
							selectedSlot = null
							MC.sendChat(tr("firmament.fitstealer.slot.all", "The scanner records all equipment slots."))
						}
					}
					mapOf(
						"head" to EquipmentSlot.HEAD,
						"chest" to EquipmentSlot.CHEST,
						"legs" to EquipmentSlot.LEGS,
						"feet" to EquipmentSlot.FEET,
					).forEach { (slotName, equipmentSlot) ->
						thenLiteral(slotName) {
							thenExecute {
								selectedSlot = equipmentSlot
								MC.sendChat(tr("firmament.fitstealer.slot.selected", "The scanner records the $slotName slot."))
							}
						}
					}
				}
				thenLiteral("path") {
					thenArgument(
						"component",
						ResourceKeyArgument.key(Registries.DATA_COMPONENT_TYPE)
					) { component ->
						thenArgument("path", NbtPrism.Argument) { path ->
							thenExecute {
								lens = LensOfFashionTheft(
									get(path),
									MC.unsafeGetRegistryEntry(get(component))!!,
								)
								source.sendFeedback(
									tr(
										"firmament.fitstealer.lensset",
										"Analyzing path ${get(path)} for component ${get(component).identifier()}"
									)
								)
							}
						}
					}
				}
			}
		}
	}

	private fun copyHistory(toCopy: List<String>) {
		ClipboardUtils.setTextContent(toCopy.joinToString("\n"))
	}

	@Subscribe
	fun onWorldSwap(event: WorldReadyEvent) {
		pendingPreview = null
		if (activeTrace != null) stopTrace("world_change")
		subject = null
		if (history.isNotEmpty()) {
			metaHistory.add(history)
			history = mutableListOf()
		}
	}

	private fun toggleObserve(entity: Entity?) {
		subject = if (subject == null) entity else null
		if (subject == null) {
			metaHistory.add(history)
			history = mutableListOf()
		}
		MC.sendChat(
			subject?.let {
				tr(
					"firmament.fitstealer.targeted",
					"Observing the equipment of ${it.name}."
				)
			} ?: tr("firmament.fitstealer.targetlost", "No longer logging equipment."),
		)
	}
}
