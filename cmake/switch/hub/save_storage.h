#ifndef ALICEHUB_HUB_SAVE_STORAGE_H
#define ALICEHUB_HUB_SAVE_STORAGE_H

#include "model.h"

#define HUB_SAVE_FILE_SIZE (2 * HUB_PATH_SIZE)

typedef char HubSavePath[HUB_SAVE_FILE_SIZE];

typedef struct {
    size_t count;
    HubSavePath *paths;
} HubSaveSlots;

typedef enum { HUB_SAVE_EDIT_BACKUP, HUB_SAVE_UNLOCK_BACKUP } HubSaveBackup;

bool hub_save_join(HubSavePath path, const char *dir, const char *name);
bool hub_save_supported(const char *game_dir);
bool hub_save_scan(const char *save_dir, HubSaveSlots *slots);
bool hub_save_global_paths(const char *dir, HubSavePath global, HubSavePath collection);
bool hub_save_has_backup(const char *path, HubSaveBackup kind);
bool hub_save_restore(const char *path, HubSaveBackup kind);
bool hub_save_has_set_backup(const char *dir, HubSaveBackup kind);
bool hub_save_restore_set(const char *dir, HubSaveBackup kind);
bool hub_save_install_unlock(const char *dir, const char *assets);

#endif
