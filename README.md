# SteamDeck

Valve's **native aarch64 Linux Steam client**, running on an Android phone or handheld: a glibc
rootfs under proot, gamescope as a Wayland client of an in-app Vulkan compositor, Xwayland and
Zink for the client's CEF, and the device's Turnip driver underneath. There is no Wine here, and
no x86 translation for the client itself — it is ARM code running as ARM code. Windows games it
launches go through Valve's own ARM64 Proton build, which is where FEX comes in.

One screen, one button: install the runtime, press Play, Big Picture comes up. Or start the
**desktop** instead — LXQt, a browser and a shelf of emulators, in the same session.

## Requirements

- An **arm64 Android device with an Adreno GPU that Turnip supports** — in practice Adreno **730 or
  newer** (Snapdragon 8 Gen 1 onward) and the **8xx** series. Mali, Xclipse and PowerVR are not
  supported here, and neither is the low-end Adreno 710 (Snapdragon 6 Gen 1): Mesa has no entry
  for it at all, so the session log ends at `device (chip_id = 7010000, gpu_id = 710) is
  unsupported` before anything is drawn.
- **Android 9 or newer**, and roughly **3 GB free** for the runtime before any games. The desktop
  and its emulator packages are another ~1.1 GB if you install them.
- **No root required.** Root only helps if you want to copy an existing Bannerlator runtime across
  instead of downloading one.

## Installing

1. Install the APK from the release page.
2. Launch it and press **Install Linux runtime** — about 790 MB, once.
3. Press **Play**. On the very first run the app fetches Valve's client (a minute or two with
   nothing on screen), then Big Picture comes up and you sign in.

For a desktop, open **Desktop & apps**, install the *Desktop* package, then press **Desktop** on
the main screen.

## What works today

This is version 0.1, and most of it has been exercised on one or two devices only. An honest
ledger rather than a feature list:

| | State |
|---|---|
| Steam sign-in, store, install, launch | ✅ proven — *FlatOut* at 144 Hz through ARM64 Proton |
| Frame generation (LSFG 2×) | ✅ proven — 30 → 61, 60 → 118, 61 → 123 fps |
| Performance HUD | ✅ proven |
| Desktop, panel, file manager | ✅ proven at the panel's native resolution |
| Firefox and the network (IPv4 + IPv6) | ✅ proven — live pages at 93 fps |
| Controller as an Xbox 360 pad | ✅ proven |
| Foldable, opened and closed mid-session | ✅ proven |
| Background / foreground, wake locks | ✅ proven |
| The emulators | ⚠️ they build, publish and install — none has been run with a game |
| Starting Steam offline | ⚠️ built, not yet tested |
| The soft keyboard | ⚠️ built, not yet tested |
| GE-Proton / proton-cachyos | ⚠️ built, not yet tested in this app |

## Known limits

- **The desktop composites in software.** wlroots allocates through gbm on a real DRM render node,
  and the Adreno stand-in is not one, so labwc falls back to pixman. Programs on the desktop still
  reach the GPU themselves through Vulkan and Turnip — it is the desktop's own compositing that
  runs on the CPU.
- **Firefox's child-process sandboxes are off.** Seccomp and namespace sandboxes cannot be set up
  under proot, and its tabs crash otherwise. This is a genuine reduction in isolation: treat
  browsing here as less protected than on an ordinary desktop.
- **The display shape changes at the next session**, not immediately — gamescope fixes its display
  size when it starts.
- **No Switch emulator.** Eden's repository was taken down in February; there is nothing to mirror
  or to build from.
- PS3 (RPCS3) and Wii U (Cemu) are demanding even on desktop hardware — expect light titles only.

## How it fits together

```
 Android app (this repo)
 ├── libbannerwayland.so      Wayland compositor, presents into the activity's Surface (Vulkan/Turnip)
 ├── PulseAudio 13 + AAudio   the guest's audio server, on a socket bound into the session
 ├── fake-evdev rings         a physical pad, republished as /dev/input/eventN for the client
 └── proot ── linuxfs (~790 MB, downloaded once)
              ├── gamescope ── Xwayland ── Zink
              └── Steam (steamrtarm64, fetched from Valve on first run) ── Proton ARM64 ── FEX
```

The runtime image is the one published for Bannerlator (`linuxfs.json` in
`The412Banner/winlator-contents`); this app installs and updates it from that catalog. **No Valve
software is redistributed** — the client is fetched from Valve's own manifest on the device at
first use.

## Building

Nothing is built locally. Push, and the `Build APK` workflow produces `steamdeck-apk`, a
debug-signed arm64 apk. It cross-compiles the two glibc preloads the session needs
(`libfakeinput.so`, `libblsession.so`) from the sources in this repo before the app build, so
those can never drift from what ships.

## If Bannerlator is already installed

The runtime image is the same one Bannerlator installs, so on a device that already has it the
790 MB download can be skipped entirely — copy it across with root, once, before the first launch:

```sh
su -c 'cp -a /data/data/com.tencent.ig/files/linuxfs /data/data/com.steamdeck.launcher/files/ \
  && chown -R $(stat -c %u /data/data/com.steamdeck.launcher/files) \
              /data/data/com.steamdeck.launcher/files/linuxfs \
  && chcon -R u:object_r:app_data_file:s0 /data/data/com.steamdeck.launcher/files/linuxfs'
```

The two runtimes are independent after that: each app has its own Steam install, its own login and
its own games.

## Controls

A physical controller is republished into the session as a synthetic Xbox 360 pad, which is the
identity SDL and Steam have a mapping for. With nothing attached, an on-screen pad appears —
d-pad, A/B/X/Y, shoulders, select/start and the Steam button — writing into the same rings, so the
client sees one pad either way. Connect a controller and the on-screen one disappears; unplug it
and it comes back. Anywhere the controls are not is a touchpad: touch moves the pointer and a tap
clicks, which is how the client's own on-screen keyboard is used to sign in.

## Desktop

Besides the Steam client the app can start a **desktop**: LXQt on labwc, in the same session and
the same compositor, with Xwayland for the X11 programs. It is a second choice on the main
screen, not a replacement — the Steam button still hands the session to gamescope.

What it is made of is downloaded on request from a catalog hosted beside the runtime, so the app
stays small and nothing ships that a given device will not use:

| Package | What it brings |
|---|---|
| **Desktop** (406 MB) | labwc, LXQt's panel, file manager, terminal, text and image viewers, Firefox, mpv |
| **Emulators** (694 MB) | PPSSPP and RetroArch with 28 cores — PS1, PS2, N64, SNES, NES, Game Boy, GBA, DS, Genesis, Dreamcast, Saturn, PC Engine, arcade — plus ScummVM, DOSBox, Mednafen, Snes9x |
| **RPCS3 · DuckStation · melonDS** | PS3, PS1 and DS, mirrored from the projects' own ARM64 builds |
| **PCSX2 · Dolphin · Cemu** | PS2, GameCube/Wii and Wii U. No ARM64 Linux build exists upstream for any of the three, so they are compiled from source against the same glibc the runtime uses |

The desktop composites in software: wlroots allocates through gbm on a real DRM render node, and
the Adreno stand-in is not one, so labwc runs on pixman. The programs on it still reach the GPU
themselves through Vulkan and Turnip — it is the desktop's own compositing that is on the CPU.
`Download/steamdeck-wlr-renderer` (`pixman`, `gles2` or `vulkan`) overrides the choice.

Programs whose child processes sandbox themselves with seccomp and namespaces cannot set those up
under proot; Firefox's are turned off in the session's environment, without which its tabs crash.

## Pointer

The app draws the arrow. On the desktop, touch is a **touchpad** (Bannerlator's desktop-container
feel): drag anywhere and the pointer moves from where it is; tap to click; a second finger tapping
while one holds, or a two-finger tap, right-clicks; two fingers scroll; hold still then drag, or
double-tap-and-hold, drags. In Steam, touch is **direct**: the pointer jumps under the finger. The
drawer's *Touch* switch pins either. A USB or Bluetooth mouse works as itself.

## Networking

The desktop's programs use the phone's network directly — proot makes no network namespace, so
IPv4 and IPv6 both pass through — and the app writes the runtime's `/etc/resolv.conf` from the
active network's own DNS servers whenever the link changes (IPv4 first, never a link-local IPv6 one,
public servers as fallback), which is what Firefox and everything else in the desktop resolve with.

## Keyboard

**Keyboard** in the drawer opens the soft keyboard over the session. Steam's UI and the games
under it are X11 clients of gamescope and know nothing of Wayland text input, so what the
keyboard produces is turned into the key presses that would have typed it — Shift included, so
capitals and the symbol row (`@`, `!`, `_` …) arrive as themselves — and fed to the compositor as
key events. A hardware keyboard works as it is.

## Starting Steam offline

The client runs without a connection — installed games launch, Proton and the frame generation
are local — provided it has credentials from a sign-in that succeeded earlier. **Start offline**
on the main screen is where that is decided, and not in the drawer, because the client reads the
two keys that control it once, while it starts: changed during a session it would write the file
and change nothing until the next launch. Without an earlier sign-in the button says so and does
nothing. The client rewrites those keys when it exits, so the app writes them again at every
session start. Games with always-online DRM will not start offline, and nothing can be installed
or updated.

## Audio

The Steam client always plays through **PulseAudio** — it is a native Linux program with no other
way to make a sound, and its menus, music and voice chat all go through it. **Audio** on the main
screen adds two things on top, each applying at the next session start:

- **DirectAudio for games** — replaces Wine's audio driver *inside the games the client launches*
  with Bannerlator's DirectAudio, which talks straight to Android over a socket instead of through
  PulseAudio: lower latency, and no resampling in between. The client is untouched. It only pairs
  with a Wine 11 Proton (Valve's ARM64 builds, GE-Proton, proton-cachyos); on anything else the
  wrapper leaves it off and says so in the log, because a mismatch would be silence rather than an
  error. A game's first launch after turning it on still uses Proton's own audio — the driver is
  named in the game's prefix, which does not exist until that first launch — and the second uses
  DirectAudio.
- **Microphone** — the relay helper opens Android's input stream under the app's own uid (which is
  what the recording permission is checked against) and PulseAudio exposes it to the client as a
  source named **DirectAudioMic**, so Steam voice chat works. Asks for the recording permission when
  turned on; off, nothing in the session can record and Android's indicator stays off.

The helper is a small Android program the app runs beside the session; the driver is a glibc build
staged into the runtime at every launch like the session scripts, so a fix reaches an installed
runtime without re-hosting it. Proven in Bannerlator's Linux session with voice in Steam's own
tester; ⚠️ not yet exercised in this app.

## Cores

**Performance** on the main screen carries the two core masks Bannerlator's Linux session has, kept
separate because they are wanted at the same time and suit different things:

- **Steam client cores** — Steam pins its own interface renderer to a subset of cores it chooses (on
  one device 5 of 8, leaving out both little cores and the fastest), which is reasonable while a
  game runs and makes Big Picture sluggish when the client is all there is. Turning the override
  on pins the client, its UI helper and gamescope to the cores you tick, re-applied every few
  seconds because the UI keeps spawning helpers that inherit Steam's choice. Every core ticked is
  the usual fix — and, unlike the game mask, it *is* sent when it names every core, since undoing
  Steam's pin is the whole point.
- **Game cores** — applied by the Proton wrapper, which execs the game through `taskset` so every
  thread inherits the mask from its first instruction. Leaving every core ticked sends nothing;
  untick the small cores to keep a heavy game off them.

Both apply at the next session start.

Under the same dialog, **session fixes** for a device the runtime does not sit well on, tried one at
a time. **Skip Steam's xalia helper** (`PROTON_USE_XALIA=0`) is the one to reach for when a session
dies seconds after Big Picture appears: xalia is an x86 Windows program Proton starts for gamepad
navigation, it cannot load the session's preload shim under FEX, and where the vendor's seccomp
answers its `socket()` and `memfd` calls with ENOSYS it storms until the session collapses (89
refusals then a broken pipe, in the log). It costs gamepad navigation in Windows programs that are
not games. Then two for a runtime that renders slowly: **Turnip: sysmem rendering** (`TU_DEBUG=sysmem`, Banners-Turnip's advice for an Adreno 8xx that
looks glitchy or slow; a 710/720/722 gets it on its own) and **Zink: lazy descriptors**
(`ZINK_DESCRIPTORS=lazy`, for Steam's menus, which Chromium draws through Zink — the first thing to
try when they are slow while games are fast). The `steamdeck-tu-debug` and `steamdeck-env` files in
Downloads still win over both.

## Compatibility tools

**Compatibility tools** (main screen and drawer) installs GE-Proton or proton-cachyos — native
ARM64 builds — beside the ARM64 Proton Valve ships. The runtime's own registrar downloads and
installs a requested build when the next session starts (several hundred MB), patches it past
pressure-vessel like Valve's, and it then appears in Steam under Properties → Compatibility for
any game. The dialog queues or cancels a request and removes an installed build.

## Graphics drivers

Two drivers, two lists, because a zip for one cannot serve the other — the same split Bannerlator's
Contents screen makes:

- **Linux runtime driver** — the glibc Turnip everything *inside* the runtime renders on: the Steam
  client's UI, every game it launches, and in desktop mode every program on the desktop. Import a
  "-Linux" Turnip zip from Banners-Turnip; a plain Android or "-Wayland" Turnip is refused with the
  reason, since a Linux process cannot load a bionic library. The choice is per mode, so Steam and
  the desktop can run different builds, and it takes effect at the next session start. Nothing in
  the runtime is modified: the session is handed the imported driver's ICD manifest and points the
  Vulkan loader at it, and the runtime's own Turnip is what a removed or unreadable import falls
  back to. This is how a driver fix reaches an installed runtime without a ~790 MB re-download.
- **Display driver (Android)** — the bionic Turnip the app's compositor puts the frame on the panel
  with, the last step of every session. *Auto* picks one of the two bundled builds by GPU; an
  imported AdrenoTools zip can be chosen instead. The compositor loads its driver once per app
  process, so this one applies after the app is fully closed and started again.

## Frame generation

Extra frames are generated between the real ones on the way to the screen — inside the app's
compositor, on gamescope's final output — so it works for any game with nothing changed in the
runtime. **Win-FG** is built in. **LSFG** uses the shader chain from your own copy of Lossless
Scaling: install it from the Steam client in this app and the app reads its `Lossless.dll` from
the runtime's Steam library (parsed as data, never executed; nothing of it is redistributed).
Pick an engine and 2×/3×/4× on the main screen; it applies immediately, mid-game included.

## Background and foreground

A session belongs to a foreground service, not to the activity, so leaving Big Picture does not
end it: the process stays at perceptible priority, a partial wake lock keeps the CPU from dropping
the guest's threads, and a high-performance WiFi lock keeps a backgrounded download from being
throttled to nothing. **Back opens the drawer**: the performance HUD switch, frame generation, on-screen controls
(auto / always / never), the display shape (the panel's own, or a fixed 16:9 — the default on a
foldable, so opening or closing it mid-session only changes the bars, never the picture; takes
effect on the next session), *Send to background* and *Stop session*. Backgrounded, Steam keeps
running and the (silent) notification brings it back. A session ends only when you say so — the
drawer or the notification's **Stop session** — or when the app is swiped out of recents.

## Device switches

Files in `/sdcard/Download`, for a device that cannot be reached with a debugger:

| File | Effect |
|---|---|
| `steamdeck-no-pad` | Turns the controller feature off entirely (a true baseline) |
| `steamdeck-pad-log` | Traces every interposer call into the session log |
| `steamdeck-driver` | `a7xx`, `a8xx` or `system` — overrides the Turnip build the compositor loads |
| `steamdeck-no-hud` | Hides the top-right performance line (fps, and `base → generated` while frame generation runs) |
| `steamdeck-osc` | `always` or `never` — pins the on-screen controls instead of following what is attached |
| `steamdeck-env` | `KEY=VALUE` lines added to the session's environment as written, after the app's own — Zink and Turnip tunables (`ZINK_DESCRIPTORS=lazy`, `MESA_*`), gamescope's, the client's; `#` comments allowed |
| `steamdeck-tu-debug` | Turnip's `TU_DEBUG` for the runtime's driver, verbatim (`sysmem`, `sysmem,deck_emu`). Without it, an imported A710/A720/A722 driver gets `sysmem` on its own |

Session logs land in `/sdcard/Download/SteamDeck/`, **one folder per session**, and are the first
thing to look at when something does not start:

```
Download/SteamDeck/session-20260921-161256/
    device.txt     what this device is, and every setting the session ran with
    session.log    the guest session: proot, gamescope, the client's own output
    wayland.log    the app's compositor - what it presented, and how fast
    app.log        what the app itself decided and reported
    crash.log      Android's crash buffer, as it stood when the session ended
    audio.log      the PulseAudio daemon and the DirectAudio relay helper
    network.txt    the link, the DNS the runtime was given, and what it means
    steam.log      the Steam client's log, scrubbed        (Steam mode)
    steam/         the rest of the client's logs, scrubbed (Steam mode)
    desktop.log    labwc, the panel and the programs on it (desktop mode)
```

`app.log` is this app's own logcat, filtered to its process: which driver it chose and why, the
audio line, a rival Steam client being stopped, a helper that was missing, the session's exit
status. `crash.log` is Android's crash buffer at teardown, which is where a session the *system*
killed leaves its only trace - proot once died before `main` over a missing library and said so
there and nowhere else. `audio.log` is the PulseAudio daemon's own output plus the relay helper's,
so a module that refuses to load is visible instead of being a silent absence of sound.
`network.txt` records the transport, whether the link validated, the address families and the DNS
servers the runtime was actually given - **never the network's name**.

`device.txt` is written before the session starts, so a session that dies in its first second
still says what it ran on: model, SoC, Android and kernel, cores and their ceilings, RAM, the GPU
as KGSL names it, the panel, **which driver each half of the session chose**, the runtime version,
and every switch that was in effect. **Nothing identifying is collected** - no serial number, no
device or advertising id, no account name, no network names - and the Steam logs are scrubbed line
by line on the way in: session tokens, machine-auth GUIDs, WebAPI keys, Guard codes and e-mail
addresses are replaced, and a SteamID is masked to its last four digits so lines can still be
correlated. `loginusers.vdf`, `config.vdf` and the `ssfn` files are never copied at all. A session
folder is meant to be attachable to a bug report exactly as it is.

### If Steam dies with nothing in the log

That is usually not Steam. **Android 12 and later kill the extra processes an app starts for
itself** once there are more than a few, and a session is made of dozens - proot, gamescope,
Xwayland, the client and its helpers, Wine, FEX. Where that is left on, the OS ends the session and
no log of ours says why, because nothing in the session did it. Some phones expose it in Developer
options as a **"restrict child processes"** switch; turn it off. Otherwise, once per device:

```sh
adb shell settings put global settings_enable_monitor_phantom_procs false
```

`device.txt` records what this phone reports, and the Performance dialog says so when it is not
disabled. Creating `~/.steamdeck-desktop-debug` inside the
runtime additionally makes the desktop log labwc at debug level, each program's window-protocol
traffic, and a name lookup plus an IPv4 and IPv6 fetch from inside the session — which is how to
tell a broken network apart from a program that only believes it has one.

## Authors

- **The412Banner** — the app, the Wayland compositor, the runtime plumbing and the Steam session.
- **maxjivi05 (Max)** — the gamescope runtime this is built on: the proot session, the session
  shim, the fake-evdev interposer and the controller work, from WinNative.

## Lineage and licence

GPL-3.0. The gamescope runtime, the session shim and the fake-evdev interposer come from
maxjivi05's WinNative work and from Bannerlator, both GPL-3.0; the Wayland compositor, the frame
generation engines and the runtime plumbing are Bannerlator's. Underneath all of it is Winlator
(brunodev85, GPL-3.0): the PulseAudio-on-AAudio audio stack, the gamepad state model and the shape
of a session's host-side components are his, and WinNative and Bannerlator are both Winlator
lineage. Valve, Steam and Proton are Valve Corporation's; this project
is not affiliated with Valve.
