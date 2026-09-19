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
su -c 'cp -a /data/data/com.tencent.ig/files/linuxfs /data/data/com.the412banner.steamdeck/files/ \
  && chown -R $(stat -c %u /data/data/com.the412banner.steamdeck/files) \
              /data/data/com.the412banner.steamdeck/files/linuxfs \
  && chcon -R u:object_r:app_data_file:s0 /data/data/com.the412banner.steamdeck/files/linuxfs'
```

The two runtimes are independent after that: each app has its own Steam install, its own login and
its own games.

## Device switches

Files in `/sdcard/Download`, for a device that cannot be reached with a debugger:

| File | Effect |
|---|---|
| `steamdeck-no-pad` | Turns the controller feature off entirely (a true baseline) |
| `steamdeck-pad-log` | Traces every interposer call into the session log |
| `steamdeck-driver` | `a7xx`, `a8xx` or `system` — overrides the Turnip build the compositor loads |

Session logs land in `/sdcard/Download/SteamDeck/`.

## Authors

- **The412Banner** — the app, the Wayland compositor, the runtime plumbing and the Steam session.
- **maxjivi05 (Max)** — the gamescope runtime this is built on: the proot session, the session
  shim, the fake-evdev interposer and the controller work, from WinNative.

## Lineage and licence

GPL-3.0. The gamescope runtime, the session shim and the fake-evdev interposer come from
maxjivi05's WinNative work and from Bannerlator, both GPL-3.0; the Wayland compositor and the
runtime plumbing are Bannerlator's. Valve, Steam and Proton are Valve Corporation's; this project
is not affiliated with Valve.
