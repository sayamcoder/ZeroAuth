# ZeroAuth

ZeroAuth is a secure, limbo-style authentication plugin for modern Minecraft servers. Players are moved to a separate authentication world when they join and must register or log in before they can return to their previous location and use the server normally.

## Features

- Password registration and login.
- Passwords stored as hashes rather than plain text.
- Configurable minimum password length.
- Separate void authentication world.
- Automatic return to the player's previous location after authentication.
- Movement, teleportation, damage, hunger, inventory access, and commands blocked while unauthenticated.
- Configurable command allowlist for unauthenticated players.
- Flat-file, MySQL, PostgreSQL, SQLite, and MongoDB storage options.
- MiniMessage, legacy `&` colors, and hex color support in messages.
- Configurable join event scripts.
- Administrator commands for reload, spawn management, forced authentication, logout, and status checks.
- Maven Shade packaging with the Kotlin and relocated Adventure runtime dependencies included in the final JAR.

## Requirements

- Java 17 or newer.
- A Paper server compatible with the Paper API used by this project.
- Minecraft/Paper 1.20.6 or newer is recommended. The plugin has been prepared for modern Paper 1.21.1 servers.
- Permission to create and manage a world in the server's world container.

ZeroAuth uses Paper APIs at runtime. Paper is recommended over an older or unmodified Spigot implementation. The server API is compile-only and is not bundled into the plugin JAR.

## Building the plugin

This project uses **Maven only**. Do not create or use `build.gradle`, `build.gradle.kts`, or any Gradle wrapper files.

From the project root, run:

```bash
mvn clean package
```

The Maven Shade Plugin creates the uploadable plugin JAR at:

```text
target/zeroauth-1.1.1.jar
```

Use the shaded JAR from `target`, not an intermediate or source JAR. The shaded file contains the plugin classes, Kotlin runtime, and relocated Adventure MiniMessage runtime required by ZeroAuth. Relocation prevents conflicts with Adventure versions supplied by the server or other plugins.

The database drivers are intentionally excluded from the shaded JAR because Paper loads them from the `libraries` section of `plugin.yml` when a supported Paper server starts the plugin. Flat-file storage does not require any additional database setup.

## Installing on a server

1. Stop the Minecraft server.
2. Remove older or duplicate ZeroAuth files from the `plugins` directory, especially files such as `ZeroAuth-1.0.0 (2).jar`.
3. Copy `target/zeroauth-1.1.1.jar` into the server's `plugins` directory.
4. Start the server.
5. Confirm that the console reports that ZeroAuth enabled successfully.
6. Stop the server once after the first successful start if you need to edit the generated configuration.
7. Edit `plugins/ZeroAuth/config.yml` as required.
8. Start the server again, or run `/zeroauth reload` after changing reloadable settings.

Do not place the JAR inside `plugins/.paper-remapped`. Paper creates and manages that directory automatically. Always upload the original shaded JAR to `plugins`.

### Important upload notes

- Upload only one ZeroAuth JAR. Duplicate versions can cause confusion about which version is being loaded.
- Do not rename the JAR to a name containing a second version or copy number.
- Back up `plugins/ZeroAuth/users.yml` before changing storage types or updating the plugin.
- If using database storage, ensure the database server is running and the credentials are valid before starting Minecraft.
- The first startup creates the authentication world and the default event script.

## First-time setup

The default storage type is `flatfile`, so no database is required for a basic installation.

1. Start the server with ZeroAuth installed.
2. Join the server.
3. You will be moved to the configured authentication world.
4. Register with:

   ```text
   /register <password> <password>
   ```

5. After successful registration, you are authenticated and returned to your saved location.
6. On future joins, use:

   ```text
   /login <password>
   ```

The default minimum password length is eight characters. Passwords should not be shared with staff or entered into public chat where other players can see them.

## Commands

### Player commands

| Command | Description |
|---|---|
| `/register <password> <password>` | Creates an account and authenticates the player when both passwords match. |
| `/login <password>` | Logs in to an existing account. |

Only players can use `/register` and `/login`. Console execution is rejected.

### Administrator command

| Command | Description |
|---|---|
| `/zeroauth reload` | Reloads `config.yml` and all event scripts. |
| `/zeroauth setspawn` | Saves the administrator's current position as the authentication-world spawn. The command must be run while standing in the authentication world. |
| `/zeroauth auth <player>` | Forces an online player to become authenticated. |
| `/zeroauth logout <player>` | Logs out an online player and sends them to the authentication world. |
| `/zeroauth status <player>` | Displays whether an online player is registered and authenticated. |
| `/zauth <subcommand>` | Alias for `/zeroauth`. |

The administrator command requires:

```text
zeroauth.admin
```

This permission defaults to server operators. The player argument must be an exact name of an online player.

## Authentication protection

While a player is unauthenticated, ZeroAuth:

- Keeps the player in the authentication world.
- Prevents movement out of that world.
- Prevents teleportation out of that world.
- Cancels damage.
- Prevents hunger changes.
- Prevents inventory opening.
- Blocks commands not listed in `security.allowed-unauthenticated-commands`.
- Temporarily enables flight and restores the player's previous flight state after authentication.

After registration or login, the player is returned to the last saved non-authentication-world location. Locations are saved when an authenticated player quits or logs out.

## Configuration

The configuration file is generated at:

```text
plugins/ZeroAuth/config.yml
```

### Authentication world

```yaml
auth-world:
  name: virtual_world_auth
  mode: void
  reset-existing-world: true
  environment: THE_END
  spawn:
    x: 0.5
    y: 100.0
    z: 0.5
    yaw: 0.0
    pitch: 0.0
```

| Setting | Description |
|---|---|
| `auth-world.name` | World name used for authentication. |
| `auth-world.mode` | Current supported mode is `void`. |
| `auth-world.reset-existing-world` | When enabled, ZeroAuth resets an existing world that is not marked as its current ZeroAuth void world. Use carefully. |
| `auth-world.environment` | Bukkit environment, such as `NORMAL`, `NETHER`, or `THE_END`. |
| `auth-world.spawn.x/y/z` | Spawn coordinates for unauthenticated players. |
| `auth-world.spawn.yaw/pitch` | Spawn rotation. |

The void world is generated automatically. ZeroAuth disables mob spawning, removes Ender Dragons, sets peaceful difficulty, disables PvP, and creates a small bedrock platform below the configured spawn. `/zeroauth setspawn` updates the spawn values in `config.yml`.

> **Warning:** Enabling `reset-existing-world` can delete an existing world folder when it is not recognized as the current ZeroAuth void world. Never use the authentication-world name for a world containing important builds unless you have a backup.

### Storage

```yaml
storage:
  type: flatfile
  mysql:
    url: jdbc:mysql://localhost:3306/zeroauth
    username: zeroauth
    password: change-me
  postgresql:
    url: jdbc:postgresql://localhost:5432/zeroauth
    username: zeroauth
    password: change-me
  sqlite:
    file: zeroauth.db
  mongodb:
    connection-string: mongodb://localhost:27017
    database: zeroauth
    collection: users
```

Supported values for `storage.type` are:

| Type | Data location |
|---|---|
| `flatfile` or `yaml` | `plugins/ZeroAuth/users.yml` |
| `mysql` | The configured MySQL JDBC URL and database. |
| `postgresql` | The configured PostgreSQL JDBC URL and database. |
| `sqlite` | The file configured by `storage.sqlite.file`, normally inside the plugin data folder. |
| `mongodb` or `mongo` | The configured MongoDB connection, database, and collection. |

For MySQL or PostgreSQL, create the database and user first, then replace the example URL, username, and password. For MongoDB, replace the connection string and database details. The required JDBC and MongoDB drivers are declared in `plugin.yml` for Paper library loading.

If a selected storage provider cannot be initialized, ZeroAuth logs the problem and falls back to flat-file storage. Check the server console after changing storage settings.

### Security

```yaml
security:
  minimum-password-length: 8
  allowed-unauthenticated-commands:
    - login
    - register
    - help
    - zeroauth
```

`minimum-password-length` controls the shortest accepted password. The commands in `allowed-unauthenticated-commands` remain available before login. Add command labels without the leading `/`.

Only add commands that are safe for unauthenticated players. Adding a command to this list bypasses ZeroAuth's command block for players who have not logged in.

### Messages and colors

Messages are under `messages` in `config.yml`. They support:

- MiniMessage tags, for example `<green>` and `<gradient:#00d4ff:#7a5cff>`.
- Legacy colors, for example `&a` and `&l`.
- Hex colors, for example `&#00d4ff`.
- `{player}`, `{registered}`, and `{authenticated}` placeholders where applicable.

The configurable message keys include `prefix`, `join`, `registered`, `logged-in`, `logged-out`, `already-registered`, `not-registered`, `invalid-password`, `password-mismatch`, `already-authenticated`, `command-blocked`, `player-not-found`, `no-permission`, `reload`, `spawn-set`, `forced-auth`, `forced-logout`, `status`, and `usage`.

## Join event scripts

On startup, ZeroAuth creates:

```text
plugins/ZeroAuth/events/join.yml
```

Every enabled YAML file in the `events` folder with `event: join` contributes actions that run after a player successfully registers or logs in. Files are loaded alphabetically.

Example:

```yaml
enabled: true
event: join
actions:
  - 'message:<green>Welcome back, {player}!'
  - 'console:give {player} bread 8'
  - 'player:spawn'
```

Supported action formats:

| Format | Result |
|---|---|
| `console:<command>` | Executes the command from the console. |
| `player:<command>` | Makes the authenticated player execute the command. |
| `message:<text>` | Sends a colored message to the player. |
| `<text>` | Sends the text as a colored message. |

Available placeholders are `{player}`, `{uuid}`, and `{world}`. Set `enabled: false` to disable a script. Reload scripts with `/zeroauth reload`.

## Data and backup

With flat-file storage, registered accounts and saved locations are stored in:

```text
plugins/ZeroAuth/users.yml
```

Back up this file before reinstalling, changing storage providers, or making database migrations. Never publish it because it contains password hashes and player data.

ZeroAuth also creates its authentication world in the server world container. Back up or remove that world only when the server is stopped and you understand the effect on unauthenticated players.

## Troubleshooting

### `NoClassDefFoundError` for MiniMessage

Build and upload the Maven shaded JAR:

```bash
mvn clean package
```

Then upload `target/zeroauth-1.1.1.jar` and remove old duplicate JARs. Do not upload an intermediate Maven artifact or a previously built `ZeroAuth-1.0.0` file. Run `mvn clean package` so stale classes from an older build cannot remain in the JAR.

### The plugin is not detected

- Confirm the file is directly inside `plugins/`.
- Confirm it ends with `.jar`.
- Confirm there is only one ZeroAuth JAR.
- Check that the server is running Java 17 or newer.
- Read the first ZeroAuth error in the console; later errors may only be consequences of the first one.

### A database storage provider does not start

- Verify the database server is online.
- Verify the JDBC URL, username, password, database name, and network access.
- Confirm that the server can download libraries if running Paper's library loader.
- Check the console for the selected storage type and the driver error.
- Temporarily set `storage.type: flatfile` to start without an external database.

### Players cannot leave the authentication world

This is expected until they successfully register or log in. Verify that the player is using `/register` for a new account or `/login` for an existing account. Administrators can use `/zeroauth auth <player>`.

### The authentication world is incorrect or contains old terrain

Stop the server, back up important data, verify `auth-world.name`, and decide whether `auth-world.reset-existing-world` should be enabled. The authentication-world folder may be recreated on startup when it is not recognized as a ZeroAuth void world.

## Updating

1. Back up `plugins/ZeroAuth/`, including `users.yml`, `config.yml`, and `events/`.
2. Stop the server.
3. Remove the old ZeroAuth JAR.
4. Copy the new shaded Maven JAR into `plugins/`.
5. Start the server and inspect the console.
6. Compare any newly generated configuration with your backup and reapply custom settings carefully.

Do not overwrite `users.yml` unless you intentionally want to replace the stored accounts.

## Project layout

```text
ZeroAuth/
├── pom.xml
├── ReadME.md
├── src/
│   ├── main/kotlin/dev/zerostudios/zeroauth/
│   │   ├── ZeroAuthPlugin.kt
│   │   ├── auth/
│   │   ├── command/
│   │   ├── event/
│   │   ├── model/
│   │   ├── security/
│   │   ├── storage/
│   │   └── world/
│   └── main/resources/
│       ├── config.yml
│       ├── events/join.yml
│       └── plugin.yml
└── target/
    └── zeroauth-1.1.1.jar
```

The source is Kotlin, but the build and packaging process is Maven. The Paper API is provided by the server and is not bundled into the final JAR.

## License and support

ZeroAuth is maintained by ZeroStudios. For deployment issues, include the Minecraft version, server implementation and build, Java version, ZeroAuth version, and the first relevant console error. Never include passwords, database credentials, `users.yml`, or private connection strings in support requests.