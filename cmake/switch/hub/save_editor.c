#include "hub.h"
#include "save_storage.h"
#include "cJSON.h"
#include "modifier_api.h"

#include <errno.h>
#include <math.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <switch.h>

#define SAVE_TASK "save-editor"
#define UNLOCK_ASSETS "romfs:/save-unlock"
#define ARRAY_COUNT(a) (sizeof(a) / sizeof(*(a)))

typedef enum { LOCAL, GLOBAL, COLLECTION, COMMON } Section;
typedef enum { INT, FLAG, DECIMAL, BULK } FieldType;
typedef struct {
    const char *key, *label;
    Section section;
    FieldType type;
    int min, max;
    const char *count_key;
} Field;

#define NUMBER(key, label, section) {#key, label, section, INT, INT32_MIN, INT32_MAX, NULL}
#define RANGE(key, label, section, min, max) {#key, label, section, INT, min, max, NULL}
#define TOGGLE(key, label, section) {#key, label, section, FLAG, 0, 1, NULL}
#define ALL(key, label, section, min, max, count) {#key, label, section, BULK, min, max, #count}

static const Field fields[] = {
    NUMBER(turn, "Current turn", LOCAL),
    NUMBER(chapter, "Chapter", LOCAL),
    RANGE(difficulty, "Difficulty", LOCAL, 0, 10),
    NUMBER(battleCount, "Battle count", LOCAL),
    NUMBER(foodTicket, "Food tickets", LOCAL),
    NUMBER(honor, "Medals", LOCAL),
    NUMBER(friendPoint, "Friend points", LOCAL),
    NUMBER(totalExp, "Total EXP", LOCAL),
    NUMBER(partyRank, "Party rank", LOCAL),
    NUMBER(itemStar, "Item star bonus", LOCAL),
    NUMBER(treasureNotGetCount, "Rare pity count", LOCAL),
    NUMBER(hannyZippo, "Zippo count", LOCAL),
    NUMBER(partyHp, "Party HP", LOCAL),
    NUMBER(partyMaxHp, "Max party HP", LOCAL),
    NUMBER(partyAvailCount, "Deploy slots", LOCAL),
    TOGGLE(partyIsFixStatus, "Lock party stats", LOCAL),
    NUMBER(partyAtk, "Party attack", LOCAL),
    NUMBER(atkPerPhys, "Physical attack bonus %", LOCAL),
    NUMBER(atkPerMag, "Magic attack bonus %", LOCAL),
    NUMBER(defPerPhys, "Physical defence bonus %", LOCAL),
    NUMBER(defPerMag, "Magic defence bonus %", LOCAL),
    NUMBER(perCrit, "Critical hit bonus %", LOCAL),
    NUMBER(perMulti, "Multi-hit bonus %", LOCAL),
    NUMBER(perExpUp, "EXP bonus %", LOCAL),
    NUMBER(perFindTreasure, "Treasure bonus %", LOCAL),
    NUMBER(humanCount, "Human population", LOCAL),
    NUMBER(humanDead, "Human casualties", LOCAL),
    NUMBER(curseTurn, "Curse turns", LOCAL),
    NUMBER(rareLuck, "Rare luck", LOCAL),
    NUMBER(seaPlayerArmy, "Sea fleet strength", LOCAL),
    NUMBER(leazasPArmy, "Leazas allied strength", LOCAL),
    NUMBER(helmanPArmy, "Helman allied strength", LOCAL),
    NUMBER(zethPArmy, "Zeth allied strength", LOCAL),
    NUMBER(freeCitiesPArmy, "Free Cities allied strength", LOCAL),
    NUMBER(leazasEArmy, "Leazas enemy strength", LOCAL),
    NUMBER(helmanEArmy, "Helman enemy strength", LOCAL),
    NUMBER(zethEArmy, "Zeth enemy strength", LOCAL),
    NUMBER(freeCitiesEArmy, "Free Cities enemy strength", LOCAL),
    ALL(setCardCount, "Set every card stack", LOCAL, 1, INT32_MAX, cardCount),
    ALL(cardAtkBonus, "Minimum card attack", LOCAL, 1, INT32_MAX, cardCount),
    ALL(cardHpBonus, "Minimum card HP", LOCAL, 1, INT32_MAX, cardCount),
    ALL(setCharStar, "Minimum character stars", LOCAL, 1, 5, charCount),
    ALL(setCharExp, "Set every character's EXP", LOCAL, 1, INT32_MAX, charCount),
    NUMBER(totalClearPoint, "Clear CP", COMMON),
    NUMBER(clearBPointMax, "Part 2 max CP", COMMON),
    NUMBER(AutoBattleMedal, "Auto battle medals", COMMON),
    NUMBER(AchievementPoint, "Achievement points", COMMON),
    NUMBER(TotalClearCount, "Total clears", COMMON),
    NUMBER(Clear_A_Count, "Rank A clears", COMMON),
    NUMBER(Clear_B_Count, "Rank B clears", COMMON),
    NUMBER(Clear_C_Count, "Rank C clears", COMMON),
    NUMBER(BestScore, "Best score", COMMON),
    NUMBER(LeaderSmallestTurn, "Demon lord minimum turn", COMMON),
    NUMBER(RealSmallestTurn, "Part 2 minimum turn", COMMON),
    TOGGLE(CollectionClearB, "Part 2 cleared", COMMON),
    TOGGLE(OpenMonsterpedia, "Monsterpedia unlocked", COMMON),
    TOGGLE(TrophyComplete, "All trophies complete", COMMON),
    TOGGLE(CardComplete, "All cards collected", COMMON),
    NUMBER(gamePlayCount, "Global play count", GLOBAL),
    NUMBER(itemStarRank, "Global item star rank", GLOBAL),
    ALL(setAllCharRank, "Set all character ranks", GLOBAL, 0, INT32_MAX, charRankCount),
    ALL(setTakeOverCardCount, "Set all carry-over card counts", GLOBAL, 0, INT32_MAX, takeOverCardCount),
    {"leaderAtkRatio", "Leader attack ratio", COLLECTION, DECIMAL, 0, INT32_MAX, NULL},
    NUMBER(trophyExpBonus, "Trophy EXP bonus %", COLLECTION),
    NUMBER(trophyStatusMaxPer, "Trophy stat cap %", COLLECTION),
};

typedef enum {
    SCAN, LOAD, APPLY, RESTORE_EDIT, INSTALL, RESTORE_UNLOCK
} SaveJob;

static struct {
    HubSavePath dir, game_dir, local;
    HubSaveSlots slots;
    bool supported, scan_ok, unlock_backup, edit_backup, global;
    bool busy, exit_locked;
    atomic_bool done;
    pthread_t thread;
    SaveJob job, confirmation;
    cJSON *summary, *pending;
    const char *notice;
} editor;

static void finish_worker(void)
{
    pthread_join(editor.thread, NULL);
    editor.busy = false;
    hub_progress_remove(SAVE_TASK);
    if (editor.exit_locked)
        appletUnlockExit();
}

static void applet_hook(AppletHookType hook, void *unused)
{
    (void)unused;
    if (hook == AppletHookType_OnExitRequest && editor.busy)
        finish_worker();
}

static const char *job_title(SaveJob job)
{
    switch (job) {
    case SCAN: return "Finding saves";
    case LOAD: return "Reading save";
    case APPLY: return "Saving changes";
    case RESTORE_EDIT: return "Restoring previous save";
    case INSTALL: return "Installing full unlock";
    case RESTORE_UNLOCK: return "Restoring pre-unlock saves";
    }
    return "Working";
}

static bool has_value(const Field *field)
{
    cJSON *item = cJSON_GetObjectItemCaseSensitive(editor.summary, field->count_key ? field->count_key : field->key);
    return cJSON_IsNumber(item) && (!field->count_key || item->valueint > 0);
}

static bool matches_changes(void)
{
    const cJSON *change;
    cJSON_ArrayForEach(change, editor.pending) {
        for (size_t i = 0; i < ARRAY_COUNT(fields); ++i) {
            const Field *field = &fields[i];
            if (strcmp(field->key, change->string))
                continue;
            if (field->type != BULK) {
                const cJSON *item = cJSON_GetObjectItemCaseSensitive(editor.summary, field->key);
                if (!cJSON_IsNumber(item) || (field->type == DECIMAL ?
                    (float)item->valuedouble != (float)change->valuedouble : item->valueint != change->valueint))
                    return false;
            }
            break;
        }
    }
    return true;
}

static void load_summary(const char *global, const char *collection)
{
    char *json = editor.global ? modifier_api_read_global_save_summary(global, collection) :
        modifier_api_read_local_save_summary(editor.local);
    cJSON_Delete(editor.summary);
    editor.summary = json ? cJSON_Parse(json) : NULL;
    free(json);
}

static void *worker(void *unused)
{
    (void)unused;
    editor.notice = NULL;
    if (editor.job == SCAN) {
        editor.supported = hub_save_supported(editor.game_dir);
        if (editor.supported)
            editor.scan_ok = hub_save_scan(editor.dir, &editor.slots);
    } else if (editor.job == INSTALL) {
        editor.notice = hub_save_install_unlock(editor.dir, UNLOCK_ASSETS) ?
            "Full unlock installed" : "Install incomplete; retry or restore the unlock backup";
    } else if (editor.job == RESTORE_UNLOCK) {
        editor.notice = hub_save_restore_set(editor.dir, HUB_SAVE_UNLOCK_BACKUP) ?
            "Pre-unlock saves restored" : "Restore incomplete; check SD card and retry";
    } else {
        HubSavePath global, collection;
        bool available = !editor.global || hub_save_global_paths(editor.dir, global, collection);
        bool applied = false;
        if (editor.job == APPLY && available) {
            char *changes = cJSON_PrintUnformatted(editor.pending);
            if (changes)
                applied = editor.global ? modifier_api_write_global_save_mod(global, collection, changes) :
                    modifier_api_write_local_save_mod(editor.local, changes);
            free(changes);
        } else if (editor.job == RESTORE_EDIT) {
            bool ok = editor.global ? hub_save_restore_set(editor.dir, HUB_SAVE_EDIT_BACKUP) :
                hub_save_restore(editor.local, HUB_SAVE_EDIT_BACKUP);
            editor.notice = ok ? "Previous save restored" : "Restore incomplete; check SD card and retry";
            available = !editor.global || hub_save_global_paths(editor.dir, global, collection);
        }
        if (available) {
            load_summary(global, collection);
        } else {
            cJSON_Delete(editor.summary);
            editor.summary = NULL;
        }
        if (editor.job == APPLY)
            editor.notice = applied && matches_changes() ? "Changes saved" :
                "Some changes could not be saved; use Restore to undo";
        cJSON_Delete(editor.pending);
        editor.pending = NULL;
        editor.edit_backup = editor.global ? hub_save_has_set_backup(editor.dir, HUB_SAVE_EDIT_BACKUP) :
            hub_save_has_backup(editor.local, HUB_SAVE_EDIT_BACKUP);
    }
    editor.unlock_backup = hub_save_has_set_backup(editor.dir, HUB_SAVE_UNLOCK_BACKUP);
    atomic_store(&editor.done, true);
    return NULL;
}

static bool start(HubApp *app, SaveJob job)
{
    pthread_attr_t attr;
    if (pthread_attr_init(&attr)) {
        hub_dialog(app, "Save editor", "Could not start the save worker.");
        return false;
    }
    editor.job = job;
    atomic_store(&editor.done, false);
    editor.exit_locked = R_SUCCEEDED(appletLockExit());
    bool ok = !pthread_attr_setstacksize(&attr, 1024 * 1024) &&
        !pthread_create(&editor.thread, &attr, worker, NULL);
    pthread_attr_destroy(&attr);
    if (!ok) {
        if (editor.exit_locked)
            appletUnlockExit();
        hub_dialog(app, "Save editor", "Could not start the save worker.");
        return false;
    }
    editor.busy = true;
    HubProgress progress = {.id = SAVE_TASK, .state = HUB_PROGRESS_ACTIVE};
    snprintf(progress.title, sizeof(progress.title), "%s", job_title(job));
    hub_progress_publish(&progress);
    return true;
}

static bool busy(HubApp *app)
{
    if (editor.busy && atomic_load(&editor.done)) {
        finish_worker();
        if (editor.notice)
            hub_toast(app, editor.notice);
    }
    if (!editor.busy)
        return false;
    hub_heading(app, "Save editor", job_title(editor.job));
    hub_hints(app, NULL, "Please wait", NULL);
    return true;
}

enum { OPEN_GLOBAL, INSTALL_UNLOCK, RESTORE_UNLOCK_ROW, FIRST_SLOT };

static HubItem menu_item(const void *unused, size_t index)
{
    (void)unused;
    switch (index) {
    case OPEN_GLOBAL: return (HubItem){"Global save", "Account-wide progress"};
    case INSTALL_UNLOCK: return (HubItem){"Install full unlock", "Gallery and global progress"};
    case RESTORE_UNLOCK_ROW:
        if (editor.unlock_backup)
            return (HubItem){"Restore pre-unlock saves", "Undo full unlock"};
        break;
    }
    return (HubItem){editor.slots.paths[index - FIRST_SLOT + !editor.unlock_backup], "Progress save"};
}

static void draw_menu(HubApp *app, size_t *focus, const HubGame *game)
{
    if (*focus == (size_t)-1) {
        *focus = 0;
        editor.supported = editor.scan_ok = editor.unlock_backup = false;
        char dir[HUB_PATH_SIZE], game_dir[HUB_PATH_SIZE];
        if (game && hub_game_save_path(HUB_ROOT, game, dir) && hub_game_path(HUB_ROOT, game, game_dir)) {
            strcpy(editor.dir, dir);
            strcpy(editor.game_dir, game_dir);
            start(app, SCAN);
        }
    }
    if (busy(app))
        return;
    hub_heading(app, "Save editor", !editor.supported ? "Offline editing is available for Rance 10." :
        !editor.scan_ok ? "Could not read the SD save folder." :
        editor.slots.count ? game->name : "No progress saves found");
    if (editor.supported && editor.scan_ok) {
        size_t count = FIRST_SLOT - !editor.unlock_backup + editor.slots.count;
        if (*focus >= count)
            *focus = count - 1;
        hub_list(app, count, *focus, menu_item, NULL);
    }
    hub_hints(app, editor.supported && editor.scan_ok ? "Open" : NULL, "Back", NULL);
}

static bool confirm(HubApp *app, SaveJob job)
{
    editor.confirmation = job;
    return hub_push(app, "save-editor.confirm", NULL);
}

static bool input_menu(HubApp *app, size_t *focus, const HubGame *game, HubInput input)
{
    if (editor.busy)
        return true;
    if (input == HUB_BACK) {
        free(editor.slots.paths);
        editor.slots = (HubSaveSlots){0};
        return false;
    }
    if (!editor.supported || !editor.scan_ok)
        return true;
    size_t count = FIRST_SLOT - !editor.unlock_backup + editor.slots.count;
    if (input == HUB_CONFIRM && *focus < count) {
        if (*focus == INSTALL_UNLOCK)
            return confirm(app, INSTALL);
        if (*focus == RESTORE_UNLOCK_ROW && editor.unlock_backup)
            return confirm(app, RESTORE_UNLOCK);
        editor.global = *focus == OPEN_GLOBAL;
        if (editor.global || hub_save_join(editor.local, editor.dir,
                editor.slots.paths[*focus - FIRST_SLOT + !editor.unlock_backup])) {
            editor.edit_backup = false;
            return hub_push(app, "save-editor.fields", game);
        }
        hub_dialog(app, "Save editor", "The save path is too long.");
        return true;
    }
    return hub_move_focus(focus, count, 1, 5, input);
}

static bool field_available(const Field *field)
{
    if (!has_value(field))
        return false;
    switch (field->section) {
    case LOCAL: return !editor.global && cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(editor.summary, "valid"));
    case GLOBAL: return editor.global && cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(editor.summary, "hasGlobal"));
    case COLLECTION: return editor.global && cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(editor.summary, "hasCollection"));
    case COMMON: return editor.global &&
        (cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(editor.summary, "hasGlobal")) ||
         cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(editor.summary, "hasCollection")) ||
         cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(editor.summary, "hasCommonSystemData")));
    }
    return false;
}

static size_t field_count(void)
{
    size_t count = 0;
    for (size_t i = 0; i < ARRAY_COUNT(fields); ++i)
        count += field_available(&fields[i]);
    return count;
}

static const Field *get_field(size_t index)
{
    for (size_t i = 0; i < ARRAY_COUNT(fields); ++i)
        if (field_available(&fields[i]) && index-- == 0)
            return &fields[i];
    return NULL;
}

static const cJSON *field_value(const Field *field, bool *changed)
{
    const cJSON *value = cJSON_GetObjectItemCaseSensitive(editor.pending, field->key);
    *changed = value != NULL;
    if (!value && field->type != BULK)
        value = cJSON_GetObjectItemCaseSensitive(editor.summary, field->key);
    return value;
}

static void format_value(char *text, size_t size, const Field *field, const cJSON *value)
{
    if (field->type == DECIMAL)
        snprintf(text, size, "%.8g", value->valuedouble);
    else
        snprintf(text, size, "%d", value->valueint);
}

static HubItem field_item(const void *unused, size_t index)
{
    (void)unused;
    static char text[64];
    const Field *field = get_field(index);
    if (!field)
        return (HubItem){"Restore previous save", "Undo offline edits"};
    bool changed;
    const cJSON *value = field_value(field, &changed);
    if (!value)
        return (HubItem){field->label, "Not set"};
    format_value(text, sizeof(text), field, value);
    if (changed)
        strcat(text, "  (unsaved)");
    return (HubItem){field->label, text};
}

static void draw_fields(HubApp *app, size_t *focus, const HubGame *game)
{
    (void)game;
    if (*focus == (size_t)-1) {
        *focus = 0;
        start(app, LOAD);
    }
    if (busy(app))
        return;
    size_t count = field_count();
    bool pending = editor.pending && editor.pending->child;
    const char *name = strrchr(editor.local, '/');
    hub_heading(app, editor.global ? "Global save" : name ? name + 1 : "Progress save",
        count ? "Select a value to edit it" :
        editor.global ? "No readable global save data; install full unlock first." : "Could not read this save.");
    size_t rows = count + editor.edit_backup;
    if (rows) {
        if (*focus >= rows)
            *focus = rows - 1;
        hub_list(app, rows, *focus, field_item, NULL);
    }
    hub_hints(app, count || editor.edit_backup ? "Select" : NULL, pending ? "Discard" : "Back",
        pending ? "Save edits" : NULL);
}

static void edit_field(HubApp *app, const Field *field)
{
    bool changed;
    const cJSON *current = field_value(field, &changed);
    char value[32] = "";
    if (current)
        format_value(value, sizeof(value), field, current);
    SwkbdConfig keyboard;
    if (R_FAILED(swkbdCreate(&keyboard, 0))) {
        hub_dialog(app, "Save editor", "Could not open the keyboard.");
        return;
    }
    swkbdConfigMakePresetDefault(&keyboard);
    swkbdConfigSetHeaderText(&keyboard, field->label);
    swkbdConfigSetSubText(&keyboard, field->type == FLAG ? "Enter 0 or 1" :
        field->type == DECIMAL ? "Enter a nonnegative number" : "Enter a whole number");
    swkbdConfigSetInitialText(&keyboard, value);
    swkbdConfigSetStringLenMax(&keyboard, sizeof(value) - 1);
    Result result = swkbdShow(&keyboard, value, sizeof(value));
    swkbdClose(&keyboard);
    hub_wait_for_release(app);
    if (R_FAILED(result))
        return;
    char *end;
    errno = 0;
    double number = field->type == DECIMAL ? strtod(value, &end) : (double)strtol(value, &end, 10);
    if (end == value || *end || errno == ERANGE || !isfinite(number) || number < field->min || number > field->max) {
        hub_dialog(app, "Invalid value", "Enter a number in the allowed range.");
        return;
    }
    const cJSON *original = cJSON_GetObjectItemCaseSensitive(editor.summary, field->key);
    if (original && original->valuedouble == number) {
        cJSON_DeleteItemFromObjectCaseSensitive(editor.pending, field->key);
        return;
    }
    if (!editor.pending)
        editor.pending = cJSON_CreateObject();
    cJSON *item = cJSON_GetObjectItemCaseSensitive(editor.pending, field->key);
    if (item)
        cJSON_SetNumberValue(item, number);
    else if (!editor.pending || !cJSON_AddNumberToObject(editor.pending, field->key, number))
        hub_dialog(app, "Save editor", "Could not queue this change.");
}

static bool input_fields(HubApp *app, size_t *focus, const HubGame *game, HubInput input)
{
    (void)game;
    if (editor.busy)
        return true;
    if (input == HUB_BACK) {
        cJSON_Delete(editor.summary);
        cJSON_Delete(editor.pending);
        editor.summary = editor.pending = NULL;
        return false;
    }
    if (input == HUB_ACTION && editor.pending && editor.pending->child) {
        start(app, APPLY);
        return true;
    }
    size_t count = field_count();
    if (input == HUB_CONFIRM && *focus < count + editor.edit_backup) {
        if (*focus == count)
            return confirm(app, RESTORE_EDIT);
        edit_field(app, get_field(*focus));
        return true;
    }
    return hub_move_focus(focus, count + editor.edit_backup, 1, 5, input);
}

static HubItem confirm_item(const void *unused, size_t index)
{
    (void)unused;
    if (index == 0)
        return (HubItem){"Cancel", NULL};
    const char *label = editor.confirmation == INSTALL ? "Install full unlock" :
        editor.confirmation == RESTORE_UNLOCK ? "Restore pre-unlock saves" : "Restore previous save";
    return (HubItem){label, NULL};
}

static void draw_confirm(HubApp *app, size_t *focus, const HubGame *game)
{
    (void)game;
    if (*focus == (size_t)-1)
        *focus = 0;
    hub_heading(app, "Confirm save change", editor.confirmation == INSTALL ?
        "Gallery and global progress will be replaced. Existing files are backed up." :
        "This replaces the current data with the backup.");
    hub_list(app, 2, *focus, confirm_item, NULL);
    hub_hints(app, "Select", "Back", NULL);
}

static bool input_confirm(HubApp *app, size_t *focus, const HubGame *game, HubInput input)
{
    (void)game;
    if (input == HUB_CONFIRM && *focus < 2) {
        bool accepted = *focus == 1;
        hub_pop(app);
        if (accepted)
            start(app, editor.confirmation);
        return true;
    }
    return hub_move_focus(focus, 2, 1, 5, input);
}

static bool available(const HubGame *game)
{
    return game && game->engine == HUB_XSYSTEM4;
}

void hub_register_save_editor(HubApp *app)
{
    static AppletHookCookie hook;
    static const HubScreen menu = {"save-editor.menu", draw_menu, input_menu};
    static const HubScreen fields_screen = {"save-editor.fields", draw_fields, input_fields};
    static const HubScreen confirmation = {"save-editor.confirm", draw_confirm, input_confirm};
    static const HubActionEntry action = {"Save editor", "save-editor.menu", available};
    hub_register_screen(app, &menu);
    hub_register_screen(app, &fields_screen);
    hub_register_screen(app, &confirmation);
    hub_register_action(app, HUB_DETAIL_ACTION, &action);
    appletHook(&hook, applet_hook, NULL);
}
