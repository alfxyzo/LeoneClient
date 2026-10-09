<p align="center">
  <img src="src/main/resources/assets/leoneclient/icon.png" width="96" alt="Leone Client logo" />
</p>

<h1 align="center">Leone Client</h1>

<p align="center">The client mod for <a href="https://leonemc.net">LeoneMC</a>, for Fabric on Minecraft 26.2.</p>

<p align="center"><img src="docs/menu.png" alt="The Leone Client menu" /></p>

Press **Right Shift** in game (or type `/leone`) to open the menu. Each segment of the wheel is a category of modules, and the dock along the bottom holds search, LeoneMC's servers, your LeoneMC friends, player profiles, config profiles and the HUD.

Every module starts switched off, so you only get what you turn on.

## Modules

| Category | Module | What it does |
|---|---|---|
| Combat | Combat Timer | Shows your combat tag countdown as a status effect, next to your other effects. Can chime when the tag runs out. |
| | Combat Bar | Takes the combat tag line out of the action bar and puts it on its own bar, so the two stop replacing each other. It can also be merged onto one line. |
| | Combat Log Guard | Asks for confirmation before you disconnect from the pause menu while combat tagged. |
| | Session Stats | Counts your kills, deaths, KDR, kill streak and best streak from the death messages, on a HUD panel. Starts again on login, on each server switch, or never. |
| Chat | Anti-Mute | Replaces words that LeoneMC's filter mutes for before your message is sent, in chat and in private messages. Groups: discrimination, death wishes, swears and advertisements. |
| | Alert Filter | Hides the kinds of `[Alert]` broadcast you choose: staff recruitment (hidden by default), purchases, events, streams, tips and others. Proxy restart warnings are never hidden. |
| | Chat Cleaner | Hides the broadcasts you choose: other players' deaths and killstreaks, crate openings, coinflips, votes, daily rewards, envoy pickups, lobby joins and the rotating announcement boxes. Lines that name you or a friend always show, and so do your own crate rewards. A line repeated straight after itself stacks into one line with a count, such as `[x3]`. |
| | Mentions | Plays a ping and highlights your name when a player mentions you in chat or staff chat. Join messages, kill messages and staff notices that contain your name do not count, and neither do your own messages. Extra keywords can be added. |
| | Chat Tabs | Tabs along the top of the open chat that each show one kind of message: Chat (players talking), Private (private messages), Mentions, Friends, Server (broadcasts, events, kills and command replies), a Custom tab for your own words, and for staff, Staff (staff chat) and Alerts (anticheat, reports, requests, punishments and staff tools). Every message is still kept and All shows everything. Quiet tabs count what you missed, the reply to a command you just sent shows in the tab you are on, Leone Client's own notices show in every tab, and chat goes back to All when you close it unless you choose to keep the tab. Click a tab, scroll over the bar, or use Ctrl+Tab. A note above the input says where typing goes on tabs like Staff and Private, which only filter what you read. |
| Friends | Friend Highlight | Makes your LeoneMC friends' names stand out in chat, with their status on hover. |
| | Friend Activity | Turns `Friends \| X has joined the server Y.` into a short clickable line, a pop-up, or nothing, and can limit it to your own server. |
| | Player Lookup | Give it a key, then press it while looking at a player to open their LeoneMC profile. `/leone profile` with no name does the same. |
| HUD | Action Bar | Draws the action bar wherever you place it in Modify HUD, with optional backdrop, shadow and fade. |
| | Timers | One HUD panel for what is coming: events (such as Lava Rising) and proxy restarts from `[Alert]` messages, server reboots, the server's own sidebar countdowns (KOTH, Key All, Map Reset), envoy events, supply drops (ElytraBox and InsaneKits), and the current Target bounty. Warns you a minute before, and works even when Alert Filter or Chat Cleaner hides the messages. |
| | Pack Warnings | Hides LeoneMC's resource pack titles and reminders. |
| Server | Auto Join | Sends you to ElytraBox, WildKits, CoreRaiding, InsaneKits, Lifesteal, Gens, Survival or any server you type when you log in. |
| | Auto Reconnect | After a restart or a dropped connection, the disconnect screen counts down and puts you back on LeoneMC, then on the server you were on. It never reconnects after a kick, a ban or a login from elsewhere, gives up after five tries in a row, and one click cancels it. |
| | Ender Chest Pages | Makes `/ec (number)` and `/enderchest (number)` run `/pv (number)`. While it is off, those commands go to the server as normal. |
| Client | Interface | Accent colour (Leone Pink, Leone Blue, Purple, Royal Blue or Emerald), font, sounds and blur. |
| | All Servers | Runs the LeoneMC-only modules everywhere, including singleplayer, which is handy for trying them out. |
| | Log Filter | Keeps the thousands of harmless warnings LeoneMC causes out of your game log. |

Left-click a card to switch a module on or off, and right-click it (or use the gear) for its settings. Every setting explains itself when you hover it. Any module can be given a key that switches it on or off while you play: open its settings, click **Set a key** and press the key (Escape cancels, and the cross clears it). If the key is also one of Minecraft's controls or another module's key, the settings say so.

<p align="center"><img src="docs/settings.png" alt="A module's settings" /></p>

### Staff

The Staff category only appears when your [leonemc.net](https://leonemc.net) profile shows a LeoneMC staff rank (Trainee, Helper, Junior Mod, Mod, Senior Mod, Admin, Developer, Builder, Manager, Executive or Owner). It is checked when the game starts and when you join LeoneMC.

| Module | What it does |
|---|---|
| Staff Chat | Keeps staff chat (staff messages, reports, help requests, anticheat alerts, banned join attempts and punishments) in your chat on your screen, but leaves those lines blank in OBS, Medal, Discord and other screen capture, by drawing them in a window that Windows excludes from capture. It can show them normally while you are vanished or in mod mode, when its key switches between showing and hiding them. Windows only. If the separate Staff Chat Overlay mod is installed, remove it to use this module. |
| Anticheat Alerts | Folds repeats of the same player and check into one line that moves down with a count, such as `[x3]`, so you can see an alert is still coming in without it flooding chat (or hides repeats outright, if you prefer). Lists recently flagged players on a HUD panel with their checks and server, and can play a sound the first time a player is flagged. |
| Reports | Pops up player reports and help requests as notifications, and optionally VPN warnings and banned players trying to join. |
| Mod Mode Status | A small HUD panel showing mod mode, vanish, whether staff chat is hidden, and how many punishments you have given this session. Each part can be turned off, and the panel can show only while you are in mod mode. |

Under the Staff modules, the Staff page lists who was flagged recently (with their checks) and the latest reports and help requests, each with a button for the player's profile and one that teleports to them, or joins their server first when they are on another one. While staff chat is hidden from capture, the names there are hidden until you click **Show anyway**, and the Anticheat panel and staff pop-ups are hidden too.

While LeoneMC's staff chat toggle is on, the open chat always shows **Typing goes to staff chat** above the input, so nothing meant for one chat lands in the other.

Mod mode is read from LeoneMC's own messages whenever you switch it. Joining a server with "Enable Mod Mode on Join" is silent, so Leone Client learns the items mod mode puts in your hotbar the first time you switch mod mode on and off, and recognises them when you join later. Until then, mod mode after a join shows as unknown, and while it is unknown only vanish can make staff chat visible. Vanish is read from the "You are currently Vanished" action bar and ends as soon as that stops repeating. Only messages from the server count, never player chat. `/leone staff` shows what Leone Client currently knows.

### LeoneMC's servers

The wheel has one more segment for the LeoneMC server you are on: ElytraBox, WildKits, CoreRaiding, InsaneKits, Lifesteal, Gens, Survival, MoneyDupe, KnockbackFFA, Practice, Events or Hub, or the server's own name for one Leone Client does not know yet. It holds the modules made for that server. They only run on that server: switch server and they stop and disappear, then come back as you left them when you return. A server with nothing made for it yet says so. To set them up away from LeoneMC, turn on **All Servers** and choose a server under **Act as if on**.

| Server | Module | What it does |
|---|---|---|
| ElytraBox | Item Cooldowns | Times the Cage, the Cobweb Circle and Knockback weapons, whose cooldowns the game does not show. Each use starts a timer unless the server refuses it, and the server's own "You cannot use cage item for another 12 seconds!" sets the timer to exactly that. A HUD panel lists what is cooling down, the item in your hotbar and inventory counts down like a vanilla cooldown, and a ping or pop-up says when each is ready. Timers last through relogging, and the server's cooldown messages can be hidden. |

### Built into chat

With chat open, hold **Ctrl** over a message and press **C** to copy it, or hold **Alt** and click a message to open the LeoneMC profile of the player it is from or about. Holding the key highlights the message and says what will happen; without it, chat works exactly as normal.

## The dock

- **Search** finds modules by name, category or description. Typing anywhere in the menu starts a search.
- **Servers** switches LeoneMC server in one click and shows which of your friends were last seen on each.
- **Friends** shows your friends list from your profile on [leonemc.net](https://leonemc.net): online friends first, the server each online friend is on, and when offline friends were last seen. Click a friend for their profile, message online friends, send friend requests, or view another player's friends with Change account.
- **Players** looks up any LeoneMC player by name: rank, where they are or when they were last seen, join date, playtime and friends, with their statistics in a tab for each game mode, each stat showing their place on its leaderboard. `/leone profile <name>` opens it directly.
- **Configs** saves your setup as named profiles. The active profile saves automatically.
- **Overlays** turns HUD elements on and off: Watermark, Module List, Action Bar, Combat Bar, Notifications, Server, Timers, Session Stats, FPS, Ping, Coordinates, Speed, CPS and Keystrokes, plus Staff Status and Anticheat Panel for staff. Right-click one (or use its gear) for its settings: backgrounds, units, what it shows, colour by value, how many rows a panel lists, and a reset for its position and size.
- **Modify HUD** lets you drag overlays anywhere and resize them from 50% to 250%, by scrolling over one or dragging the bracket on its bottom-right corner. They snap to the edges and centre lines, and right-clicking one hides it. Panels that grow (such as Timers, Session Stats and the Anticheat panel) move aside for each other instead of overlapping when they share a corner.

<p align="center"><img src="docs/servers.png" alt="The Servers page" /></p>
<p align="center"><img src="docs/hud.png" alt="The HUD editor" /></p>

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2, with [Fabric API](https://modrinth.com/mod/fabric-api).
2. Put `leoneclient-<version>.jar` in your `mods` folder.
3. [Mod Menu](https://modrinth.com/mod/modmenu) is optional; with it, the config button opens the Leone Client menu.

Settings are kept in `config/leoneclient/`. Settings from the previous version of Leone Client (`config/leoneclient.json`) are imported the first time this version runs.

## Building

Requires JDK 25.

```bash
./gradlew build
```

The jar is written to `build/libs/`. `gradle.properties` points Gradle at the Windows certificate store so downloads work behind antivirus HTTPS inspection; remove those two options on macOS or Linux.

`./gradlew runClient` starts the game and joins the singleplayer world "Leone Test". `./gradlew runAutotest` uses its own `run-autotest/` folder: it creates that world, runs every feature with all modules off and then on, walks through the menu, saves screenshots to `run-autotest/leone-shots/` and then quits.

## Notes

- LeoneMC is recognised however you join it. An address with "leonemc" in it (play.leonemc.net, play.leonemc.gg, eu.leonemc.net and so on) counts at once. Any other address is watched for LeoneMC's own signs: its name in the sidebar or the tab list, or one of its announcement boxes. Once seen, that address is remembered (in `config/leoneclient/network.json`) and counts at once next time. Player chat never counts, so another server cannot be mistaken for LeoneMC because someone mentions it.
- The menu uses Segoe UI on Windows and falls back to the Minecraft font elsewhere (or when Interface > Font is set to Minecraft).
- Friends, profiles and player search come from leonemc.net, heads from mc-heads.net. Reading a profile page does not add to its view count. The friends list is refreshed at most every 90 seconds and cached on disk; each friend's own profile is read for their server or last-seen time at most once a minute while they are online and every five minutes while they are offline.
- leonemc.net can list a player under a different name from their Minecraft one. Player lookup falls back to the players on your server and Mojang's public name lookup, and the Message buttons use the player's Minecraft name.
- Server transfers are always handled on the game's main thread. This stops a crash with OpSec Enhanced's "Confirm transfer" prompt when `/hub` moves you to a hub on another proxy.
- Icons are from [Lucide](https://lucide.dev) (ISC licence).

Copyright (c) 2025-2026 Alfxyz. All rights reserved.
