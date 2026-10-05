# Albion launcher ICU compatibility

Albion's Windows launcher can fail before showing a window on Proton Experimental
(ARM64) when Qt6Core imports `icuuc.dll` and the Proton build only supplies
`icu.dll` and versioned ICU libraries. On the Xiaomi M332BF, the failing launch
logged `Library icuuc.dll ... Qt6Core.dll ... not found`.

`droiddeck-game-env` calls `droiddeck-icu` after esync selects the actual Proton
directory. For an x64 `AlbionLauncher.exe`, the helper reads Qt6Core's ICU imports
and checks them against that Proton's `icu.dll` exports. If the game and Proton
both lack `icuuc.dll`, it writes a code-free x64 PE DLL beside the launcher that
forwards those imports to `icu.dll`. No downloaded Windows DLL or compiler is
required. Existing game DLLs are never replaced; missing target exports leave
the launch unchanged with a diagnostic. The helper is staged from the APK at
each session start, including for existing runtime installations.

Run `python3 -m unittest tools.tests.test_icu tools.tests.test_game_environment
tools.tests.test_session_assets` on Linux. On a device, back up any manually
installed launcher `icuuc.dll`, launch Albion using the ARM64 compatibility tool,
and verify that the session logs report `droiddeck-icu: supplied icuuc.dll` and
the launcher opens. Relaunch to verify the existing DLL is preserved.

Device verification on 2026-10-05: backed up the manually installed DLL, staged
the project helpers into the running session, and launched Albion from Steam.
Session `2026-10-05-04-steam` reported 20 ICU exports supplied; the user confirmed
the launcher opened. The 2048-byte DLL read back from the phone exactly matched
the project generator (SHA256
`25c42d851d638155e19c091614d0892da1a19660a1bded73e6f0ba338e9119fb`).
The 38 focused Python tests passed. An APK build was not completed because the
local NDK 27.3.13750724 installation lacks `source.properties`; this device pass
used the existing APK with the new helpers staged manually.

The launcher's tiled background is a separate issue: disabling **Force game
windows fullscreen** lets its fixed 1024x610 layout display correctly. This ICU
repair does not change fullscreen settings or game rendering.
