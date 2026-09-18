<div align="center">

<img src="branding/ShopkeeperNexo-resource-icon-v2-master.png" alt="ShopkeeperNexo resource icon" width="144">

# ShopkeeperNexo

**Keep Nexo custom items in Shopkeepers trades up to date—automatically.**

![Paper 26.2](https://img.shields.io/badge/Paper-26.2-2C2C2C?style=for-the-badge)
![Java 25](https://img.shields.io/badge/Java-25-E76F00?style=for-the-badge)
![Nexo 1.28.0](https://img.shields.io/badge/Nexo-1.28.0-7C4DFF?style=for-the-badge)
![Shopkeepers 2.27.0](https://img.shields.io/badge/Shopkeepers-2.27.0-36A269?style=for-the-badge)

[Features](#features) · [Installation](#installation) · [Commands](#commands) · [How it works](#how-it-works) · [Building](#building)

</div>

## Why ShopkeeperNexo?

When a Nexo item definition changes, Shopkeepers may still hold an older copy
of that item inside its saved trades. The item can look correct while its stored
data no longer matches Nexo's current definition, causing an otherwise valid
trade to be rejected.

ShopkeeperNexo connects Shopkeepers' item-update system to Nexo's official item
updater. Saved Nexo items are refreshed after item definitions load or reload,
without rebuilding every trade by hand.

## Features

- Synchronizes automatically at server startup.
- Synchronizes whenever Nexo finishes loading or reloading item definitions.
- Provides an administrator command for on-demand synchronization.
- Reports dependency versions, registry readiness, sync state, and the last
  result.
- Uses Nexo's own update policy, preserving stack amounts and supported
  per-item state.
- Participates in `/shopkeepers updateItems`.
- Ignores vanilla items.
- Keeps items with removed Nexo definitions unchanged instead of silently
  deleting them.
- Isolates individual item failures so one bad item does not stop the complete
  update.
- Coalesces overlapping synchronization requests into one run.
- Requires no configuration and performs no scheduled polling.

## Requirements

| Component | Required version |
| --- | --- |
| Server | Paper 26.2 |
| Java | 25 |
| Nexo | 1.28.0 |
| Shopkeepers | 2.27.0 |

Nexo and Shopkeepers are hard dependencies. ShopkeeperNexo will not load unless
both plugins are installed and enabled.

## Installation

1. Stop the server.
2. Install Nexo and Shopkeepers, then confirm that both load successfully.
3. Download a ShopkeeperNexo release JAR and place it in the server's `plugins`
   directory.
4. Start the server.
5. Run `/sknexo status` to verify that the integration is ready.

There is no configuration file to edit.

## Commands

| Command | Description |
| --- | --- |
| `/shopkeepernexo sync` | Queues a full item synchronization for the next server tick and reports the result when it finishes. |
| `/shopkeepernexo status` | Shows dependency versions, registered Nexo definitions, current sync state, and the most recent result. |
| `/sknexo ...` | Short alias for the commands above. |

### Permission

| Permission | Description | Default |
| --- | --- | --- |
| `shopkeepernexo.admin` | Allows access to the `sync` and `status` commands. | Server operators |

Tab completion is permission-aware.

## How it works

For every item exposed by the Shopkeepers update API, ShopkeeperNexo:

1. Checks whether the item contains a Nexo ID.
2. Leaves the item untouched if it is a vanilla item.
3. Verifies that the referenced Nexo definition still exists.
4. Passes recognized items to `NexoItems.updateItem`.
5. Replaces the stored item only when its data has changed.

A complete Shopkeepers item scan is queued when:

- ShopkeeperNexo starts and Nexo definitions are already available;
- Nexo fires its items-loaded event after loading or reloading definitions; or
- an administrator runs `/shopkeepernexo sync`.

Requests made before a queued run begins are combined. Each completed run logs
the number of Nexo items inspected, updated, missing a definition, or unable to
be processed, along with its duration.

## Safe handling

- A vanilla item is never passed to the Nexo updater.
- An item whose Nexo definition has been removed is retained unchanged.
- An exception while processing one item is logged and counted without
  terminating the rest of the synchronization.
- Pending work is cancelled when the plugin is disabled.

## Limitations

- Shopkeepers' update API does not traverse historical trading records, so the
  addon cannot update those records.
- A complete update can close active Shopkeepers editor or trading sessions.
  This is normal behavior of `ShopkeepersAPI.updateItems()`.
- ShopkeeperNexo updates stored trade items; it does not add a Nexo item picker
  to the Shopkeepers editor.

## Building

Build and run the test suite with Maven and JDK 25:

```shell
mvn clean verify
```

The current snapshot artifact is written to:

```text
target/ShopkeeperNexo-1.0.0-SNAPSHOT.jar
```

The test suite covers item filtering and updates, missing definitions, updater
failures, Nexo reload synchronization, request coalescing, shutdown behavior,
commands, permissions, status output, and tab completion.

## Before publishing a release

- Replace `1.0.0-SNAPSHOT` with the intended release version.
- Run `mvn clean verify` with JDK 25.
- Test the built JAR on a clean Paper server with the documented dependency
  versions.
- Verify startup synchronization, a Nexo reload, `/sknexo sync`,
  `/sknexo status`, and `/shopkeepers updateItems`.

---

<div align="center">

**Update your Nexo definitions. Reload. Keep trading.**

</div>
