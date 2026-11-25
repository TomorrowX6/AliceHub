#include "controller_profile.h"

#include <errno.h>
#include <string.h>
#include <sys/stat.h>

const char *const controller_button_names[] = {
    "Up", "Down", "Left", "Right", "A", "B", "X", "Y", "L", "R", "ZL", "ZR",
    "Minus", "Plus", "Left Stick", "Right Stick", "Unbound"
};

void controller_profile_defaults(ControllerProfile *profile)
{
    *profile = (ControllerProfile){.touch = {
        {CP_UP, 2, 5}, {CP_DOWN, 2, 7}, {CP_LEFT, 1, 6}, {CP_RIGHT, 3, 6},
        {CP_A, 14, 6}, {CP_B, 13, 7}, {CP_X, 13, 5}, {CP_Y, 12, 6}
    }};
    for (unsigned i = 0; i < CP_BUTTONS; ++i)
        profile->binding[i] = i;
}

bool controller_profile_move(ControllerProfile *profile, size_t slot, unsigned column, unsigned row)
{
    if (slot >= CP_TOUCH_BUTTONS || column >= CP_COLUMNS || row >= CP_ROWS)
        return false;
    ControllerTouchButton *button = &profile->touch[slot];
    for (size_t i = 0; i < CP_TOUCH_BUTTONS; ++i)
        if (i != slot && profile->touch[i].column == column && profile->touch[i].row == row) {
            profile->touch[i].column = button->column;
            profile->touch[i].row = button->row;
            break;
        }
    button->column = column;
    button->row = row;
    return true;
}

uint32_t controller_profile_map(const ControllerProfile *profile, uint32_t physical)
{
    uint32_t result = 0;
    for (unsigned i = 0; i < CP_BUTTONS; ++i)
        if ((physical & (1u << i)) && profile->binding[i] < CP_BUTTONS)
            result |= 1u << profile->binding[i];
    return result;
}

int controller_profile_hit(const ControllerProfile *profile, float x, float y)
{
    if (!profile->touch_enabled || !(x >= 0 && x < 1 && y >= 0 && y < 1))
        return -1;
    for (size_t i = 0; i < CP_TOUCH_BUTTONS; ++i)
        if (profile->touch[i].button < CP_BUTTONS &&
            profile->touch[i].column == (unsigned)(x * CP_COLUMNS) &&
            profile->touch[i].row == (unsigned)(y * CP_ROWS))
            return (int)i;
    return -1;
}

static bool valid_profile(const ControllerProfile *profile)
{
    for (size_t i = 0; i < CP_BUTTONS; ++i)
        if (profile->binding[i] > CP_BUTTONS)
            return false;
    bool occupied[CP_ROWS][CP_COLUMNS] = {{0}};
    for (size_t i = 0; i < CP_TOUCH_BUTTONS; ++i) {
        ControllerTouchButton button = profile->touch[i];
        if (button.button > CP_BUTTONS || button.column >= CP_COLUMNS || button.row >= CP_ROWS ||
            occupied[button.row][button.column])
            return false;
        occupied[button.row][button.column] = true;
    }
    return true;
}

static bool read_uint(FILE *file, unsigned limit, unsigned *out)
{
    int c;
    do {
        c = fgetc(file);
    } while (c == ' ' || c == '\n' || c == '\r' || c == '\t');
    unsigned value = 0, digits = 0;
    for (; c >= '0' && c <= '9'; c = fgetc(file)) {
        value = value * 10 + (unsigned)(c - '0');
        if (++digits > 2 || value > limit)
            return false;
    }
    if (!digits || (c != ' ' && c != '\t' && c != '\r' && c != '\n'))
        return false;
    *out = value;
    return true;
}

static bool profile_read(FILE *file, ControllerProfile *profile)
{
    char header[32];
    unsigned n;
    if (!fgets(header, sizeof(header), file) || strcmp(header, "alicehub-controller 1\n") ||
        !read_uint(file, 1, &n))
        return false;
    ControllerProfile parsed = {.touch_enabled = n};
    for (size_t i = 0; i < CP_BUTTONS; ++i) {
        if (!read_uint(file, CP_BUTTONS, &n))
            return false;
        parsed.binding[i] = n;
    }
    for (size_t i = 0; i < CP_TOUCH_BUTTONS; ++i) {
        unsigned button, column, row;
        if (!read_uint(file, CP_BUTTONS, &button) || !read_uint(file, CP_COLUMNS - 1, &column) ||
            !read_uint(file, CP_ROWS - 1, &row))
            return false;
        parsed.touch[i] = (ControllerTouchButton){button, column, row};
    }
    if (fgetc(file) != EOF || ferror(file) || !valid_profile(&parsed))
        return false;
    *profile = parsed;
    return true;
}

enum { PROFILE_PATH_SIZE = HUB_PATH_SIZE + HUB_NAME_SIZE + 64 };

static bool profile_base(const char *root, const HubGame *game, bool create,
                         char path[PROFILE_PATH_SIZE])
{
    if (!game)
        return (size_t)snprintf(path, PROFILE_PATH_SIZE, "%s/controller-profile", root) < PROFILE_PATH_SIZE;
    HubGame checked;
    if (!hub_game_init(&checked, game->engine, game->name))
        return false;
    int n = snprintf(path, PROFILE_PATH_SIZE, "%s/controller-profiles", root);
    if ((unsigned)n >= PROFILE_PATH_SIZE || (create && mkdir(path, 0777) && errno != EEXIST))
        return false;
    int more = snprintf(path + n, PROFILE_PATH_SIZE - n, "/%s", hub_engines[game->engine].id);
    if ((unsigned)more >= PROFILE_PATH_SIZE - (unsigned)n || (create && mkdir(path, 0777) && errno != EEXIST))
        return false;
    n += more;
    more = snprintf(path + n, PROFILE_PATH_SIZE - n, "/%s", checked.name);
    if ((unsigned)more >= PROFILE_PATH_SIZE - (unsigned)n || (create && mkdir(path, 0777) && errno != EEXIST))
        return false;
    n += more;
    return (size_t)snprintf(path + n, PROFILE_PATH_SIZE - n, "/profile") < (size_t)(PROFILE_PATH_SIZE - n);
}

static ControllerProfileScope load_one(const char *root, const HubGame *game, ControllerProfile *profile)
{
    char path[PROFILE_PATH_SIZE + 4];
    if (!profile_base(root, game, false, path))
        return CP_UNREADABLE;
    FILE *file = fopen(path, "rb");
    if (!file && errno == ENOENT) {
        strcat(path, ".bak");
        file = fopen(path, "rb");
    }
    if (!file)
        return errno == ENOENT ? CP_DEFAULTS : CP_UNREADABLE;
    bool ok = profile_read(file, profile);
    fclose(file);
    return ok ? (game ? CP_GAME : CP_GLOBAL) : CP_UNREADABLE;
}

ControllerProfileScope controller_profile_load(const char *root, const HubGame *game, ControllerProfile *profile)
{
    controller_profile_defaults(profile);
    if (game) {
        ControllerProfileScope scope = load_one(root, game, profile);
        if (scope == CP_GAME)
            return scope;
        if (scope == CP_UNREADABLE) {
            load_one(root, NULL, profile);
            return CP_UNREADABLE;
        }
    }
    return load_one(root, NULL, profile);
}

bool controller_profile_save(const char *root, const HubGame *game, const ControllerProfile *profile)
{
    char path[PROFILE_PATH_SIZE], temp[PROFILE_PATH_SIZE + 4], backup[PROFILE_PATH_SIZE + 4];
    if (!valid_profile(profile) || !profile_base(root, game, true, path))
        return false;
    snprintf(temp, sizeof(temp), "%s.tmp", path);
    snprintf(backup, sizeof(backup), "%s.bak", path);
    FILE *file = fopen(temp, "wb");
    if (!file)
        return false;
    fprintf(file, "alicehub-controller 1\n%d\n", profile->touch_enabled);
    for (size_t i = 0; i < CP_BUTTONS; ++i)
        fprintf(file, "%u%c", profile->binding[i], i + 1 == CP_BUTTONS ? '\n' : ' ');
    for (size_t i = 0; i < CP_TOUCH_BUTTONS; ++i) {
        ControllerTouchButton button = profile->touch[i];
        fprintf(file, "%u %u %u\n", button.button, button.column, button.row);
    }
    bool ok = !ferror(file);
    if (fclose(file) || !ok) {
        remove(temp);
        return false;
    }
    if (!rename(temp, path)) {
        remove(backup);
        return true;
    }
    remove(backup);
    if (rename(path, backup)) {
        remove(temp);
        return false;
    }
    if (rename(temp, path)) {
        rename(backup, path);
        remove(temp);
        return false;
    }
    remove(backup);
    return true;
}

bool controller_profile_reset(const char *root, const HubGame *game)
{
    char path[PROFILE_PATH_SIZE + 4];
    if (!profile_base(root, game, false, path))
        return false;
    if (remove(path) && errno != ENOENT)
        return false;
    strcat(path, ".bak");
    return !remove(path) || errno == ENOENT;
}
