# SteamDeck

Valve's **native aarch64 Linux Steam client**, running on an Android phone or handheld: a glibc
rootfs under proot, gamescope as a Wayland client of an in-app Vulkan compositor, Xwayland and
Zink for the client's CEF, and the device's Turnip driver underneath. There is no Wine here, and
no x86 translation for the client itself — it is ARM code running as ARM code. Windows games it
launches go through Valve's own ARM64 Proton build, which is where FEX comes in.

One screen, one button: install the runtime, press Play, Big Picture comes up.

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

## Keyboard

**Keyboard** in the drawer opens the soft keyboard over the session. Steam's UI and the games
under it are X11 clients of gamescope and know nothing of Wayland text input, so what the
keyboard produces is turned into the key presses that would have typed it — Shift included, so
capitals and the symbol row (`@`, `!`, `_` …) arrive as themselves — and fed to the compositor as
key events. A hardware keyboard works as it is.

## Compatibility tools

**Compatibility tools** (main screen and drawer) installs GE-Proton or proton-cachyos — native
ARM64 builds — beside the ARM64 Proton Valve ships. The runtime's own registrar downloads and
installs a requested build when the next session starts (several hundred MB), patches it past
pressure-vessel like Valve's, and it then appears in Steam under Properties → Compatibility for
any game. The dialog queues or cancels a request and removes an installed build.

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

Session logs land in `/sdcard/Download/SteamDeck/`.

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
