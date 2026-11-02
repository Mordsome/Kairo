# Changelog

## 0.2.0

Both editions.

- **Wallpaper colours:** like Material You, the theme can come from your main menu background. A custom image, GIF
  (first frame) or panorama gives 4 to 6 swatches (Minecraft's panorama and the Void scene have built-in ones), shown
  in Visuals → Theme (Void: Interface → Wallpaper colours), the background picker and the first-run colour step.
  Click one to use it; **Use wallpaper colours** follows the background when it changes. Read once per picture, cached.
- **Theme tints:** **Tint backgrounds** gives panels, popups, toasts, Minecraft's restyled menus and HUD boxes a dark
  tone of your colour; **Tint text** gives main text a very light tint and secondary text a softer one. Both have a
  strength slider. Text is kept at WCAG 4.5:1 contrast or better. Void keeps its flat style.
- **Per mod:** **Follow theme colours** (every mod with colours) and **Tinted background** (every mod with a
  background), built on the existing accent system.
- **Look codes** carry the colours and tint settings, never the wallpaper.
- **CPS label:** editable (`CPS` by default; empty shows just `8 | 6`).
- **Show CPS in Keystrokes:** the mouse buttons show Label, CPS or Both (small label above the number). The CPS box
  hides while Keystrokes shows it (optional); the layout editor and the PvP layout handle both.
- **Panel edge light:** the light running along the top of big panels has its own switch (Visuals → Menus).
- **Updates (opt-in, off by default):** "Check for updates on GitHub" in the first-run setup and Settings → About.
  Once per launch it asks GitHub for the latest release (nothing about you is sent); a newer version shows a notice
  on the main menu and an Update button with the release notes in Settings → About. Update downloads your edition's
  jar over HTTPS, checks it against `SHA256SUMS.txt` and swaps it in after the game closes. Nothing installs without
  a click.
- **Config:** profiles from 0.1.0 load unchanged (config v2): the tints start off for them, and the panel edge light
  stays off if Living menus was off.
- First-run setup has nine steps (the updates step is new).

## 0.1.0 (first public release)

A visuals-only client for Minecraft Java 26.3 (Fabric + Sodium). No cheats, no automation, nothing extra sent to servers.

- **Menus:** Right Shift ring menu (pick 3 to 8 segments), glass panels for mods, visuals and settings, a first-run
  welcome setup, and five main menu styles (Kairo, Dock, Orbit, Custom widget home screen, Minecraft).
- **HUD:** 25+ mods with a drag-and-drop layout editor (multi-select, undo/redo), PvP mode with its own layout and settings.
- **Visuals:** 10 attack and 7 mining animations, weapon inspect (V), swing trail, held item shimmer and outline,
  11 colour filters and screen effects, hit and kill effects, jump, landing, speed lines and explosion effects.
- **Cosmetics:** capes (some animated), wings, hats (with fit sliders and animated cat ears), bandanas, trails and
  emotes with a hold-to-pick wheel. Only you see them.
- **Colours:** every colour can be static, chroma or a gradient through up to 5 colours.
- **Emotes:** 37 emotes in four pages (Dances, Reactions, Poses, Moves) and up to 12 favourites; the wheel has tabs and
  scrolls between pages, right-click favourites an emote. New: Floss, Disco, Robot, Headbang, Shuffle, Wiggle, Laugh,
  Cry, Point, Think, Angry, Excited, Meditate, Hero pose, Kneel, Flex, Victory, Lounge, Front flip, Cartwheel, Jump for
  joy, Levitate. A small burst of light when an emote starts.
- **More visuals (both editions):** kill effects Shatter, Void implosion and Ascend; five sound packs (hit, crit and
  kill sounds); projectile trails and a ring where ender pearls land; item physics with an optional glow under rare
  items; a real bloom; motion blur that follows how fast you turn; share codes for your whole look (Copy my look,
  Paste a look); the living ring and living menus.
- **Safety:** a photosensitivity warning in the first-run setup, and **Fewer flashes**, which tones down explosion and
  hit flashes, lightning, the totem blast, the hologram flicker and the low-health beat.
- **Kairo Void welcome:** shows what Void adds next to plain Kairo, then a look (presets) and how much effect you
  want (Low to Insane), the menu style, a HUD and cosmetics.
- **Kairo Void** (second jar, same code): flat dark-grey menus with one accent, the same cosmetics and emotes, PvP mode,
  quality presets (Low to Insane), energy slashes with heat haze (double on crits), impact shockwaves, white hit flash,
  hit-stop, totem pop blast, a cosmic night sky (nebula, galaxy, ringed planet, shooting stars), aurora, richer
  sunsets, depth fog, and the Stage main menu. Later in 0.1.0: energy blades on the weapon instead of full-screen slashes,
  a card-grid Visuals panel, nine atmospheres, glowing HUD styles, keystroke ripples, living menus and a living ring.
- Mod colours follow the accent until changed; **Accent on every mod** option.
- Void: visuals on/off switch (ring Effects segment, J, Visuals page), presets, crystal styles and crystal pop,
  picture cards for atmospheres, a drawn space scene instead of the panorama, and Stage / Simple / Minecraft menus.