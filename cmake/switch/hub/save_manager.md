# Switch save manager

Open **Save manager** from a game's details, or choose a game from **Settings
→ Save manager**. Press **X** on the save manager screen to see that game's SD
folder: `sdmc:/switch/alicehub/save-manager/<engine>/<game>/`. The active
saves remain in the engine's existing `saves/<game>/` folder.

- **Prepare SD folders** creates `Import` and `Sync`. Put unpacked save
  files or subfolders in `Import`. If it contains a `SaveData` directory,
  that directory is used instead. **Import from SD** merges files into active
  saves; files not in the import are left alone.
- **Export original saves** writes an uncompressed `SaveData` directory.
  **Export PC-compatible saves** writes a separate `PC_SaveData` directory:
  X4SS struct saves become Windows GD saves, other files are copied unchanged,
  and `.bak` and `.log` files are omitted. Windows GD saves can be imported
  as-is; System 4 reads that format directly.
- **Create backup** makes a dated snapshot in `Backups`. **Restore latest
  backup** replaces active saves with the newest snapshot. The manager makes
  another snapshot before replacing existing active saves (including import,
  restore, or sync), so the previous state is recoverable. To restore an older
  snapshot, move newer snapshots out of `Backups` first.
- **Sync new files both ways** copies missing files between the active and
  `Sync` folders, without propagating deletions or changing differing
  copies. To resolve differences, **Pull from sync folder** uses the SD copy
  for matching paths; **Push to sync folder** uses the active copy.

Use extracted folders rather than archives. Transfer/export/sync ignore hidden
files from external folders; internal snapshots keep them. Backups and staged
operations require spare SD space. Remove old snapshots manually when needed.
