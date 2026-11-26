# CrafterConnect

CrafterConnect is a high-performance, real-time delivery, store and chest integration plugin for game servers that run on the **crafter.net.tr** platform.

## ⚠️ Important Security Notice (Critical)

CrafterConnect identifies players and acts on their behalf directly by the **username** they join the server with. Therefore:

- **Premium servers:** The plugin is only fully secure on original (premium) servers.
- **Cracked servers:** If your server is "cracked", you **must** run this plugin behind `AuthMe`, `LimboAuth` or a similar authentication plugin.
- **Risk:** On servers without authentication, a player can join with someone else's name and claim the items in that player's chest or spend their credit. Crafter cannot be held responsible for this.

---

## 🚀 Installation and Usage (2.0)

### 1. Install the plugin

| Platform | File | Folder |
| :-- | :-- | :-- |
| Paper / Spigot / Folia (1.18+) | `crafter-connect-spigot-2.0.0.jar` | the server's `plugins/` folder |
| Velocity | `crafter-connect-velocity-2.0.0.jar` | the proxy's `plugins/` folder |
| BungeeCord / Waterfall | `crafter-connect-bungee-2.0.0.jar` | the proxy's `plugins/` folder |

On a proxy, delivery commands run on the proxy console. For commands that must run on a backend server (e.g. `give`),
install the Paper plugin on the backend servers as well; each install connects to Crafter as a separate server. If the
proxy also runs the plugin, set `report-players: false` in the backend servers' `config.yml` (so players are not counted twice).

### 2. Connect

1. In the dashboard, create a connection code with **Settings → Servers → Connect a server** (valid for 15 minutes, single use).
2. Run in the console (or in game with the `crafter.admin` permission):

```text
/crafter connect K7QM-4XPD
```

The plugin registers itself, writes the server-specific **server token** (`cct_…`) to `config.yml` and restricts the
file's permissions to the server user only (600). The token is never written to logs or chat. You do not need to copy a
website ID, secret or server ID. If the machine changes or the token has leaked, create a new code with **Reconnect** in
the dashboard and run the same command; the old token is invalidated immediately.

**Legacy installs:** If `website-id`, `plugin-secret` and `server-id` are set and `server-token` is empty, the plugin
keeps connecting the old way and shows a console warning asking you to create a new code in the dashboard and run
`/crafter connect`.

### 3. Configuration (`config.yml`)

```yaml
api-url: "api.crafter.net.tr"
server-token: ""                 # written by /crafter connect; do not edit or share
language: "tr"                   # tr or en
allowed-command-prefixes: []     # if set, only commands starting with these prefixes run (e.g. ["lp user", "give"])
report-players: true             # false on servers behind a proxy
debug-mode: false
```

### 4. Commands and permissions

| Command | Permission | Description |
| :-- | :-- | :-- |
| `/crafter connect <code>` | `crafter.admin` | Connects the server to Crafter with a connection code (also works from the console). |
| `/crafter disconnect` | `crafter.admin` | Disconnects and removes the server token. |
| `/crafter status` | `crafter.admin` | Connection, store, server ID, version and pending commands. |
| `/crafter reload` | `crafter.admin` | Reloads the config and language files and reconnects. |
| `/crafter magaza [category]` | `crafter.shop` | In-game store (Paper/Spigot). |
| `/crafter sandik` | `crafter.chest` | Chest of purchased items (Paper/Spigot). |

> [!NOTE]
> To give these permissions to all players by default, add `crafter.shop` and `crafter.chest` to the `default` group with a plugin such as LuckPerms.

### 5. Delivery and security

- **Persistent queue:** If the player is offline, the command waits in `plugins/CrafterConnect/data/commands.json` and
  runs when the player joins (name is case-insensitive; by UUID for Microsoft-verified players). Commands that arrive while
  the server is down are kept by Crafter and sent in order once the server connects.
- **Run once:** Every command's `commandId` is stored; if the same command arrives again it is not run, only the
  acknowledgement is repeated. Acknowledgements are written to disk while disconnected and sent once connected.
- **Command protection:**
  - The player name (`player` and the `%player%` filled in by the plugin) must follow the Java (`[A-Za-z0-9_]{1,16}`) or
    Bedrock/Floodgate (`.` prefix, single spaces between words) rule. This rule applies only to the player name; apart from
    spaces the name cannot contain quotes or characters such as `; & | $ @ %`.
  - Line breaks and control characters are rejected anywhere in the command text. Characters such as `;` in the command
    itself are not rejected (the seller writes the command; the Bukkit console does not split commands on `;`).
  - If both `allowed-command-prefixes` and the list in the dashboard are set, the command must match both.
  - A rejected command is reported to Crafter and the order goes to review; if the same command arrives again it is rejected again.
- Commands run with console permissions on the main thread (on Folia, the global region scheduler).
- **Bedrock (Geyser/Floodgate):** If Floodgate is installed, its prefix is read from it and reported to Crafter.

---

## 🔨 Building

Always produce release jars with a clean build (so stale `target/` files do not get mixed in):

```bash
mvn -B clean package
```

Jars: `spigot/target/crafter-connect-spigot-<version>.jar`, `velocity/target/…`, `bungee/target/…`
(do not use the `original-*.jar` files). The same command runs in GitHub Actions on every push and PR.

---

## 🏗️ Features

- **Real-time delivery:** Purchases reach the server instantly, without a page refresh.
- **Advanced GUI:** A dynamically laid out, categorized and polished store menu.
- **Chest system:** Items bought on the website are kept safely on the server; players claim them any time with `/sandik`.
- **100% localization:** All messages, button names and placeholder outputs can be edited in `lang/tr.yml` or `lang/en.yml`.

---

## 📊 PlaceholderAPI Integration

The plugin is fully integrated with PlaceholderAPI (PAPI), so you can use data from your website in other plugins such as scoreboards, tab lists or holograms.

### Available placeholders

| Placeholder                              | Description                       | Example output   |
| :--------------------------------------- | :-------------------------------- | :--------------- |
| `%crafterconnect_balance%`               | The player's current credit       | `150.00 Balance` |
| `%crafterconnect_role%`                  | The player's role on the website  | `VIP` or `Guest` |
| `%crafterconnect_user_id%`               | The player's unique ID            | `839f27e4-...`   |
| `%crafterconnect_total_users%`           | Total registered members          | `1,245`          |
| `%crafterconnect_last_purchase_user%`    | Last buyer                        | `efesoroglu`     |
| `%crafterconnect_last_purchase_product%` | Name of the last purchased item   | `VIP+ Package`   |
| `%crafterconnect_top_loader_user%`       | Player who topped up the most     | `Steve123`       |
| `%crafterconnect_top_loader_amount%`     | Largest top-up amount             | `500.00`         |

> [!TIP]
> The balance format and default texts (such as the guest role name) are fully customizable in the language files (`lang/tr.yml`, `lang/en.yml`).

### How to use

1. Make sure **PlaceholderAPI** is installed on your server.
2. CrafterConnect registers itself with PAPI automatically when it loads.
3. Use the placeholders above in any compatible plugin (e.g. DeluxeMenus, TAB, FeatherBoard).

---

## 🛠️ Technical Design

- **Asynchronous architecture:** All network work runs without burdening the main server thread (no TPS impact).
- **Two-way communication:** The server does not only pull data from the backend; the backend also pushes packets to the server in real time.
- **Maven modules:** `core` (shared logic), `spigot` (in-game UI), `velocity` and `bungee` (proxy support).

**Build command:**

```bash
mvn clean package
```

---

## 📜 License

This project is licensed under the **MIT License**. See the `LICENSE` file for details.
