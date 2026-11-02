(() => {
  "use strict";
  const root = document.documentElement;
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const $ = (s) => document.querySelector(s);

  const REPO = {
    kairo: "https://github.com/Mordsome/Kairo",
    void: "https://github.com/Mordsome/KairoVoid",
  };
  const COLORS = {
    kairo: { bg: "#0a1322", accent: [127, 216, 255], hot: [255, 154, 82] },
    void: { bg: "#0b0816", accent: [167, 124, 255], hot: [255, 110, 199] },
  };
  const img = (ed, n) => `img/${ed === "void" ? "void" : "kairo"}/${n}.jpg`;

  const TOUR = [
    { id: "ring", t: "The ring", d: "Right Shift opens a glass ring. Choose which 3 to 8 sections it shows.", k: "11_ring_menu", v: "11_ring_menu" },
    { id: "mods", t: "Mods", d: "25+ mods as cards, tiles or a list. Every one has its own settings and a PvP mode version.", k: "12_mods", v: "12_mods" },
    { id: "hud", t: "HUD editor", d: "Drag, resize, multi-select, undo. PvP mode gets its own layout.", k: "18_hud_layout_editor", v: "18_hud_layout_editor" },
    { id: "menus", t: "Main menus", d: "Kairo, Dock, Orbit, your own widget grid, or Void's spotlight stage.", k: "02_main_menu_orbit", v: "00_main_menu_stage" },
    { id: "visuals", t: "Visuals", d: "Attack animations, colour filters, hit effects, and in Void, whole presets.", k: "13_visuals_0", v: "13_visuals_1" },
    { id: "wardrobe", t: "Wardrobe", d: "Capes, wings, hats, trails and 37 emotes. Only you see them.", k: "15_wardrobe_capes", v: "15_wardrobe_capes" },
  ];
  const STRIP = {
    kairo: ["00_main_menu_classic", "10_gameplay_hud", "31_filter_vaporwave", "16_emote_wheel", "03_main_menu_dashboard", "33_kill_effect", "20_cosmetics_front", "01_main_menu_dock", "13_visuals_1", "07_welcome_2"],
    void: ["41_aurora", "48_totem_blast", "45_crystal_prism", "01_main_menu_simple", "40_atmosphere_synthwave", "47_hud_neon", "43_galaxy_overhead", "32_hit", "40_atmosphere_blood_moon", "07_welcome_3"],
  };
  const ATMOS = [["galaxy", "Galaxy"], ["blizzard", "Blizzard"], ["blood_moon", "Blood moon"], ["inferno", "Inferno"], ["sakura", "Sakura"], ["toxic", "Toxic"], ["abyss", "Abyss"], ["synthwave", "Synthwave"], ["storm", "Storm"]];

  let edition = "kairo";
  try { if (localStorage.getItem("kairo-edition") === "void") edition = "void"; } catch (e) {}
  if (location.hash === "#void") edition = "void";

  /* ---------- pixel field ---------- */
  const cv = $("#pixels"), cx = cv.getContext("2d");
  const CELL = 18;
  let W = 0, H = 0, cols = 0, rows = 0, dpr = 1, heat = new Float32Array(0), active = new Set(), grid = null, stars = [];
  const ripples = [];
  let mouse = null, last = null;

  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    W = innerWidth; H = innerHeight;
    cv.width = W * dpr; cv.height = H * dpr; wipe.width = W * dpr; wipe.height = H * dpr;
    cx.setTransform(dpr, 0, 0, dpr, 0, 0); wx.setTransform(dpr, 0, 0, dpr, 0, 0);
    cols = Math.ceil(W / CELL); rows = Math.ceil(H / CELL);
    heat = new Float32Array(cols * rows); active.clear();
    grid = document.createElement("canvas"); grid.width = W * dpr; grid.height = H * dpr;
    const g = grid.getContext("2d"); g.setTransform(dpr, 0, 0, dpr, 0, 0);
    g.fillStyle = "rgba(255,255,255,0.035)";
    for (let y = 0; y < rows; y++) for (let x = 0; x < cols; x++) g.fillRect(x * CELL + CELL / 2 - 1, y * CELL + CELL / 2 - 1, 2, 2);
    stars = Array.from({ length: Math.round(W * H / 9000) }, () => ({ x: Math.random() * W, y: Math.random() * H, s: Math.random() < 0.15 ? 2 : 1, p: Math.random() * 6.28, v: 0.4 + Math.random() }));
    if (reduce) draw(0);
  }
  function bump(px, py, r, amt) {
    const cxl = Math.floor(px / CELL), cyl = Math.floor(py / CELL), R = Math.ceil(r);
    for (let y = cyl - R; y <= cyl + R; y++) {
      if (y < 0 || y >= rows) continue;
      for (let x = cxl - R; x <= cxl + R; x++) {
        if (x < 0 || x >= cols) continue;
        const d = Math.hypot(x - cxl, y - cyl);
        if (d > r) continue;
        const i = y * cols + x;
        heat[i] = Math.min(1, heat[i] + amt * (1 - d / (r + 0.5)));
        active.add(i);
      }
    }
  }
  function draw(t) {
    cx.clearRect(0, 0, W, H);
    if (edition === "void") {
      for (const s of stars) {
        const a = 0.25 + 0.35 * Math.sin(t / 1000 * s.v + s.p);
        cx.fillStyle = `rgba(230,220,255,${Math.max(0.05, a)})`;
        cx.fillRect(s.x, s.y, s.s, s.s);
      }
    }
    cx.drawImage(grid, 0, 0, W, H);
    const [r, g, b] = COLORS[edition].accent, [hr, hg, hb] = COLORS[edition].hot;
    for (const i of active) {
      const v = heat[i];
      if (v < 0.02) { heat[i] = 0; active.delete(i); continue; }
      const x = (i % cols) * CELL, y = Math.floor(i / cols) * CELL;
      const mix = v > 0.85 ? (v - 0.85) / 0.15 : 0;
      cx.fillStyle = `rgba(${r + (hr - r) * mix | 0},${g + (hg - g) * mix | 0},${b + (hb - b) * mix | 0},${v * 0.75})`;
      cx.fillRect(x + 2, y + 2, CELL - 4, CELL - 4);
      heat[i] = v * 0.93;
    }
  }
  function frame(t) {
    if (mouse) {
      if (last) {
        const dx = mouse.x - last.x, dy = mouse.y - last.y, steps = Math.min(20, Math.ceil(Math.hypot(dx, dy) / (CELL / 2)));
        for (let s = 1; s <= steps; s++) bump(last.x + dx * s / steps, last.y + dy * s / steps, 1.9, 0.55);
      }
      last = { ...mouse };
    }
    for (let k = ripples.length - 1; k >= 0; k--) {
      const rp = ripples[k];
      rp.r += 0.55;
      const n = Math.max(12, Math.round(rp.r * 7));
      for (let a = 0; a < n; a++) {
        const ang = a / n * Math.PI * 2;
        bump(rp.x + Math.cos(ang) * rp.r * CELL, rp.y + Math.sin(ang) * rp.r * CELL, 0.6, 0.9 * (1 - rp.r / rp.max));
      }
      if (rp.r >= rp.max) ripples.splice(k, 1);
    }
    if (Math.random() < 0.25) bump(Math.random() * W, Math.random() * H, 0.4, 0.3);
    draw(t);
    requestAnimationFrame(frame);
  }
  addEventListener("pointermove", (e) => { mouse = { x: e.clientX, y: e.clientY }; }, { passive: true });
  addEventListener("pointerleave", () => { mouse = null; last = null; });
  document.addEventListener("pointerout", (e) => { if (!e.relatedTarget) { mouse = null; last = null; } });
  addEventListener("pointerdown", (e) => { if (!reduce) ripples.push({ x: e.clientX, y: e.clientY, r: 0, max: 9 }); }, { passive: true });

  /* ---------- edition switch + pixel wipe ---------- */
  const wipe = $("#wipe"), wx = wipe.getContext("2d");
  const scr = $(".scramble");
  const GLYPHS = "▓▒░█▚▞▖▗▘▝#%&@$";
  function scramble(el, to) {
    if (reduce) { el.textContent = to; return; }
    const from = el.textContent, len = Math.max(from.length, to.length), t0 = performance.now(), dur = 650;
    (function step(now) {
      const p = Math.min(1, (now - t0) / dur);
      let html = "";
      for (let i = 0; i < len; i++) {
        const lock = p * len * 1.4 - i * 0.4;
        if (lock >= 1) html += to[i] || "";
        else if (lock > 0 || from[i] !== to[i]) html += `<span class="glitch">${GLYPHS[(Math.random() * GLYPHS.length) | 0]}</span>`;
        else html += from[i] || "";
      }
      el.innerHTML = html;
      if (p < 1) requestAnimationFrame(step); else el.textContent = to;
    })(t0);
  }
  function apply(ed, animate) {
    edition = ed;
    root.dataset.edition = ed;
    document.querySelector('meta[name="theme-color"]').setAttribute("content", COLORS[ed].bg);
    document.querySelector('link[rel="icon"]').href = ed === "void" ? "img/icon-void.png" : "img/icon.png";
    document.querySelectorAll(".switch button").forEach((b) => b.setAttribute("aria-checked", String(b.dataset.set === ed)));
    document.querySelectorAll("[data-kairo]").forEach((el) => { if (el !== scr) el.textContent = el.dataset[ed]; });
    animate ? scramble(scr, scr.dataset[ed]) : (scr.textContent = scr.dataset[ed]);
    $("#download").href = REPO[ed] + "/releases/latest";
    $(".rel-link").href = REPO[ed] + "/releases/latest";
    $(".jarname").textContent = ed === "void" ? "kairo-void-0.1.0.jar" : "kairo-0.1.0.jar";
    document.title = ed === "void" ? "Kairo Void: Kairo, turned all the way up" : "Kairo: a visual client for Minecraft 26.3";
    buildStrip(); showTour(tourIdx, false);
    try { localStorage.setItem("kairo-edition", ed); } catch (e) {}
  }
  let switching = false;
  function switchTo(ed) {
    if (ed === edition || switching) return;
    if (reduce) { apply(ed, false); return; }
    switching = true;
    const seed = Array.from({ length: cols * rows }, (_, i) => Math.random() * 0.45 + ((i % cols) / cols) * 0.55);
    const color = COLORS[ed].bg, accent = `rgb(${COLORS[ed].accent.join(",")})`, t0 = performance.now(), dur = 900;
    let swapped = false;
    (function step(now) {
      const p = Math.min(1, (now - t0) / dur);
      wx.clearRect(0, 0, W, H);
      const inP = Math.min(1, p * 2), outP = Math.max(0, p * 2 - 1);
      for (let i = 0; i < seed.length; i++) {
        const s = seed[i];
        if (inP <= s || outP > s) continue;
        const x = (i % cols) * CELL, y = Math.floor(i / cols) * CELL;
        const edge = inP - s < 0.035 || s - outP < 0.035;
        wx.fillStyle = edge ? accent : color;
        wx.fillRect(x, y, CELL, CELL);
      }
      if (!swapped && p >= 0.5) { swapped = true; apply(ed, true); }
      if (p < 1) requestAnimationFrame(step); else { wx.clearRect(0, 0, W, H); switching = false; }
    })(t0);
  }
  document.querySelectorAll(".switch button").forEach((b) => {
    b.addEventListener("click", () => switchTo(b.dataset.set));
    b.addEventListener("keydown", (e) => {
      if (e.key === "ArrowLeft" || e.key === "ArrowRight") { e.preventDefault(); const t = edition === "kairo" ? "void" : "kairo"; switchTo(t); document.querySelector(`.switch [data-set="${t}"]`).focus(); }
    });
  });

  /* ---------- ring ---------- */
  const ring = $("#ring"), NS = "http://www.w3.org/2000/svg";
  const C = 300, RO = 262, RI = 140, GAP = 5;
  const ICONS = {
    grid: "M-8 -8h6v6h-6zM2 -8h6v6h-6zM-8 2h6v6h-6zM2 2h6v6h-6z",
    sliders: "M-8 -5h10M6 -5h2M-8 5h2M2 5h6M4 -5m-2.2 0a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0M-2 5m-2.2 0a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0",
    sword: "M2.5 -8.5h6v6L-2 8L-8 2ZM-5 5l-3 3M1 -4.5l3.5 3.5",
    sparkle: "M0 -9L1.9 -2.9L8 -1L1.9 0.9L0 7L-1.9 0.9L-8 -1L-1.9 -2.9Z",
    layout: "M-8.5 -7.5h17v15h-17zM-8.5 -2.5h17M-2 -2.5v10",
    hanger: "M0 -6a2.5 2.5 0 1 1 2.5 2.5L0 -1.5L-9 6h18L0 -1.5",
  };
  const SEGS = [
    { id: "mods", label: "Mods", icon: "grid", kick: "Open", title: "Mods", desc: "25+ of them, each with its own settings", go: "mods" },
    { id: "settings", label: "Settings", icon: "sliders", kick: "Open", title: "Settings", desc: "Keybinds, profiles, questions", go: "#faq" },
    { id: "pvp", label: "PvP Mode", icon: "sword", kick: "Click to toggle", title: "PvP mode", desc: "No hurt tilt, calmer explosions, small totem pops", pill: true },
    { id: "visuals", label: "Visuals", icon: "sparkle", kick: "Click to switch", title: "Visuals", desc: "Flip the whole site to the other edition", edition: true },
    { id: "layout", label: "Layout", icon: "layout", kick: "Edit", title: "Layout", desc: "Drag your HUD around", go: "hud" },
    { id: "cosmetics", label: "Cosmetics", icon: "hanger", kick: "Open", title: "Cosmetics", desc: "Capes, wings, hats and emotes", go: "wardrobe" },
  ];
  const n = SEGS.length, span = (Math.PI * 2) / n;
  let pvpOn = false;
  const el = (tag, attrs, parent) => { const e = document.createElementNS(NS, tag); for (const k in attrs) e.setAttribute(k, attrs[k]); if (parent) parent.appendChild(e); return e; };
  const P = (a, r) => `${(C + r * Math.cos(a)).toFixed(2)} ${(C + r * Math.sin(a)).toFixed(2)}`;
  el("circle", { cx: C, cy: C, r: 292, class: "ring-dots" }, ring);
  const orbit = el("g", { class: "orbit-g" }, ring);
  el("circle", { cx: C, cy: C - 292, r: 4, class: "ring-orbit" }, orbit);
  el("circle", { cx: C + 292, cy: C, r: 2.5, class: "ring-orbit", opacity: ".6" }, orbit);
  const segEls = SEGS.map((s, i) => {
    const mid = -Math.PI / 2 + i * span, a0 = mid - span / 2, a1 = mid + span / 2;
    const ao = Math.asin(GAP / RO), ai = Math.asin(GAP / RI);
    const d = `M ${P(a0 + ao, RO)} A ${RO} ${RO} 0 0 1 ${P(a1 - ao, RO)} L ${P(a1 - ai, RI)} A ${RI} ${RI} 0 0 0 ${P(a0 + ai, RI)} Z`;
    const g = el("g", { class: "seg", tabindex: "0", role: "button", "aria-label": s.pill ? "PvP mode, off" : s.label }, ring);
    g.style.animationDelay = `${120 + i * 70}ms`;
    const mv = el("g", { class: "seg-move" }, g);
    el("path", { d, class: "seg-shape" }, mv);
    const lr = (RO + RI) / 2, lx = C + lr * Math.cos(mid), ly = C + lr * Math.sin(mid);
    el("path", { d: ICONS[s.icon], class: "seg-icon", transform: `translate(${lx} ${ly - 16})` }, mv);
    el("text", { x: lx, y: ly + 15, class: "seg-label" }, mv).textContent = s.label;
    if (s.pill) {
      el("rect", { x: lx - 20, y: ly + 22, width: 40, height: 16, rx: 8, class: "seg-pill" }, mv);
      s.pillT = el("text", { x: lx, y: ly + 34, class: "seg-pill-t" }, mv); s.pillT.textContent = "OFF";
    }
    s.g = g; s.mv = mv; s.dx = Math.cos(mid) * 12; s.dy = Math.sin(mid) * 12;
    const enter = () => hot(s), leave = () => hot(null);
    g.addEventListener("pointerenter", enter); g.addEventListener("pointerleave", leave);
    g.addEventListener("focus", enter); g.addEventListener("blur", leave);
    g.addEventListener("click", () => act(s));
    g.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); act(s); } });
    return s;
  });
  el("circle", { cx: C, cy: C, r: 122, class: "ring-glass" }, ring);
  el("circle", { cx: C, cy: C, r: 112, class: "ring-dots" }, ring);
  if (!reduce) {
    ring.classList.add("ring-in");
    let a = 0; (function spin() { a = (a + 0.12) % 360; orbit.setAttribute("transform", `rotate(${a} ${C} ${C})`); requestAnimationFrame(spin); })();
  }
  function core(k, t, d) { $("#coreKick").textContent = k; $("#coreTitle").textContent = t; $("#coreDesc").textContent = d; }
  function hot(s) {
    segEls.forEach((x) => { x.g.classList.toggle("hot", x === s); x.mv.style.transform = x === s ? `translate(${x.dx}px, ${x.dy}px)` : ""; });
    if (!s) return core("Right Shift menu", "Menu", "Hover a section");
    if (s.pill) return core(s.kick, pvpOn ? "PvP mode on" : "PvP mode off", pvpOn ? "No hurt tilt, calmer explosions, small totem pops" : "Normal visuals. Click to go PvP.");
    if (s.edition) return core(s.kick, edition === "void" ? "Back to Kairo" : "Go Void", s.desc);
    core(s.kick, s.title, s.desc);
  }
  function act(s) {
    const r = s.g.getBoundingClientRect();
    if (!reduce) ripples.push({ x: r.left + r.width / 2, y: r.top + r.height / 2, r: 0, max: 7 });
    if (s.pill) {
      pvpOn = !pvpOn; s.g.classList.toggle("on", pvpOn); s.pillT.textContent = pvpOn ? "ON" : "OFF";
      s.g.setAttribute("aria-label", `PvP mode, ${pvpOn ? "on" : "off"}`); hot(s); return;
    }
    if (s.edition) { switchTo(edition === "void" ? "kairo" : "void"); setTimeout(() => hot(s), 500); return; }
    if (s.go.startsWith("#")) { document.querySelector(s.go).scrollIntoView({ behavior: reduce ? "auto" : "smooth" }); return; }
    showTour(TOUR.findIndex((x) => x.id === s.go), true);
    $("#tour").scrollIntoView({ behavior: reduce ? "auto" : "smooth" });
  }

  /* ---------- tour ---------- */
  const list = $("#tourList"), tImg = $("#tourImg"), cap = $("#tourCap");
  let tourIdx = 0;
  const tabs = TOUR.map((t, i) => {
    const b = document.createElement("button");
    b.type = "button"; b.className = "tour-item"; b.setAttribute("role", "tab"); b.id = "tab-" + t.id;
    b.innerHTML = `<b>${t.t}</b><span>${t.d}</span>`;
    b.addEventListener("click", () => showTour(i, true));
    b.addEventListener("pointerenter", () => { if (matchMedia("(hover: hover)").matches) showTour(i, true); });
    b.addEventListener("keydown", (e) => {
      const k = e.key; let j = null;
      if (k === "ArrowDown" || k === "ArrowRight") j = (i + 1) % TOUR.length;
      if (k === "ArrowUp" || k === "ArrowLeft") j = (i - 1 + TOUR.length) % TOUR.length;
      if (j !== null) { e.preventDefault(); showTour(j, true); tabs[j].focus(); }
    });
    list.appendChild(b); return b;
  });
  function showTour(i, animate) {
    if (i < 0) i = 0;
    const same = i === tourIdx; tourIdx = i;
    tabs.forEach((b, k) => { b.setAttribute("aria-selected", String(k === i)); b.tabIndex = k === i ? 0 : -1; });
    const t = TOUR[i], src = img(edition, edition === "void" ? t.v : t.k);
    const alt = `${edition === "void" ? "Kairo Void" : "Kairo"}: ${t.t}`;
    cap.textContent = t.d;
    if (tImg.getAttribute("src") === src) return;
    if (!animate || reduce) { tImg.src = src; tImg.alt = alt; return; }
    tImg.classList.add("fade");
    setTimeout(() => { tImg.src = src; tImg.alt = alt; tImg.onload = () => tImg.classList.remove("fade"); if (tImg.complete) tImg.classList.remove("fade"); }, same ? 0 : 160);
  }
  const frameEl = $(".shot-frame");
  if (!reduce) {
    frameEl.addEventListener("pointermove", (e) => {
      const r = frameEl.getBoundingClientRect(), x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
      frameEl.style.setProperty("--ry", `${x * 7}deg`); frameEl.style.setProperty("--rx", `${-y * 7}deg`);
    });
    frameEl.addEventListener("pointerleave", () => { frameEl.style.setProperty("--ry", "0deg"); frameEl.style.setProperty("--rx", "0deg"); });
  }

  /* ---------- strip ---------- */
  function buildStrip() {
    const track = $("#strip"); track.innerHTML = "";
    const names = STRIP[edition];
    [...names, ...names].forEach((n, k) => {
      const im = document.createElement("img");
      im.src = img(edition, n); im.loading = "lazy"; im.width = 1600; im.height = 900;
      im.alt = k < names.length ? "Screenshot of " + (edition === "void" ? "Kairo Void" : "Kairo") : "";
      if (k >= names.length) im.setAttribute("aria-hidden", "true");
      track.appendChild(im);
    });
  }

  /* ---------- atmospheres ---------- */
  const pick = $("#atmoPick"), aImg = $("#atmoImg");
  ATMOS.forEach(([id, name], i) => {
    const b = document.createElement("button");
    b.type = "button"; b.setAttribute("role", "radio"); b.textContent = name; b.setAttribute("aria-checked", String(i === 0));
    b.addEventListener("click", () => {
      pick.querySelectorAll("button").forEach((x) => x.setAttribute("aria-checked", String(x === b)));
      aImg.style.opacity = "0.2";
      setTimeout(() => { aImg.src = img("void", "40_atmosphere_" + id); aImg.alt = `The ${name} atmosphere in Kairo Void`; aImg.style.opacity = "1"; }, 140);
    });
    pick.appendChild(b);
  });

  /* ---------- start ---------- */
  resize();
  addEventListener("resize", resize);
  apply(edition, false);
  if (!reduce) requestAnimationFrame(frame);
})();
