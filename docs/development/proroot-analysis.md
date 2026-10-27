# proroot: what it does and what DroidDeck can take from it

Static analysis of [coderredlab/proroot](https://github.com/coderredlab/proroot) v1.2.8
(`c01039ab`, 2026-06-17). The repository holds only five arm64 binaries and a README. The licence is
proprietary, and redistributing modified binaries is not allowed, so only the ideas below are
usable, not the code. The binaries were read with the NDK's `llvm-readelf`, `llvm-nm`,
`llvm-objdump` and `llvm-strings`. `libproroot-stub-loader.so` and `libproroot-bridge.so` still carry
`.symtab`, and `libproroot-runtime.so` exports about 260 names, so the function names below are
proroot's own.

## Architecture

proroot does not use ptrace. Everything runs inside the guest process.

| file | kind | role |
|---|---|---|
| `libproroot.so` | bionic PIE executable | Launcher. Parses `-r/-b/-w/-0/--link2symlink`, writes a config file (`.proroot-config-<pid>`, passed down as `PROROOT_CFG_FD`), and execs the guest through `libproroot-linker.so` or the stub loader. |
| `libproroot-linker.so` | freestanding PIE, run as an executable | A from-scratch glibc dynamic linker ("cleanroom linker") that replaces `ld-linux-aarch64.so.1`. It maps the program and its `DT_NEEDED` libraries, relocates them, sets up static TLS, and stubs `_dl_*` and `__tunable_get_val`. It finds `_rtld_global_ro` through a table of offsets per glibc version ("no offset table for glibc X, patches skipped"). It also installs a SIGSYS handler with an allow-list. |
| `libproroot-stub-loader.so` | freestanding static PIE | Userland `exec`. It maps the target and its `PT_INTERP` itself and builds the stack and auxv. It then **rewrites the `svc #0` sites** in the loaded images (`proroot_svc_scan`, `proroot_svc_patch_apply`, `select_trampoline_for_site`) to branch into per-syscall trampolines (`proroot_tramp_openat`, `_statx`, `_faccessat2`, `_clone3`, `_rt_sigaction`, `_shmget`, ...). Sites it cannot patch fall back to an in-process seccomp `RET_TRAP` filter with a SIGSYS handler ("trampoline fallback, activating trap-mode fallback"). |
| `libproroot-runtime.so` | glibc `LD_PRELOAD` | Hooks about 200 libc entry points: path translation, fake root (`fake_id0_*`), link2symlink, exec routing, SysV shm, netlink, `/proc` and `/sys` views, `brk`, a fake DRM/KMS and GBM device plus libseat for compositors, X11 and Unix-socket fixups. After load it **patches the svc sites in glibc itself** (`patch_libc_syscalls`). |
| `libproroot-bridge.so` | static `ET_EXEC` at `0x7000000000` | The "trampoline" for static guest binaries. It installs a path-trap seccomp filter plus a SIGSYS handler, maps the target, and serves a virtual `/proc` (`status`, `stat`, `cmdline`, `environ`, `mounts`, `mountinfo`, `fdinfo`, `auxv`). |

The executable files all live in `nativeLibraryDir`, which is the only place an app may exec from.
That is why the dynamic linker and the static loader each have their own userland loader. proot
handles the same constraint with `libproot-loader.so`.

## How the in-process syscall interception works

1. **Patching svc sites in glibc** (`patch_phdr_callback`, called through `dl_iterate_phdr`):
   - It scans only an allow-list of objects: `libc.so.6`/`libc-`, `ld-linux`, `libpthread`,
     `librt`, `libdl`. Main executables get patched early only for listed programs (apt, dpkg,
     curl, openssl, node, chromium/electron/playwright, passwd/useradd/...). Other programs are
     patched later (`log_deferred_libc_patch`, `PROROOT_FORCE_EARLY_PATCH`).
   - In each executable `PT_LOAD` it finds `svc #0` (`0xd4000001`). It checks the instructions
     before it for a `mov x8, #nr` it recognises. One fixed glibc `openat` call sequence gets
     special treatment: its `bl` is redirected to a relay (`emit_openat_call_relay_entry`).
   - The site is replaced with a `b`/`bl` to a relay stub on a page allocated within ±128 MiB
     (`alloc_relay_page`). Sites out of range are logged as `bl-out-of-range`; `large-image
     multi-cover tramp` places extra relay pages for big images.
   - **Write path:** it first tries `mprotect(PROT_READ|PROT_WRITE|PROT_EXEC)` on the segment.
     If that is refused, it `pwrite`s the 4-byte instruction through `/proc/self/mem`. Either way
     it then calls `__clear_cache`. The log line counts sites by kind: `patched %d seccomp + %d
     path (%d relay) + %d brk`.
2. **In-process SIGSYS fallback**:
   - A BPF filter allows syscalls whose instruction pointer is inside proroot's own code and
     returns `SECCOMP_RET_TRAP` for the path, exec, clone and wait syscalls from anywhere else.
     The IP check is the same idea as our patch 0014, with TRAP instead of TRACE.
   - The handler emulates the call and writes the result into the signal frame. Guest SIGSYS
     handlers are kept and chained (`proroot_record_user_sigsys`, `invoke_user_sigsys`).
3. **Keeping SIGSYS deliverable**, the objection in option 4 of
   [proot-performance](proot-performance.md):
   - The `sigprocmask` and `pthread_sigmask` hooks call `sigtrap_unblocked_mask`, which
     `sigdelset`s `SIGSYS` (and `SIGTRAP` for some guests) from any `SIG_BLOCK`/`SIG_SETMASK` set.
   - `rt_sigaction` is guarded so the guest cannot replace the handler (`proroot_tramp_rt_sigaction`,
     the `sigaction_guard` trace points).
   - The raw `rt_sigprocmask` that glibc makes internally, for example `pthread_create` blocking
     every signal, goes through a patched svc site. The stub loader and the runtime both compare
     against syscall 135.
4. **Android-blocked syscalls are answered in-process.** The stub loader dispatches
   `set_robust_list`, `rseq`, `faccessat2`, `clone3`, `futex_waitv` and `seccomp`. The runtime
   emulates SysV shm (`emu_shm*`), `set*id`/`get*id` under `-0`, and `brk`
   (`emulate_guest_brk`, over a reserved mapping).

## Compared with DroidDeck

| | DroidDeck (proot + 0001–0018 + `libblfastpath.so`) | proroot |
|---|---|---|
| exec, `PT_INTERP`, `/proc/self/exe` | proot's loader under ptrace, 0.4–0.8 ms per exec | Its own loaders, with no tracer; also needs its own ld.so with a glibc offset table |
| path calls through libc wrappers | fast path, about 0.6–1.9 µs | runtime hooks |
| path calls from glibc internals (ld.so, fts, realpath, nss, `__open_nocancel`) and raw `svc` | **still go to proot (15–60 µs)** | **patched svc sites** |
| Android-blocked syscalls (`set_robust_list`, `faccessat2`, `clone3`, ...) | SIGSYS stop answered by proot (0016 etc.), one ptrace round trip and one signal round trip each | in-process |
| `brk` | proot emulation, a trap per call (mitigated by `top_pad`) | in-process emulation |
| correctness fallback | proot is always there | none: anything missed runs unconfined or is emulated wrongly |
| long Unix socket paths | fast path `/proc/self/fd/N/name` aliases | short socket aliases (`proroot_create_short_unix_socket_alias`) |
| rseq | `GLIBC_TUNABLES=glibc.pthread.rseq=0` | trampoline |

proroot is not a model for replacing proot. It has no tracer to fall back on, so a missed call
site or an unexpected glibc build silently breaks isolation or correctness. Its own linker depends
on glibc internals for each version. Proton, Wine, FEX and Steam's self-updating ELFs are a much
wider surface than the Node, Python and Chromium workloads proroot was tested on. Its value for
DroidDeck is as a fast path *in front of* proot.

## What is worth taking

1. **Patch glibc's svc sites from `libblfastpath.so`'s constructor.** This is the largest gain
   and the cheapest way to do option 6 in [proot-performance](proot-performance.md) ("a proot-aware
   glibc") without rebuilding glibc.
   - For `libc.so.6` (and `ld-linux-aarch64.so.1` for runtime `dlopen`), rewrite each `svc #0`
     that follows a recognised `mov x8, #nr` for the path syscalls the fast path already handles.
     The rewrite is a `b` to a relay that calls the existing translator.
   - The relay issues the syscall from the `0xffff00000` trampoline page, which 0014 already lets
     through. Anything the translator declines is re-issued from an svc in the relay page, outside
     the trampoline, so proot traps it exactly as it does today. Correctness still rests on proot.
   - This covers fts (`find` over 2.7k files, unchanged today), realpath, nss, `__open_nocancel`,
     and `dlopen` after start-up.
   - Library loading at start-up, before the constructor runs, still traps.
2. **Answer the Android-blocked syscalls through those relays.**
   - `set_robust_list` and `get_robust_list`: keep the head in TLS, as 0016 already does in
     proot.
   - `faccessat2`: answer `ENOSYS`, or translate it when flags are 0, as the wrapper does.
   - `clone3`: answer `ENOSYS` before Android 15, so glibc falls back to `clone`.
   - `futex_waitv`: droiddeck-fsync already handles this.
   - Each one removes a SIGSYS stop plus a ptrace round trip, about 30–50 µs. `set_robust_list`
     alone is one per thread, which matters for Steam/CEF and Wine thread churn.
3. **The `/proc/self/mem` write as a fallback for patching.** If the app domain refuses
   `mprotect(PROT_WRITE|PROT_EXEC)` on rootfs (`app_data_file`) text, i.e. an `execmod` denial,
   a write through `/proc/self/mem` makes the page copy-on-write without an `mprotect`. That page
   is never writable and executable at once. Check this on the Thor before relying on it. The
   same question is still open for the trampoline page (`execmem`).
4. **Keep SIGSYS unblocked if in-process `SECCOMP_RET_TRAP` is ever used.** Strip `SIGSYS` in the
   `sigprocmask`/`pthread_sigmask`/`rt_sigprocmask` paths, and keep the handler installed while
   chaining the guest's. This only becomes safe once item 1 covers glibc's internal
   `rt_sigprocmask`. It is a prerequisite, not a recommendation to switch.
5. **Cache patch sites by build-id.** proroot rescans each image on every exec and defers patching
   for most programs to keep exec cheap. A scan of libc's roughly 1.5 MiB of text is cheap, but a
   per-build-id site table, written once in `/tmp` by the fast path, would make it free.

Not worth taking: the cleanroom ld.so and its glibc offset table; the userland exec loader (proot
already does this); the fake DRM/GBM/libseat device (DroidDeck has its own compositor path);
netlink and SysV shm (`netif.c` and `sysv.c` already cover these); and the long-socket aliasing
(the fast path covers it).

## Open questions before prototyping item 1

- Does the app domain allow `mprotect(RWX)` on a private mapping of an `app_data_file`? Does a
  `/proc/self/mem` write work under the app's SELinux context and proot's own `ptrace` attach?
  `/proc/self/mem` writes need `PTRACE_MODE_ATTACH` on self, which is always allowed.
- How many `libc.so.6` path syscall sites fall outside a recognisable `mov x8, #nr` window? For
  example, `syscall()` takes the number in a register, which needs the existing `syscall()` hook.
- Patched pages become private copies, about 4 KiB per patched page per process. Measure RSS
  across the Steam/CEF tree.
- Equivalence: run `bench/equiv.py` plus a `find`/`realpath`/nss workload with `PROOT_FP_STATS`.
  Compare the trap counts from patch 0018 before and after.

## First implementation and Thor validation (2026-10-27)

`tools/proot/fastpath/svc-patch.h`, included by `fastpath.c`, implements the first
slice of item 1. It runs automatically in fresh processes using the replacement
`libblfastpath.so` when the ordinary fast-path configuration and tracer-key check
succeed. Set `PROOT_FP_PATCH_SVC=0` to disable it for troubleshooting or comparisons;
`PROOT_FP_PATCH_SVC=1` also explicitly enables it. It skips processes that already have multiple threads,
and declines SVE/SME hardware because the relay preserves the base ARM64 register state.

The scanner visits executable segments of the loaded `libc.so.6` and
`ld-linux-aarch64.so.1`. It only patches `openat` and `newfstatat` sites with an
unshifted `movz x8/w8, #nr` within the preceding eight instructions. Intervening
instructions must belong to a small allow-list of argument moves/sign-extension,
immediate moves, stack loads/stores and NOPs that leave x8 unchanged. Branches,
calls, x8 writes and unknown instructions stop recognition. This includes libc's
non-adjacent O_CREAT sequences. Register-number dispatchers and other syscall
numbers remain under PRoot. No glibc version offsets, proroot code, or modified
proroot binaries are used.

Each replacement is a four-byte `b` into a nearby relay. Relay pages are filled while
writable, then changed to RX before publishing any branch. Text writes use
`/proc/self/mem`; failure leaves the site unpatched. The relay preserves x1–x30, all
32 SIMD registers, NZCV, FPCR/FPSR and the caller's `errno`, returning a raw kernel-style
result in x0. It calls the existing path translator, using the existing allowed
trampoline for translated calls. Declined and recursive calls execute an ordinary
`svc` in this library, where PRoot still intercepts them. No SIGSYS handler or new
seccomp filter is installed. `PROOT_FP_STATS=1` adds `fastpath-svc` exit counters.

The connected AYN Thor was running runtime r9, glibc 2.43, with a 4 KiB page size in
`u:r:untrusted_app_27:s0`. `bench/svc-probe.c`, run through the app's guest bridge,
confirmed all of the following while PRoot was attached:

- An RX private mapping of the rootfs libc accepted `mprotect(RWX)`.
- `/proc/self/mem` accepted a changed byte in that RX mapping; readback and restoration
  succeeded, and the backing file stayed unchanged.
- An anonymous RW mapping could be changed to RX.
- libc contained 415 `svc #0` sites: 327 with an adjacent number load, 76 with a
  number load only within the preceding eight instructions, and 12 with neither.
  The linker contained 56 sites, 45 adjacent and 11 nearby-only. Nearby counts are
  scan statistics, not proof that the number remains live at the syscall.

The initial adjacent-only scanner patched nine sites; the final scanner patched
14, including file-creation paths, across libc and the linker. It used two relay
pages in standalone validation processes. `bench/svc-check.c` exercises the
actual emitted relay and checks raw success/error results, errno, x1–x30, q0–q31,
NZCV, FPCR/FPSR, four simultaneous threads, the multi-thread patching guard, and fork.
Null arguments and unsupported flags fall back to PRoot. The instruction decoder
and exact ±128 MiB branch limits also have a host regression test.

Default-policy validation on the Thor confirmed that an unset `PROOT_FP_PATCH_SVC`
and explicit `1` both patched 14 sites; explicit `0` left all 415 libc and 56 linker
syscall sites unchanged. The register/thread/fork check also passed with the
environment flag unset.

Five alternating pairs of `bench/svc-workload.c` runs compared the same replacement
library with patching off/on, with fast-path stats enabled in both. The workload
gets `fstatat` and `__open64_nocancel` directly from libc, bypassing the preload's
public wrappers, and makes 5,000 of each call against a prepared regular file:

| median | wrappers only | svc patching |
|---|---:|---:|
| internal `fstatat` | 38.6 µs | 3.0 µs |
| internal open + close | 39.3 µs | 5.8 µs |
| voluntary context switches over both loops | 10,000 | 0 |

### SD allocation and writes

`bench/svc-storage.c` separates the operations relevant to Steam downloads. Three
alternating pairs ran on internal `/tmp` and the selected `/mnt/droiddeck-sd` library,
with Steam idle and no simultaneous local Steam download. Each fresh directory held 64 long-named files
extended to 256 KiB, followed by 1,000 libc-internal stats and a 64 MiB sequential
write followed by `fdatasync`. Both modes used the same replacement library; only
`PROOT_FP_PATCH_SVC` changed. Medians on SD were:

| phase | wrappers only | svc patching |
|---|---:|---:|
| create/extend/close 64 files | 1.500 s | 1.486 s |
| 1,000 internal stats | 61.2 ms | 5.4 ms |
| 64 MiB write calls | 43.9 ms | 40.9 ms |
| final `fdatasync` | 2.514 s | 2.539 s |

Internal allocation fell from 7.1 ms to 1.9 ms; internal stats fell from 39.5 ms
to 2.5 ms. Internal `fdatasync` varied from 36 to 164 ms across individual runs.
The patched SD workload recorded 1,065 internal calls handled in-process, covering
64 file creations, 1,000 stats and the streaming-file creation. Per-phase medians
are independent and should not be added as a median whole-run duration.

The SD metadata gain does not establish faster Steam downloads. File creation
improved by only a few percent, and write/flush speed did not improve in this probe.
The remaining SD cost is much larger than the saved syscall-interception time.
The earlier [Thor SD investigation](thor-sd-storage-investigation.md) likewise
identified native exFAT metadata waits; the current benchmark does not independently
attribute those waits to a particular mount flag. A real Steam install comparison
must separate preallocation, transfer/staging and final commit before claiming an
end-to-end improvement.

### Real Steam downloads to SD

Four fresh installs of owned, initially uninstalled **7 Billion Humans** (792100)
used library folder 1, `/mnt/droiddeck-sd`, and were uninstalled between trials.
Each downloaded 228,935,584 bytes and staged 237,717,350 bytes in 19 files, ending
at BuildID 8016557, depots 792101/5052600457178655633 and
792104/8245917369753676198. Every trial logged zero reused/stored/delta bytes at
the start and completed with `No Error`. Existing installed games were retained.

The repeat off/on pair used the exact same replacement library, SHA-256
`ab605c021aee271cd49fa1cb337d7ff0c762dbac198b711e9818639c0cd4b17d`.
The first off run used the earlier adjacent-only build, with patching disabled.
Only the patching environment flag changed for the final pair. Times below come
from Steam's one-second-resolution `content_log.txt`; total starts at Reconfiguring
and ends at scheduler completion, excluding session startup:

| trial | patching | preallocate | download/stage | commit to completion | total |
|---|---|---:|---:|---:|---:|
| first baseline | off | 1 s | 123 s | 1 s | 135 s |
| first patched | on | <1 s | 47 s | 1 s | 50 s |
| repeat baseline | off | <1 s | 30 s | 1 s | 33 s |
| repeat patched | on | <1 s | 29 s | 2 s | 32 s |

Reconfiguration accounts for the remaining total time. The first baseline
overlapped another Steam client's network download; that client's telemetry must
not be mistaken for the Thor's. Later samples were filtered to local client `0`.
The repeat pair reached similar network rates, roughly 7–8 MB/s while transferring.
All four attempts logged the same unsupported large-file preallocation retry.

**This does not demonstrate a useful SD-download speedup.** The apparent initial
135→50 second gain disappeared when baseline was repeated. The final 33 versus
32 seconds is within this small experiment's timing/network variability. This
19-file title also does not settle performance for games with thousands of loose
files. Metadata calls clearly improve, while SD creation/writeback remain the
stronger next targets. No network settings, SD mount flags or root privileges
were changed for these tests.

The internal-call equivalence workload, including fts, realpath, reentrant NSS,
runtime dlopen, missing paths and fallback errors, matched `PROOT_FP_OFF=1` at both
the default parent-cache TTL and TTL 0. The existing `equiv.py` also matched after
normalizing random/thermal read payloads to their lengths, volatile PID values in
the `/proc` directory listing, and tolerating Android's
denied `/dev` directory read. Its root fixtures were relocated into a temporary tree.

`equiv2.py` used temporary root/SD fixtures, normalized `/proc/<self-pid>` output,
and tolerated the SD filesystem's unsupported symlink creation. Its patched output
matched the wrapper-only output at both TTLs. **It did not fully match PRoot:**
binding a Unix socket in the selected SD bind returned EPERM with the existing
wrappers, whereas PRoot succeeded. This mismatch reproduced with svc patching off;
it is a separate existing fast-path socket issue. The suite is not a clean full
equivalence pass. `equiv3.py` was not run because it rewrites live NSS configuration.

A fresh Steam session using the override and `PROOT_FP_PATCH_SVC=1` rendered its
library UI. Sonic Mania (584400) gained focus and rendered its title screen. This
is startup smoke coverage, not gameplay or a Steam startup-speed measurement.
The installed PRoot pack emitted no profiling start/counter records despite the
queued profiling setting; the profiler report had `hasCounters: false`. That is a
measurement gap, not evidence of zero remaining traps.

Further validation includes signal/cancellation/unwind behavior,
additional glibc builds and workloads, startup cost and Steam/CEF tree RSS; obtain
matching patch-0018 tracer measurements; and resolve the existing SD socket mismatch.
Additional syscall families, Android-blocked syscall emulation and build-id caching
remain follow-ups. In particular this implementation does not remove startup loader
traps before the preload constructor, nor patch raw calls in arbitrary guest ELFs.

Build the tools with the same ARM64 glibc toolchain as the fast path:

```sh
aarch64-linux-gnu-gcc -O2 -Wall -Wextra -pthread \
  -o svc-check tools/proot/bench/svc-check.c -ldl
aarch64-linux-gnu-gcc -O2 -Wall -Wextra \
  -o svc-probe tools/proot/bench/svc-probe.c -ldl
aarch64-linux-gnu-gcc -O2 -Wall -Wextra \
  -o svc-workload tools/proot/bench/svc-workload.c -ldl
aarch64-linux-gnu-gcc -O2 -Wall -Wextra \
  -o svc-storage tools/proot/bench/svc-storage.c -ldl
```

Run `svc-probe` inside the app's guest environment, not from `adb shell`.
`svc-check` embeds the fast path and requires its normal enabled tracer environment.
For `svc-workload`, pass a prepared directory containing `file` and a `link` symlink
to it; add any second argument to enable timing. Use the documented library override
workflow in [proot-performance](proot-performance.md) for a fresh session. No extra
environment setting is needed; use `PROOT_FP_PATCH_SVC=0` for the unpatched comparison.
`svc-storage` takes a new, unused directory name; it creates and removes its own test
files and directory. It writes 64 MiB plus 16 MiB of file extensions per run.
