#ifndef ALICEHUB_INSTALL_TRANSACTION_H
#define ALICEHUB_INSTALL_TRANSACTION_H

#include "model.h"

typedef struct HubInstallTransaction HubInstallTransaction;
typedef bool (*HubInstallVerify)(const char *stage, void *context);

/* Call at startup before scanning the library. No transaction may be active. */
bool hub_install_recover(const char *root);

/* Serializes all game-tree replacements in the Hub. The stage starts empty.
 * For an overlay, copy the installed tree into the stage before applying files.
 * Do not stage saves: the separate save directory is never touched. */
HubInstallTransaction *hub_install_begin(const char *root, const HubGame *game);
const char *hub_install_stage(const HubInstallTransaction *transaction);
bool hub_install_relative_path(const char *path);
/* Creates any missing parent directories, not the file itself. */
bool hub_install_stage_file(HubInstallTransaction *, const char *relative,
                            char path[HUB_PATH_SIZE]);

/* Close all writing handles before verification and do not write afterwards.
 * The callback checks the staged content/boot files for the specific engine. */
bool hub_install_verify(HubInstallTransaction *, HubInstallVerify, void *context);
/* Success consumes the transaction; on failure call rollback. */
bool hub_install_commit(HubInstallTransaction *);
/* Always consumes the transaction. If false, leave recovery to the next run. */
bool hub_install_rollback(HubInstallTransaction *);

#endif
