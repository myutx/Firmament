package moe.nea.firmament.events

import moe.nea.firmament.keybindings.GenericInputAction
import moe.nea.firmament.keybindings.InputModifiers
import moe.nea.firmament.keybindings.SavedKeyBinding

data class WorldKeyboardEvent @JvmOverloads constructor(
	val action: GenericInputAction,
	val modifiers: InputModifiers,
	val isRepeat: Boolean = false,
) : FirmamentEvent.Cancellable() {
	fun matches(keyBinding: SavedKeyBinding, atLeast: Boolean = false): Boolean {
		return keyBinding.matches(action, modifiers, atLeast)
	}

	companion object : FirmamentEventBus<WorldKeyboardEvent>()
}
