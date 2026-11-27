#include "install_transaction.h"
#include "fs_util.h"

#include <dirent.h>
#include <errno.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#ifdef __SWITCH__
#include <switch.h>
#endif

#define INSTALL_DIR ".install-v1"

struct HubInstallTransaction {
    char target[HUB_PATH_SIZE], work[HUB_PATH_SIZE];
    char stage[HUB_PATH_SIZE], backup[HUB_PATH_SIZE], publishing[HUB_PATH_SIZE];
    char replacing[HUB_PATH_SIZE];
    bool verified, backed_up, promoted, publishing_new, replacing_copy;
};

static atomic_flag lock = ATOMIC_FLAG_INIT;

static bool join(char out[HUB_PATH_SIZE], const char *base, const char *part)
{
    return (size_t)snprintf(out, HUB_PATH_SIZE, "%s/%s", base, part) < HUB_PATH_SIZE;
}

static bool paths(HubInstallTransaction *tx, const char *root, const HubGame *game)
{
    char engine[HUB_PATH_SIZE], installs[HUB_PATH_SIZE];
    return hub_game_path(root, game, tx->target) &&
           join(engine, root, hub_engines[game->engine].id) &&
           join(installs, engine, INSTALL_DIR) && join(tx->work, installs, game->name) &&
           join(tx->stage, tx->work, "stage") && join(tx->backup, tx->work, "backup") &&
           join(tx->publishing, tx->work, "publishing") &&
           join(tx->replacing, tx->work, "replacing");
}

static bool commit_device(void)
{
#ifdef __SWITCH__
    if (R_FAILED(fsdevCommitDevice("sdmc"))) {
        errno = EIO;
        return false;
    }
#endif
    return true;
}

static bool stat_optional(const char *path, struct stat *st)
{
    if (!lstat(path, st))
        return true;
    if (errno != ENOENT)
        return false;
    st->st_mode = 0;
    return true;
}

static bool directory(const char *path, bool *exists)
{
    struct stat st;
    if (!stat_optional(path, &st))
        return false;
    *exists = S_ISDIR(st.st_mode);
    if (st.st_mode && !*exists) {
        errno = ENOTDIR;
        return false;
    }
    return true;
}

static bool mkdir_ok(const char *path)
{
    bool exists;
    return directory(path, &exists) && (exists || !mkdir(path, 0777));
}

static bool mkdir_parents(char *path)
{
    for (char *p = strchr(path, '/'); p; p = strchr(p + 1, '/')) {
        if (p == path || p[-1] == ':')
            continue;
        *p = '\0';
        bool ok = mkdir_ok(path);
        *p = '/';
        if (!ok)
            return false;
    }
    return true;
}

static bool dot_entry(const char *name)
{
    return !strcmp(name, ".") || !strcmp(name, "..");
}

static bool tree(const char *path, bool remove)
{
    struct stat st;
    if (!stat_optional(path, &st))
        return false;
    if (!st.st_mode)
        return remove;
    if (!S_ISDIR(st.st_mode)) {
        if (remove)
            return !unlink(path);
        if (S_ISREG(st.st_mode))
            return true;
        errno = EINVAL;
        return false;
    }
    DIR *dir = opendir(path);
    if (!dir)
        return false;
    bool ok = true;
    for (;;) {
        errno = 0;
        const struct dirent *entry = readdir(dir);
        if (!entry) {
            ok = !errno;
            break;
        }
        if (dot_entry(entry->d_name))
            continue;
        char child[HUB_PATH_SIZE];
        if (!join(child, path, entry->d_name) || !tree(child, remove)) {
            ok = false;
            break;
        }
    }
    closedir(dir);
    return ok && (!remove || !rmdir(path));
}

static bool create_marker(const char *path)
{
    FILE *marker = fopen(path, "wbx");
    return marker && !fclose(marker);
}

static bool restore_copy(const HubInstallTransaction *tx)
{
    bool backup, target;
    struct stat marker;
    return directory(tx->backup, &backup) && backup && tree(tx->backup, false) &&
           stat_optional(tx->replacing, &marker) &&
           (marker.st_mode ? S_ISREG(marker.st_mode) : create_marker(tx->replacing)) &&
           commit_device() &&
           directory(tx->target, &target) && (!target || tree(tx->target, true)) &&
           !hub_copy_dir(tx->backup, tx->target) && commit_device() &&
           !unlink(tx->replacing) && commit_device();
}

static bool recover_one(const HubInstallTransaction *tx)
{
    bool work, backup, target;
    if (!directory(tx->work, &work) || !directory(tx->backup, &backup))
        return false;
    if (!work)
        return true;
    struct stat journal, replacing;
    if (!stat_optional(tx->publishing, &journal) || !stat_optional(tx->replacing, &replacing))
        return false;
    if (replacing.st_mode)
        return !journal.st_mode && S_ISREG(replacing.st_mode) && restore_copy(tx) &&
               tree(tx->work, true) && commit_device();
    if (journal.st_mode && (backup || !S_ISREG(journal.st_mode) ||
        !directory(tx->target, &target) || (target && !tree(tx->target, true)) || !commit_device()))
        return false;
    if (backup) {
        if (!directory(tx->target, &target))
            return false;
        if (!target && (rename(tx->backup, tx->target) || !commit_device()))
            return false;
    }
    return tree(tx->work, true) && commit_device();
}

static bool recover_orphaned_copies(const char *folder)
{
    bool exists;
    if (!directory(folder, &exists))
        return false;
    if (!exists)
        return true;
    DIR *dir = opendir(folder);
    if (!dir)
        return false;
    bool ok = true;
    for (;;) {
        errno = 0;
        const struct dirent *entry = readdir(dir);
        if (!entry) {
            ok = !errno && ok;
            break;
        }
        const char *name = entry->d_name;
        if (strncmp(name, ".hub-copy-", 10) != 0 || !name[10] ||
            strspn(name + 10, "0123456789") != strlen(name + 10))
            continue;
        char child[HUB_PATH_SIZE];
        if (!join(child, folder, name) || !tree(child, true) || !commit_device())
            ok = false;
    }
    closedir(dir);
    return ok;
}

static bool recover_engine(const char *root, HubEngine engine)
{
    char folder[HUB_PATH_SIZE], installs[HUB_PATH_SIZE];
    bool exists;
    if (!join(folder, root, hub_engines[engine].id) || !join(installs, folder, INSTALL_DIR))
        return false;
    bool copies_ok = recover_orphaned_copies(folder);
    if (!directory(installs, &exists))
        return false;
    if (!exists)
        return copies_ok;
    DIR *dir = opendir(installs);
    if (!dir)
        return false;
    bool ok = copies_ok;
    for (;;) {
        errno = 0;
        const struct dirent *entry = readdir(dir);
        if (!entry) {
            ok = !errno && ok;
            break;
        }
        if (dot_entry(entry->d_name))
            continue;
        HubGame game;
        HubInstallTransaction tx;
        if (!hub_install_relative_path(entry->d_name) || !hub_game_init(&game, engine, entry->d_name) ||
            !paths(&tx, root, &game) || !recover_one(&tx))
            ok = false;
    }
    closedir(dir);
    return ok;
}

bool hub_install_recover(const char *root)
{
    if (atomic_flag_test_and_set(&lock)) {
        errno = EBUSY;
        return false;
    }
    bool ok = true;
    for (HubEngine engine = 0; engine < HUB_ENGINE_COUNT; ++engine)
        if (!recover_engine(root, engine))
            ok = false;
    atomic_flag_clear(&lock);
    return ok;
}

bool hub_install_pending(const char *root, const HubGame *game)
{
    HubInstallTransaction tx;
    struct stat st;
    if (!paths(&tx, root, game))
        return false;
    return !lstat(tx.work, &st) || errno != ENOENT;
}

static void release(HubInstallTransaction *tx)
{
    free(tx);
    atomic_flag_clear(&lock);
}

bool hub_install_relative_path(const char *path)
{
    const char *start = path;
    for (const char *p = path;; ++p) {
        if (!*p || *p == '/') {
            if (p == start || p[-1] == '.' || p[-1] == ' ' || p - start > 255)
                return false;
            if (!*p)
                return true;
            start = p + 1;
        } else if ((unsigned char)*p < 32 || *p == 127 || strchr("\\:*?\"<>|", *p)) {
            return false;
        }
    }
}

HubInstallTransaction *hub_install_begin(const char *root, const HubGame *game)
{
    HubGame validated;
    if (!memchr(game->name, 0, sizeof(game->name)) || !hub_install_relative_path(game->name) ||
        !hub_game_init(&validated, game->engine, game->name)) {
        errno = EINVAL;
        return NULL;
    }
    if (atomic_flag_test_and_set(&lock)) {
        errno = EBUSY;
        return NULL;
    }
    HubInstallTransaction *tx = calloc(1, sizeof(*tx));
    bool exists;
    if (!tx || !paths(tx, root, &validated) || !mkdir_parents(tx->target) ||
        !directory(tx->target, &exists) || !recover_one(tx) ||
        !mkdir_parents(tx->stage) || !mkdir_ok(tx->stage)) {
        release(tx);
        return NULL;
    }
    return tx;
}

const char *hub_install_stage(const HubInstallTransaction *tx)
{
    return tx->stage;
}

bool hub_install_stage_file(HubInstallTransaction *tx, const char *relative,
                            char path[HUB_PATH_SIZE])
{
    struct stat st;
    tx->verified = false;
    return hub_install_relative_path(relative) && join(path, tx->stage, relative) &&
           mkdir_parents(path) && stat_optional(path, &st) &&
           (!st.st_mode || S_ISREG(st.st_mode));
}

bool hub_install_verify(HubInstallTransaction *tx, HubInstallVerify verify, void *context)
{
    bool exists;
    tx->verified = verify && directory(tx->stage, &exists) && exists &&
                   tree(tx->stage, false) && verify(tx->stage, context);
    return tx->verified;
}

static bool commit(HubInstallTransaction *tx, bool replace)
{
    if (!tx->verified || tx->backed_up || tx->promoted || tx->publishing_new || tx->replacing_copy) {
        errno = EINVAL;
        return false;
    }
    bool exists;
    if (!directory(tx->target, &exists) || !commit_device())
        return false;
    if (exists && !replace) {
        errno = EEXIST;
        return false;
    }
    if (exists) {
        if (!rename(tx->target, tx->backup)) {
            tx->backed_up = true;
            if (!commit_device() || rename(tx->stage, tx->target))
                return false;
            tx->promoted = true;
            if (!commit_device())
                return false;
        } else {
            if (hub_copy_dir(tx->target, tx->backup) || !commit_device())
                return false;
            if (!create_marker(tx->replacing))
                return false;
            tx->replacing_copy = true;
            if (!commit_device() || !tree(tx->target, true) ||
                hub_copy_dir(tx->stage, tx->target) || !commit_device() ||
                unlink(tx->replacing) || !commit_device())
                return false;
            tx->replacing_copy = false;
        }
    } else {
        struct stat st;
        if (!lstat(tx->target, &st)) {
            errno = EEXIST;
            return false;
        }
        if (errno != ENOENT)
            return false;
        FILE *journal = fopen(tx->publishing, "wbx");
        if (!journal) return false;
        if (fclose(journal) || !commit_device()) {
            int err = errno;
            unlink(tx->publishing);
            commit_device();
            errno = err;
            return false;
        }
        tx->publishing_new = true;
        if (hub_rename_dir(tx->stage, tx->target)) {
            int err = errno;
            if (err == EEXIST) {
                tx->publishing_new = false;
                unlink(tx->publishing);
                commit_device();
            }
            errno = err;
            return false;
        }
        if (!commit_device() || unlink(tx->publishing) || !commit_device())
            return false;
        tx->publishing_new = false;
    }
    if (tree(tx->work, true))
        commit_device();
    release(tx);
    return true;
}

bool hub_install_commit(HubInstallTransaction *tx)
{
    return commit(tx, true);
}

bool hub_install_commit_new(HubInstallTransaction *tx)
{
    return commit(tx, false);
}

bool hub_install_rollback(HubInstallTransaction *tx)
{
    bool ok = (!tx->replacing_copy || restore_copy(tx)) &&
              (!tx->publishing_new || tree(tx->target, true)) &&
              (!tx->promoted || !rename(tx->target, tx->stage)) &&
              (!tx->backed_up || !rename(tx->backup, tx->target)) &&
              commit_device() && tree(tx->work, true) && commit_device();
    release(tx);
    return ok;
}
