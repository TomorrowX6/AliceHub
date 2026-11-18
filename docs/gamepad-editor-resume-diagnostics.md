# Editor-return black screen: unresolved investigation

A user reports that black screens still occur with PR15's retained-frame
re-presentation change. Three successful emulator title-screen cycles are **not**
a fix verification. Do not restart repeated GUI tests or download more game data
just to run these diagnostics.

## What source inspection establishes

- Current base is `origin/main` `37189eb`; the PR15 branch includes the settings
  switch and retained-frame changes. The editor is in the default process;
  `XSystem4Activity` is in `:game`. Saving/finishing the editor does not itself
  recreate the game or call SDL resume. The game reloads the profile in its
  activity result and resume callbacks.
- There is no separate modifier-menu pause counter/ownership in this path.
  SDL's actual pause/resume protocol is in `SDLActivity.handleNativeState` and
  SDL2's Android pause/resume semaphores. On API24+, this Java version pauses on
  **onStop**, resumes on **onStart**, and gates native resume on surface readiness,
  window focus and the resumed-called flag. onPause/onResume alone are not proof
  that the render thread resumed.
- Both native input event loops handle foreground/window events. The normal
  `handle_events` loop already re-presented on `SDL_APP_DIDENTERFOREGROUND` before
  PR15. The limited `handle_window_events` loop did not. Thus adding RESTORED
  presentation is defensive coverage, not evidence for the user's root cause.
- SDL2 2.30.9's `android_egl_context_restore` attempts to rebind its saved context.
  On failure it creates a **replacement** context and emits
  `SDL_RENDER_DEVICE_RESET`. Neither application input loop rebuilt engine GL
  textures, FBOs or programs for that event. Re-presenting old object IDs cannot
  repair this case. This is a real unsupported path, **not yet evidence that the
  affected phone took it**.
- SDL's UI-thread surface-destroy callback waits up to roughly 500ms for the
  render thread to back up/unbind its context, then proceeds with an SDL error
  if that has not happened. Render-thread stalls, slow polling or a GPU swap
  stall are competing possibilities; a video cannot distinguish them.
- Native gamepad config/clear calls use `gp_mutex`; the call sites do not hold
  that mutex across SDL lifecycle callbacks. No proven pause-count leak or
  lock-order cycle has been found. Do not infer that Android UI or native threads
  are responsive merely from a visible floating icon or system touch indicator.

## What this diagnostic change does (not a fix)

Opt-in tag `AliceHubResume` records:

- Editor launch/save/finish, activity result, profile application begin/end;
- SDL state, pending state, focus, resumed flag, surface readiness/validity and
  Java thread state at lifecycle/native transition/surface callback boundaries;
- Virtual-pad enabled/movie/physical-hide/edit flags and control count (no
  bindings, profile paths or save contents);
- Native background/foreground/focus/RESTORED and device/target reset events;
- Render-thread current-vs-original context, texture/program/FBO validity,
  viewport, renderer name and begin/end of at most two ensuing swaps per event.

Java snapshots make no JNI/GL calls. GL queries run only on the render thread
and only when enabled. No pixel readback, GPU finish, error-state clearing,
extra swap, delayed invalidation, forced context recreation, or game restart is
introduced. `sdl_error_may_be_stale` is explicitly **not** a fresh swap result.
These hooks can perturb timing, so failure to reproduce with tracing enabled is
not a clean bill of health.

The original RESTORED patch remains: its retained-context drawable regression
still passes. A separate negative control creates an unshared replacement
context and proves the old main texture is absent there. It neither simulates
an actual phone driver reset nor implements resource recovery. Revert or expand
the production patch only when evidence warrants it; no history is rewritten.

## One affected-phone capture, when the user is ready

Install an APK containing this instrumentation **as an update**, without
uninstalling or clearing data. If signing/version checks reject the update,
stop; do not work around them by removing the app. A same-signature diagnostic
build is required to update in that case. The same capture commands on the
existing APK can still collect SDL lifecycle logs, but will not include the new
`AliceHubResume` state/object diagnostics.

With USB debugging authorized, start before opening the editor:

```sh
adb shell setprop log.tag.AliceHubResume DEBUG
adb shell setprop log.tag.SDL VERBOSE
adb logcat -v threadtime -s AliceHubResume:D SDL:V AndroidRuntime:E > AliceHub-black-screen.txt
```

Use the app normally. After a black screen occurs, leave it visible briefly,
then stop **the logcat command** with Ctrl+C; do not force-stop/relaunch the game
before collecting it. Supply that text file plus phone model, Android version,
app version, game name, and whether Save or Cancel/Back returned to the black
screen. Do not send game data, accounts, or a whole-device bug report by default.
The selected tags reduce unrelated logs; review the file before sharing.

Disable tracing afterwards:

```sh
adb shell setprop log.tag.AliceHubResume INFO
adb shell setprop log.tag.SDL INFO
```

### Reading the result

- No `nativeResume.end`: native resume call stalled. A pending RESUMED state
  with ready/focus/resumed false instead identifies a Java gate not yet met.
- Surface `.begin` without `.end`, or ~500ms delay plus the SDL surface-release
  error: investigate surface teardown/context-backup ordering, not blind redraw.
- `device_reset.resources_not_rebuilt`, changed context or invalid GL objects:
  investigate full context-loss handling; retained-frame presentation is not a
  solution. A reset flag alone does not identify which game resources need
  reconstruction.
- `swap.begin` with no `swap.end`: investigate SDL/EGL/GPU blocking. Completed
  swaps with valid objects require further content/FBO/scene evidence, not an
  assumption that EGL restoration failed.
- Profile `.begin` without `.end`: investigate config/JNI/input synchronization.
  Virtual-pad visibility fields help distinguish overlays from the game surface.

A user's failure log is needed to choose among these branches. Do not describe
this instrumentation or its host regressions as a verified gameplay fix.
