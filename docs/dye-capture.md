<!--
SPDX-FileCopyrightText: 2026 Firmament Contributors

SPDX-License-Identifier: CC0-1.0
-->

# Dye capture

Use Minecraft 26.1.2 with Fabric.
Use Java 25 to build this branch.

## Build and install

1. Run `./gradlew assemble` from the repository directory.
2. Close Minecraft.
3. Disable the current Firmament file in Prism Launcher.
4. Add the mod file from `build/libs`.
5. Enable only 1 Firmament file.
6. Retain the existing Fabric dependencies.
7. Start Minecraft.

## Countdown and keybinds

The countdown appears at the top of the HUD during any active trace.
It starts at 3:00 and disappears when the trace ends.
Assign **Dye trace self** or **Dye trace target** in Firmament's Developer Settings.
Both bindings start unassigned. In the world, press either assigned key during a trace to stop and save it early.

## Capture previews automatically

Enable **Record Dye Guide previews** in Firmament's developer options or use the toggle command below.

```text
/firmament dev stealthisfit trace auto
```

Right-click a dye in Vincent's Dye Guide. Wait for the recorder to announce the dye name, then let the preview finish. Firmament samples the preview chestplate and saves when the preview disappears. Starting another preview saves the previous trace and starts a separate capture.

Automatic captures include the dye name in their metadata. When the guide provides an item ID, the filename includes it as well, such as `dye-trace-DYE_BEACH-<identifier>.csv`. If Firmament cannot identify the preview, use the manual target command below while aiming at its chest.

## Capture your armor

1. Equip the dyed armor piece.
2. Select its slot:

   ```text
   /firmament dev stealthisfit slot chest
   ```

3. Start the capture:

   ```text
   /firmament dev stealthisfit trace self
   ```

4. Keep the same armor equipped for up to 180 seconds.
5. Wait for the save message.
6. Repeat steps 3–5 for the second capture.

The trace automatically stops after 180 seconds.

The trace selects the dye color component automatically.
It samples the equipped color on each client tick.
It also records equipment updates as separate rows.
It retains repeated colors and repeated cycles.

Use `slot legs`, `slot feet` or `slot head` for another armor slot.
The trace defaults to the chest slot if no individual slot has a selection.

## Capture another entity

1. Select the dyed armor slot.
2. Aim at the player's body or the mannequin's body.
3. Run this command:

   ```text
   /firmament dev stealthisfit trace target
   ```

4. Stay near the selected entity.
5. Wait for the save message.

The trace retains the selected entity after the crosshair moves.
Other players in front of the target do not change the selection.
It also stops if the target leaves the client.
It stops if the world changes.
The metadata records the stop reason.
A preview timeout can prevent a complete cycle capture.

## Stop or repeat a save

Use this command to stop the capture early:

```text
/firmament dev stealthisfit trace stop
```

Use this command to repeat the last completed capture's save:

```text
/firmament dev stealthisfit trace save
```

## Output files

The trace saves 2 files under the instance's Minecraft directory:

```text
.firmament/dye-captures/dye-trace-<identifier>.csv
.firmament/dye-captures/dye-trace-<identifier>.json
```

The CSV file contains all observations.
The JSON file contains the slot, target type, duration and stop reason. Automatic preview captures also record the dye name and any available dye ID.
Keep both files.
The chat displays the absolute CSV path.
Select `Open folder` in the save message to open the capture directory.
Select `Copy path` to copy the CSV path to the clipboard.

| CSV column | Meaning |
| --- | --- |
| `elapsed_ns` | Elapsed nanoseconds from a monotonic clock. |
| `client_tick` | Client tick number. |
| `source` | `start`, `tick`, `equipment` or `stop`. |
| `entity_present` | Whether the client still contains the selected entity. |
| `rgb` | Decimal dye color. An empty value indicates no observed color. |
| `item_id` | Minecraft item identifier. |
| `received_elapsed_ns` | Time since the start when the client received the decoded packet. Bundled updates share a receipt time. |
| `applied_elapsed_ns` | Time since the start when the client recorded the applied equipment update. |

Version 2 adds both timing columns. Tick samples have empty packet times.
An equipment update without a receipt timestamp still records its application time.

## Original scanner commands

The original scanner also supports the armor slot filter.
Use `slot all` to restore its original equipment selection.
The `clear` command retains the slot selection.

Use the complete component selector for the dye color:

```text
/firmament dev stealthisfit path minecraft:dyed_color "."
```

The `.` selector reads the complete component value.
The trace commands select the dye color directly.
The original `copy` command removes repeated cycles.
Use the trace output to retain repeated cycles and timestamps.

## Analysis limits

The trace measures the colors that this client receives and retains.
The timestamps show color holds and gaps between observations.
The trace does not contain the server's animation definition.
Compare multiple complete cycles before a contribution.
Compare inventory and remote-entity observations if their results differ.
