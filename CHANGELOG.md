# Changelog

## 0.3.0

Mostly Kairo Void: new visuals that look nothing like vanilla (custom geometry and shaders, no particles), every one
switchable in Visuals and respecting Fewer flashes and Reduce motion.

- **Start-up intro (Void):** when the game opens, "Kairo" rises into place and "Void" pops in next to it with a ring of
  light. Once per launch, never when you leave a world; click to skip.
- **Custom sky (Void):** your own picture as the sky (a 2:1 panorama wraps all the way round), slowly turning, day and
  night or only one of them, with the sun, moon and stars kept on top if you like. Pictures live in
  `config/kairo-void/skies`.
- **Living aurora (Void):** ribbons of light winding across the whole night sky, with light running along them and
  brighter folds; colours: green and violet, your colour, rainbow or sunset. The old curtains are still a style.
- **Aura and line glyphs (Void):** orbiting cubes, light ribbons, prism shards or halo rings around you, and animated
  angular lines (ticks, brackets, runes) turning at your feet, waist and over your head. Only around your own player.
- **Through your own eyes (Void):** in first person your aura and glyphs spread out a few blocks round you (wide rings
  at your feet, one just below eye level, one high above), so the view is alive without anything in your face.
- **Light ribbons (Void):** long ribbons of light hanging and twisting in the air around you, like an aurora you walk
  through. They belong to places in the world, so you pass them as you travel; brightest at night, fainter by day.
- **Floating motes (Void):** small lights hanging in the air close around you that drift and slowly breathe.
- **Light particles (Void):** Kairo's effects (hits, kills, jumps, explosions, crystals, atmospheres, trails, emotes)
  and Minecraft's combat, death, potion and explosion particles are drawn as sparks, shards, orbs, rings, petals and
  flakes of light instead of pixel sprites. Ambient ones (torches, smoke, rain) stay as they are.
- **Player trail (Void):** a fading ribbon, comet, twin streaks or prism trail behind you.
- **Your look (Void):** an outline, a glow or a hologram (see-through light with scan lines) on your own player only.
- **Hit impact (Void):** shards, a sigil, a cross slash, a star or rings of light where your hit lands; bigger on crits.
- **Animated damage markers (Void):** numbers punch in with a slash behind them, stack into combos (×3), get spikes and a
  CRIT tag on critical hits, and break into slices as they fade.
- **Cinematic kill (Void):** after a kill a drawing (white with black outlines, black with white lines, or in your
  colour) spreads out from the kill like a wave of ink across the world, then draws back into it. Slow and smooth or
  sharp and quick; with Fewer flashes it's always the smooth, calm grey one.
- **Block overlay (Void):** brackets, a scan, a hologram grid or a pulse on the block you look at, in place of the
  outline. It glides and reshapes smoothly from one block to the next and fades in and out (on by default as brackets).
- **Nametags (Void):** Glass, Neon, Hologram or Shimmer styles for players' names, shown exactly when Minecraft shows
  them (nothing through walls that vanilla wouldn't show), and optionally your own name in F5.
- **Music emotes (both editions):** two emotes that play your own song (OGG or WAV, up to 5 minutes, only you hear it)
  while you dance to it. Set the song's BPM and a beat offset so the dance lands on the beat; pick the dance, including
  two new ones made for it (Beat bounce and Beat step, also on the Dances page). A new Music page on the wheel.
- New "You" group in Void's Visuals; every new effect has its own card with its options.
- **Favourites (Void):** Visuals opens on a Favourites tab with the most important effects already in it (hand
  animations, held item glow, blades, aura, glyphs, ribbons, motes, aurora, cosmic sky, block overlay, light
  particles, hit impacts, damage markers, cinematic kill, trail). Right-click any card to add or remove it.
- Dev: `-Pvulkan` starts dev runs on Minecraft's Vulkan backend, `-Pnokairo` without Kairo (to tell a Kairo problem
  from a Minecraft or driver one), and a backend comparison tour (`build/tour-vk`).

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