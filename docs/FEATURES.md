# Kairo: full feature guide

Everything Kairo and Kairo Void can do, in detail. Back to the [README](../README.md).

A visuals-focused client for **Minecraft Java 26.3** (Fabric): animated glass menus, HUD mods and PvP visual tweaks.
Visual and client-side only. No cheats, no automation, nothing extra sent to servers.

By Mordsome.

Features: its own main menu (with Singleplayer and Multiplayer screens) and a matching restyle of Minecraft's
other menus, a Right Shift ring menu, glass panels for mods and settings, a HUD with a drag-and-drop layout editor,
25 mods, a PvP mode that layers its own settings on top of yours, a totem changer, custom sounds, cosmetics and emotes.

## Kairo Void

A second jar built from the same code: **Kairo Void**, the dark, PvP-focused edition. The menus are flat dark grey
with one accent colour you pick (violet by default) and no glass; it has the same cosmetics and emotes as Kairo.
What it adds is in the game itself, every part of it switchable in Visuals:

- **Visuals panel:** every visual is a same-size tile with a picture, four to a row, in Presets, Combat, Crystals, Hand,
  Screen, World & sky, Atmospheres and Interface (All shows them under headings), with a search, an on/off switch for
  everything and Low / Medium / High / Insane quality presets at the top. Click a tile to switch it; the gear opens
  its options.
- **Combat:** your weapon glows with energy when you swing and leaves a short streak of light (brighter on critical
  hits); a ripple bends the picture where your hit lands; what you hit flashes white; the picture holds for a split
  second on strong hits (only the picture, the game keeps running); hit colour, extra crit sparks; golden rays and a
  slow ripple when a totem saves you.
- **Atmospheres:** Blizzard (snow settles on the tops of blocks), Galaxy, Blood moon, Inferno, Sakura dream, Toxic,
  Abyss (light rippling over the ground), Synthwave and Storm (rain and lightning). Each changes the sky, fog and
  colours and brings its own weather particles. Only on your screen.
- **Interface:** HUD mods glow (Glow, Neon or Pulse, where a light runs round each box), keystrokes pop and ripple,
  menus have drifting light behind them, and the Right Shift ring is just the ring, breathing, with lights orbiting it.
- **Sky:** at night a nebula band, a turning spiral galaxy, a ringed planet, twinkling stars and shooting stars, aurora
  curtains in the north; deeper sunrises and sunsets; distant land fading into a sky-coloured haze.
- **Presets:** a whole look in one click (accent, atmosphere, HUD style, how round corners are, crystal style):
  Void, Frostbite, Hellfire, Nebula, Sakura, Retrowave, Abyssal, Venom, Bloodline, Thunder and Clean.
- **Crystals:** end crystals as Prism (glowing glass with orbiting rings), Void core, Plasma or Hologram, and a pop of
  light, a ring and a ripple when one explodes.
- **Visuals on / off:** the Effects segment on the ring, the J key or the switch at the top of Visuals turns every
  visual off for plain Minecraft (and back on), keeping all your choices.
- **Main menu:** three styles. Stage (your player in a spotlight with dust in the beam, a glossy floor that reflects
  them, a slow camera drift and an emote now and then), Simple (just the name and a column of words), and Minecraft's
  own title. Behind every menu without a world, a drawn space scene replaces the panorama: nebulae, stars, a galaxy,
  a ringed planet and drifting crystal shards.
- **PvP mode** works the same as in Kairo.

Void keeps its own settings (`config/kairo-void`). Both jars use the mod id `kairo`, so install one or the other.

Mod colours that use the accent (HUD values, hotbar selection, cape) follow it until you pick your own; Visuals →
Theme → **Accent on every mod** makes every mod colour use it, and each mod's own **Follow theme colours** does the
same for just that mod (both editions). See [Theme and wallpaper colours](#theme-and-wallpaper-colours).

## Photosensitivity warning

Both editions have flashing lights, bright bursts of colour, screen shakes and fast-moving effects (Kairo Void has
far more of them). A very small number of people can have a seizure when they see flashing lights or patterns, even
with no history of epilepsy. If you or anyone in your family has had seizures or epilepsy, talk to a doctor before
playing. Stop playing right away if you feel dizzy or sick, or notice blurred vision, eye or muscle twitches, or
confusion.

The first-run setup shows this warning and lets you pick **Fewer flashes**, which tones down every flash: explosion
and hit flashes, lightning (storms and the lightning kill effect), the totem blast, the hologram crystal's flicker and
the low-health beat. You can change it any time (Visuals → Motion, or Visuals → Screen in Kairo Void), together
with **Reduce motion**.

## First-run setup

The first time the game starts, a black welcome screen walks you through nine quick steps before the main menu.

- **Kairo:** welcome, the photosensitivity warning (Full effects or Fewer flashes, plus Reduce motion), your accent
  colour (12 swatches, applied live, plus your wallpaper's colours when a custom background is set), your main menu
  style (Kairo, Dock, Orbit, Custom or Minecraft), a few comfort switches, a HUD preset (Clean, PvP, Everything or
  Nothing), a cape and wings, whether to check for updates (off unless you choose it), and the keys worth remembering.
- **Kairo Void:** a welcome that sets Void next to plain Kairo (menus, combat, sky, world, crystals, looks) over a
  changing atmosphere, the same warning, a whole look (eight presets with pictures), how much effect you want (Low,
  Medium, High or Insane, each with a picture of what it adds), the menu style (Stage, Simple or Minecraft), a HUD
  preset, a cape and wings, whether to check for updates, and the keys (including J for all visuals off and on).

Arrow keys / Enter move through it, Esc or "Skip setup" leaves it. Run it again from Settings → About.

## Visuals: in game

Right Shift → Visuals now has two groups: **In game** (below) and **Client** (theme, glass, motion, menus, buttons).
Everything here only changes what your own screen draws.

- **Animations:** 10 attack animations (Vanilla, Smooth, Slash, Chop, Stab, Spin, Flip, Swirl, Punch, Sweep) and 7
  mining animations (Vanilla, Sideways, Smooth, Hammer, Drill, Circle, Saw), picked from tiles that show each motion
  path on hover. "Attack animation length" slows the swing down (up to 3x) and "Mining swing length" sets seconds per
  mining swing; both are looks only: hits, cooldowns and mining speed don't change, and nothing extra is sent. A
  glowing **swing trail** (accent colour or rainbow) can follow the blade.
- **Screen effects:** 11 colour filters (Cinematic, Warm, Cold, Vibrant, Dreamy, Noir, Retro, Neon, Vaporwave, Sunset,
  Matrix) with a strength slider, plus saturation, contrast, glow, vignette (black or your colour), chromatic
  aberration, film grain, cinema bars, motion blur and a red low-health pulse. They apply to the world and your hand,
  not to the HUD or menus.
- **Hit effects:** particles on what you hit (Sparks, Crits, Magic, Hearts, Stars, Flames, Soul fire, Blood) and a
  kill effect where it dies within 5 seconds of your last hit (Lightning, Firework, Soul rising, Poof, Totem burst,
  Hearts, Blood splash, Vortex, Shatter, Void implosion, Ascend), with an optional sound. Purely client-side particles and sounds.

## Cosmetics and emotes

Right Shift → Cosmetics (the sixth ring segment) opens the wardrobe: a rotating preview of your player (drag to turn) and
a tile grid per category. Cosmetics are drawn only on your own player, on your screen.

- **Capes:** 16 built-in designs (Kairo, Gradient, Sunset, Ocean, Galaxy, Aurora, Flames, Frost, Rainbow, Stripes,
  Checker, Carbon, Neon grid, Cherry blossom, Lava, Midnight), two recolourable colours, or any image of your own.
  Ocean, Aurora, Flames, Lava, Galaxy, Rainbow and Neon grid are animated (and move in the wardrobe tiles too).
- **Wings:** Angel, Dragon, Bat, Seraph (three pairs), Energy (glowing line art with a travelling pulse), Demon,
  Phoenix, Crystal and Skeletal (bones with a ghostly membrane), with two colours, size, flap speed, rainbow and glow.
  They flap faster while you move.
- **Hats:** Crown, Halo, Top hat, Party hat, Witch hat, Cat ears, Bunny ears, Horns, Headphones, Sunglasses, Cowboy hat, Beanie.
  Every skin is different, so hats have fit sliders: height, forward/back, size and tilt. Cat ears also have spacing,
  angle and size, and come alive (optional): a slow sway, a quick flick every few seconds, lying back while you run.
- **Bandanas:** Headband, Ninja band (with tails), Pirate bandana, Face bandana, Scarf, Neckerchief; two colours and a
  solid / stripes / dots pattern.
- **Trails:** Hearts, Sparkles, Flames, Soul flames, Notes, Snow, Totem, Enchant, Clouds, Cherry petals, Rainbow dust,
  Electric; amount and "only while moving".
- **Emotes:** 37 emotes in four pages, plus your favourites. Hold **B**, point at an emote and let go to play it (or
  tap B and click one); scroll, Tab or the tabs above the wheel change the page, and right-click adds an emote to your
  favourites (up to 12, the wheel's first page). The wardrobe's Emotes tab lists them by page; right-click a tile to
  favourite it there too.
  - Dances: Dance, Zombie, Floss, Disco, Robot, Headbang, Shuffle, Wiggle.
  - Reactions: Wave, Clap, Cheer, Salute, Bow, Facepalm, Shrug, Laugh, Cry, Point, Think, Angry, Excited.
  - Poses: Sit, Lie down, Dab, T-pose, Meditate (floating), Hero pose, Kneel, Flex, Victory, Lounge.
  - Moves: Backflip, Spin, Front flip, Cartwheel, Jump for joy, Levitate.

  A small burst of light in your colour marks the start (Emote sparkle, can be turned off). The camera swings to the
  front (or back, or stays, in the options) and moving, jumping, attacking or taking damage ends the emote. Emotes are
  only visible to you.

## Visuals: more in game

- **Held item look** (Visuals → Animations): Shimmer (an enchantment sheen on whatever you hold), Outline (a
  crisp rim around the item's shape in the shimmer colour) or both, and the **shimmer colour**: vanilla, your colour, rainbow or a custom colour. The colour also applies
  to enchanted items everywhere.
- **Inspect** (V, change it in Visuals → Animations): show off the item in your hand like in shooters: Look over,
  Twirl, Toss & catch or Flourish. Attacking, using, mining or switching items stops it.
- **Movement & world:** jump effects (Dust puff, Shockwave ring, Sparkles, Flames, Hearts, Cloud, Soul), a bigger one
  when you land from a height, speed lines at the screen edges while sprinting, flying or falling fast, and explosion
  styles (Shockwave, Inferno, Firework, Magic, Void) with optional camera shake (your view only, not your aim) and a
  white flash.
- **Hit effects:** a hit flash in your colour at the screen edges, and floating damage numbers above what you hit.
- **Kill effects** also include Shatter (it breaks into cubes in your colour), Void implosion (everything is pulled
  into one point, then bursts out; in Kairo Void with a ripple) and Ascend (a column of light rises).
- **Sound packs** (Visuals → Hit effects, or Hit Sounds in Mods): Arcade, Crystal, Heavy, Void or Bubble, a matching
  sound for your hits, your critical hits and your kills. All generated by the client.
- **Projectile trails:** arrows, tridents, ender pearls, snowballs, eggs and potions leave a trail (your colour,
  rainbow, flame, magic, cloud or sparkle), for everyone's projectiles or only yours, and a ring of light where an
  ender pearl lands.
- **Item physics:** dropped items lie flat on the ground (blocks sit on it), each turned its own way, and tumble while
  they fall. Optional soft glow under rare items in their rarity colour. Only the drawing changes.
- **Bloom:** the bright parts of the picture (sky, fire, lava, lamps, glowing effects) are blurred wide and added back,
  so light bleeds softly. **Motion blur** can follow how fast you turn ("Blur only when turning", on by default), so
  standing still stays sharp.
- **Share look** (Visuals → Share look, or the Presets group in Kairo Void): **Copy my look** puts one code with your
  colours, buttons and every visual on the clipboard; **Paste a look** wears a code a friend sent. Keybinds, HUD
  positions and anything personal are never in it.
- **Living ring and living menus** (both editions, Visuals → Ring and Menus): the ring breathes, lights orbit it and
  the centre pulses; drifting light moves behind the menus. **Panel edge light** (Visuals → Menus, or Interface in
  Kairo Void) is the short light running along the top edge of big panels, with its own switch.

## Colours

Every colour setting has three modes in its picker: **Static**, **Chroma** (a rainbow at the saturation and brightness
of your colour) and **Gradient** (flows through 2 to 5 colours you pick), with a speed slider. Your accent colour can
move too, and everything that uses it follows.

## Theme and wallpaper colours

Like Material You on Android, the client can take its colours from your main menu background.

- **Swatches:** with a custom image, GIF (its first frame) or panorama as the background, Kairo reads 4 to 6 colours
  from it (the picture is shrunk, its colours grouped, and each group scored by how much of the picture it covers and
  how colourful it is; near-greys only count when the whole picture is grey). Minecraft's panorama and Kairo Void's
  space scene have built-in palettes. They show in Visuals → Theme (Kairo Void: Interface → Wallpaper colours), in
  the background picker of the main menu settings, and in the first-run colour step. Click one to make it your theme
  colour. Colours are read once per picture in the background, never every frame.
- **Use wallpaper colours:** the theme colour follows your background: change the wallpaper and the client changes
  with it (to the same swatch position you picked). A colour you set by hand stays until the background changes.
- **What the theme colours:** the accent (buttons, toggles, the ring highlight, HUD values that follow the accent);
  with **Tint backgrounds** the glass panels (Kairo) or flat panels (Kairo Void), popups, toasts, the restyled
  Minecraft menus and HUD boxes take a dark tone of it; with **Tint text** main text takes a very light tint and
  secondary text a softer one. Each tint has a strength slider. Kairo Void keeps its flat style; only the colours change.
- **Per mod:** every mod with colours has **Follow theme colours** (its colours use the theme colour, even ones you
  picked) and every mod with a background has **Tinted background** (off: the neutral grey background).
- **Readable:** text is kept at WCAG 4.5:1 contrast or better against its panel; if a tint would lower it, the text
  is made lighter automatically. Swatches are adjusted to a lightness that reads well on dark panels.
- **Look codes** carry your colours and tint settings, never the wallpaper itself. Wearing a friend's look switches
  off "Use wallpaper colours" so their colours stay.
- Profiles from 0.1.0 keep exactly the look they had: the tints start off there (one switch turns them on).

## Ring menu

Visuals → Ring picks what the Right Shift ring shows: 3 to 8 of Mods, Settings, PvP Mode, Visuals, Layout,
Cosmetics, Waypoints, Emotes, PvP options and Screenshots, with a live preview. Number keys follow the order.

## Loading screen

Minecraft's red Mojang loading screen is replaced by a deep navy one with the Kairo wordmark and a slim accent progress line (start-up and every resource reload).

## Main menu

Kairo replaces the title screen (Visuals → Menus → "Client main menu" turns it off). Styles (gear on the main
menu, or the welcome setup): Kairo, Dock, Orbit, **Custom** and Minecraft. The settings only show what the chosen style
uses (button order and cards are Kairo-only). **Custom** is your own home screen made of widgets on a 12x8 grid: clock,
client name, buttons (any menu entry, as a bar or a square), recent worlds, servers you pick, one big server, your skin in
3D with your cosmetics, a PvP switch, stats and your own text. Press **Edit** (top left): drag widgets to move them, drag
the corner to resize, x to remove, the gear for options, **Add widget** for more.

- The vanilla panorama behind glass rows for Singleplayer, Multiplayer, the client menu, Options and Quit
  (keys: ↑/↓ + Enter, or 1–5), a PvP mode pill, and two cards: **Jump back in** (your last world) and
  **Quick join** (your first server, pinged live).
- **Singleplayer**: world cards with icons, mode and last played; search (just start typing), Play, Edit, Delete
  (with a confirmation) and Create.
- **Multiplayer** (view as List, Compact or Tiles; the eye hides server addresses, handy when streaming): your servers with their icon, coloured MOTD, signal bars and player count; Join, Edit, Delete,
  Add server, Direct connect and Refresh (F5).
- Every other Minecraft menu (options, world creation, pause menu...) gets rounded dark-glass buttons, text fields,
  sliders, checkboxes and scrollbars (Visuals → Menus → "Restyle other menus").
- Soft menu sounds, generated by the client (Visuals → Menus → "Menu sounds", 0% = off).
- **Buttons** (Visuals → Buttons, with a live preview you can click): corner radius (0 = square), animated buttons
  (they fade and slide in when a menu opens, and the hover highlight fades in and out), hover glow, outline, button
  colour, and a click effect: Ripple, Pop, Shine, Rainbow, Sparks, Shockwave or Confetti (the last three fly over
  the screen, so they still play when the click opens another menu).
- **Main menu settings** (gear at the bottom right of the main menu, or "Kairo menu" on Minecraft's title screen):
  - Style: Kairo (rows and cards), Dock (a big clock and the entries in a magnifying dock), Orbit (the entries are glass planets circling the Kairo mark on a tilted ring over a starfield: click the front one, click another to swing it round, drag / scroll / ←→ to spin), or Minecraft's own title screen in the Kairo look.
  - Buttons: drag to reorder, eye to hide (at least one stays). Toggle the "Jump back in" / "Quick join" cards, and hide the friends / language / accessibility icons on Minecraft's title screen (Options and Quit move up).
  - Background for every menu without a world: Minecraft's panorama, any PNG/JPEG/BMP image, an animated GIF, or your
    own panorama (a folder with panorama_0 … panorama_5). **+** adds one through the system file/folder dialog;
    files live in `config/kairo/backgrounds`. Darken, Blur and Slow pan (images drift and zoom slowly).
    A custom background starts loading at launch and fades in from black; Minecraft's panorama never flashes first.
- **Hide extra pause buttons** (Visuals → Menus): removes the small icon row in the pause menu; the top buttons
  move down so Options and Save and Quit stay exactly where they were.

## Your own totem and sounds

- **Totem Changer**: pick a recolour (Frost, Ember, Amethyst, Emerald, Rose, Shadow) or press **+** to choose any
  PNG, JPEG, BMP or GIF. It's cropped, made square and resized; "Remove image background" clears a plain
  background (handy for photos). Shows in your hand, hotbar, inventory and the pop animation. Images live in
  `config/kairo/totems`.
- **Pop sound** (Totem Changer) and **Hurt / Hit sound** (Hit Sounds): Vanilla, a built-in sound (Beep, Double beep,
  Ding, Chime, Bell, Click, Pop, Thud, Blip, Zap, Coin, Crunch) or your own file (**+**, OGG or WAV; MP3 isn't
  supported). ▶ previews it. Files live in `config/kairo/sounds`. Hurt sounds can apply to everyone, only you,
  or only other players.

## PvP mode

Toggle it from the ring (or bind a key in Settings → Keybinds). While it's on, PvP overrides apply on top of your
normal settings; turning it off brings every normal value back exactly as it was.

- Defaults: no hurt tilt, smaller and shorter totem pops with fewer particles, a lower see-through fire overlay,
  Armor Status always visible with the totem count highlighted, and calmer explosions for crystal PvP: no big
  white explosion flash, 10% of the debris and smoke, and at most 3 explosions per second show particles.
- **PvP options**: right-click PvP Mode in the ring. One page for the things that matter in a fight: no view
  bobbing, hurt camera tilt, explosion particles / flash / per-second limit, fire overlay, totem pop size,
  smaller held items (Item Scale), hide all waypoints, Fullbright, and a shortcut to the PvP HUD layout. These
  only apply while PvP mode is on; "Reset section" goes back to your normal values.
- Every mod's settings page has an "In PvP mode" section; the preview there can show normal or PvP values.
- While PvP mode is on, quick controls (mod card switches, toggle keys) only change PvP mode.
- The layout editor has a Normal / PvP switch: in PvP each HUD element can have its own position, size and
  visibility. Elements glide between the two layouts when the mode flips.

## Mods

The switcher next to the search box in the Mods panel shows mods as Cards, Large tiles, a List or Tiny tiles.

| Mod | What it does |
|---|---|
| Keystrokes, CPS, FPS, Ping, Coordinates, Armor Status, Direction HUD | HUD elements, placed with the layout editor (ring → Layout). CPS has an editable **Label** (`CPS` by default; empty shows just `8 | 6`). Keystrokes → **Show CPS in Keystrokes**: the mouse buttons show Label, CPS (your live clicks per second for each button) or Both (a small label above the number); CPS then hides its own box unless you turn off "Hide when Keystrokes shows CPS" (the layout editor marks it "shown in Keystrokes", and the PvP layout can choose differently): drag to move, drag the corner grip (or scroll) to resize, × on the corner hides an element and + brings it back, right-click for its settings. Shift/Ctrl-click or drag a box over empty space to select several (Ctrl+A selects all): they move, resize, hide and reset together. Ctrl+Z undoes and Ctrl+Y (or Ctrl+Shift+Z) redoes, also with the arrow buttons in the toolbar. With CPS or Keystrokes selected, the toolbar has Attach / Detach CPS. Direction HUD is a compass bar (N, NE, E… with degree ticks, your heading and your waypoints on it) or just the direction you face |
| Zoom | Hold **C**; scroll to zoom further; smooth, optional hand hiding and cinematic camera |
| Fullbright | Raises the gamma value only (no Night Vision, no lighting changes); optional toggle key |
| Crosshair | Cross / dot / circle / T / custom pixel grid, outline, attack-cooldown style |
| Inventory | Inventories and containers in the glass look: Minecraft's container textures are recoloured (panel see-through, slots dark glass) over a frosted panel with no square bevels (so any corner radius works), titles turn light, the creative menu gets glass tabs, and every slot (inventory, armour, crafting, results) is a rounded glass cell drawn by the client. Buttons inside inventories (recipe book) don't play the menu intro animation. Arrows, flames and icons stay. Tint, frost, corners and slot darkness are adjustable; switching it on or off reloads resources once |
| Block Outline | Accent (or own) colour, thickness, rainbow, and a see-through fill that can pulse |
| Saturation | Shows hidden food values: a gold outline on the hunger icons covered by saturation (partial for partial points); while you hold food, the hunger and saturation it gives and the health you'd regenerate flash on the bars; food tooltips show hunger and saturation. Also Glow and Underline styles, a strip under its hunger bar, optionally the number |
| 3D Skins | Hat, jacket, sleeves and pants built from small cubes so skins have real depth (players, your own in F5 and in the inventory). Pieces under armour stay flat. Thickness and a render distance |
| Hotbar | The hotbar as a glass panel or separate slots with a sliding accent selection and optional slot numbers; health as bars or squares (absorption in gold, poison/wither/frozen colours, a fading trail for health you just lost, low-health pulse), the same for armor, hunger (with saturation) and air; a slim XP line with a level pill or no XP bar. Every part can stay Minecraft's own, and animations can be switched off |
| Item Scale | Smaller (or bigger) items in your main hand and offhand, separately, plus sideways / height / depth offsets, so held items cover less of the screen. First person only |
| Waypoints | On-screen markers with beams, edge clamping and a death point; saved per world/server. **M** (rebindable in Settings → Keybinds) or clicking the Waypoints card opens the waypoints screen: every dimension with filters and counts, sort by nearest / name / newest / oldest, search, coordinates with the matching Nether/Overworld position, distance and a direction arrow, copy coordinates (C), edit name / X Y Z / colour, use my position, hide, delete |
| Chat | Timestamps, stacked repeats `(x3)`, longer history, slide-in, search bar (only a click focuses it, so ↑ still brings back your last command), right-click to copy |
| Scrollable Tooltips | Mouse wheel scrolls tooltips taller than the screen (Shift for sideways) |
| Shulker Preview | Contents grid in the tooltip, and above the hotbar while holding Shift |
| Totem Pops | Smaller, shorter pop animation and fewer particles |
| Hurt Camera | Damage tilt, explosion particles / flash / per-second limit, lower and see-through fire overlay |
| Hit Sounds | Replace the hurt and hit sounds with a built-in sound or your own file, or pick a sound pack (hits, crits and kills) |
| Totem Changer | Any image (or a recolour) as the totem, and your own pop sound |
| Screenshots | After F2: a preview card and [Copy] [Open] [Folder] [Delete] on the chat message (Copy puts the image on the clipboard, Delete uses the Recycle Bin) |

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for 26.3.
2. Put these in your `mods/` folder:
   - [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3
   - [Sodium](https://modrinth.com/mod/sodium) 0.9.x for 26.3 (required, installed separately)
   - `kairo-<version>.jar` **or** `kairo-void-<version>.jar` from the [Releases](../../../releases) page (or
     `build/libs/` if you build it yourself). Not both.
3. Launch, join a world, press **Right Shift**.

## Build

Needs nothing but a JDK to start Gradle (17+). The wrapper downloads Gradle 9.8.2 and a Java 25 toolchain
automatically (Loom 1.19 requires Gradle itself to run on Java 25; see `gradle/gradle-daemon-jvm.properties`).

```bash
./gradlew build          # both jars in build/libs/ (kairo and kairo-void)
./gradlew runClient      # dev client (with Sodium) in ./run
./gradlew runVoidClient  # the same, as Kairo Void
./gradlew runClientGameTest   # automated visual tour, screenshots in build/run/clientGameTest/screenshots
./gradlew runClientGameTest -Pvoid   # the Kairo Void tour
```

On Windows use `gradlew.bat` the same way.

## Project layout

```
src/main/java/dev/mordo/kairo/
  core/      ClientInfo (name/id), Edition (Kairo or Void), modules, settings, PvP mode, config, keybinds
  render/    Animation/Easing, SDF Shapes, Blur (dual Kawase), font renderer, pipelines
  gui/       ring menu, panels and widgets, layout editor, icons, toasts
  gui/main/  main menu, worlds and servers screens
  sound/     built-in synthesized sounds, sound files, sound replacement
  hud/       HUD system and HUD mods
  mods/      the other mods (zoom, waypoints, chat, ...)
  mixin/     small hooks (one per concern)
src/main/resources/assets/kairo/
  shaders/   shape (SDF + glass), text, Kawase blur passes, screen effects, Void combat and sky passes
src/void/resources/   the marker that makes the second jar Kairo Void
  font/      Outfit, JetBrains Mono (SIL OFL, licenses in /licenses)
src/gametest/   dev-only visual tour (not in the mod jar)
```

Renaming the client: change `ClientInfo.NAME` (display name). The mod id lives in `ClientInfo.MOD_ID`,
`fabric.mod.json` and the `assets/kairo` folder.

## Fonts

Outfit and JetBrains Mono are licensed under the SIL Open Font License 1.1; see `src/main/resources/licenses/`.

## Privacy

Kairo has no accounts, no telemetry and no analytics. Everything it saves (settings, waypoints, your images and
sounds) stays in your `config/kairo` (or `config/kairo-void`) folder. Cosmetics and emotes are drawn only on your own
screen; nothing is sent to servers or other players. Your wallpaper's colours are worked out on your computer; the
picture never leaves it.

It only goes online if you switch on **Check for updates on GitHub** (off by default; in the first-run setup or
Settings → About). Then, once per launch, it asks GitHub's public API for the latest release of `Mordsome/Kairo`.
That request sends nothing about you or your game (just a User-Agent naming the updater, which GitHub requires), and
any failure is silent. If a newer version exists, the main menu shows a short notice and Settings → About shows the
version, its release notes and two buttons:

- **Update** downloads only your edition's jar (`kairo-X.jar` or `kairo-void-X.jar`) from that release over HTTPS,
  checks it against the release's `SHA256SUMS.txt` (a mismatch throws it away), and keeps it in `config/kairo/update`.
  When the game closes, a small helper waits for Minecraft to exit, removes the old Kairo jar from `mods` and moves the
  new one in ("Restart Minecraft to finish the update"). It touches nothing else, and nothing ever installs on its own.
- **View on GitHub** opens the release page in your browser.

## Servers

Kairo only changes what your own screen draws and plays: no cheats, no automation, no packet changes. Still, some
servers have their own rules about client mods (for example Fullbright or Zoom). Check a server's rules before you
play there; you're responsible for following them.

## License

Kairo Client is free software under the [GNU General Public License v3.0](../LICENSE): you can use, study, change and share it,
and anything you share that's built from it has to stay under the same licence, with its source available.
Copyright (C) 2026 Mordsome. It comes with no warranty, as the licence explains.

Third-party parts and what they're licensed under are listed in [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft. Minecraft is a trademark of
Mojang Synergies AB. Sodium and Fabric are separate projects with their own licences; they are never bundled here.
Kairo isn't affiliated with any other client or mod, and every texture, sound, animation and line of code in it was
made for this project (the two fonts are the exception; see the notices).
