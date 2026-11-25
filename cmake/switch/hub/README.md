# Switch Hub extension guide

## Build and launch

Use the repository's devkitPro build: configure with
`aarch64-none-elf-cmake -G Ninja -S cmake/switch -B build-switch` and build with
`cmake --build build-switch`. Copy all four NROs from its `artifacts/` to
`sdmc:/switch/` and open `AliceHub.nro` via hbmenu. If the loader does not
support `envSetNextLoad`, use the standalone engine NROs. The existing
`alicehub/<engine>/games` and `saves` paths are unchanged.

## In-game menu

Press **L + R** in a running game (**SL + SR** on a sideways Joy-Con, or
**F1** on a keyboard). Release the chord before navigating. Use the D-pad or
stick to move, **A** to select, and **B** to go back. The menu runs in each
engine NRO, including standalone launches; it is not a Hub screen.

Screenshots go to `sdmc:/switch/alicehub/<engine>/screenshots/`. System 1–3
uses the engine's read-text skip; System 3.5 and System 4 offer Ctrl skip.
Live modifiers appear for supported games only. System 4 stores text and
scrollbar scale with the current game's saves. Scaling affects screens
rendered by the Parts Engine, not legacy sprite text or custom scrollbars.

In the merged library, **X** opens the system keyboard to search game folder
names (ASCII case-insensitive; other characters match exactly). **B** clears
an active search, **A** opens the highlighted game's details, and **Y** opens
global Settings. Details can launch the game or show its game/save paths.
Settings changes the saved Name/Engine sort order or rescans game folders.

Open **Mods, translation and patches** from a game's details. Supported
titles are matched by their installed game files, not their folder names.
Enable **Online access** to browse and install the CDN releases. On the
mods or translation list, **X** installs a ZIP, RAR or 7z archive from SD;
**Y** removes a managed overlay and restores the original files. The
standalone uncensored CG patch is installed and removed independently.
Keep this screen open during the operation; **B** cancels before publication.
The SD card needs room for a staged copy of the game and the downloaded
overlay. The separate saves directory is never modified.

## Screens and actions

- Start from the foundation commit on PR #110; use a separate branch/worktree for
  each feature. Integrate back into PR #110 in order, rebasing/merging the active
  PR head into your feature branch as needed. Do not push directly to the shared
  PR branch from parallel worktrees unless the integrator owns that step.
- Add a named C file in this directory and one source in this directory's
  CMakeLists.txt. Expose one `hub_register_<feature>(HubApp *)`; call it in
  `features.c`. Use a namespaced literal screen ID and a static descriptor.
  Multiple related pages can be registered in the same call.
- Use `hub_push` and `hub_pop` for navigation. Each stack frame has its own
  focus and a copied optional game. A new focus is `(size_t)-1`; initialize it
  on the first draw. A default Back pops the stack; return true to consume it.
  Use the draw and input callbacks for UI, `hub_move_focus` for list/grid
  navigation (five rows or four cards), and `hub_heading`, `hub_hints`,
  `hub_list`, `hub_grid`, `hub_dialog` and `hub_toast` rather than drawing
  or presenting another framebuffer. Call `hub_wait_for_release` after
  returning from applets such as swkbd.
- `HUB_SECONDARY` routes the Y button for screens that opt in.
  `hub_hints_extra` can show that hint or omit unused paging hints;
  `hub_empty` shows a custom empty state. The existing widgets are unchanged.
- In the same feature registration function, call `hub_register_action` with
  a static entry targeting your screen ID: `HUB_DETAIL_ACTION` gets a game,
  `HUB_SETTING_ENTRY` gets no game. An optional availability callback is
  called with the game or NULL accordingly. Details and Settings enumerate these
  entries via `hub_action_entry`; no feature edits those screens. Use
  feature-owned versioned settings files for additional settings unless a
  cross-feature setting/schema change is agreed first.
  Use `hub_apply_settings` to persist the shared settings and apply their sort
  order; a failed save leaves the current library and settings unchanged.
- Read `hub_library` and store an instance ID or a game copy, not a pointer
  into the library across rescans. Local IDs are engine + SD folder name,
  scoped separately as game and instance IDs. A rename currently creates a
  new identity; catalog IDs/instance aliasing require a coordinated model change.
  Use `hub_game_path` and `hub_game_save_path`, and refresh on completed
  library changes. Keep staging outside the scanned directories.

## Work and tests

- Controller routing and framebuffer begin/end stay on the main thread in
  `app.c`. Keep callbacks non-blocking except short services/applets. Put worker
  routines and storage behind feature-specific files. Publish value snapshots
  via `hub_progress_publish`, read a copy via `hub_progress_snapshot`, and
  retire via `hub_progress_remove`. Do not call other Hub UI/model APIs from
  workers. Use feature-prefixed task IDs; the shared table accepts 16 entries.
  The feature owns cancellation, shutdown/join and resume records; don't
  rely on workers living across sleep or chainload.
- Run `python3 -m unittest discover -s xsystem4/test/native -p 'test_switch_hub.py'`
  from repo root. Add host tests to that suite or another clearly wired native
  suite. Model, storage, parser and worker policy should be buildable without
  libnx whenever possible. Verify controller interactions with the affected screen
  visible; do not use a build as a substitute for that check.
- Screens should not change the shared renderer, pad routing, engine launcher,
  SD handoff protocol, or paths without coordinating with the hub owner.
  Keep installers, sync, downloads and other unrelated features out of the
  shared foundation files.
