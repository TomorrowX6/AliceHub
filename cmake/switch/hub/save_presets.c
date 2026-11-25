#include "save_presets.h"

typedef struct { const char *key; int value; } Change;
typedef struct { const char *label; const Change *changes; size_t count; } Preset;

#define COUNT(a) (sizeof(a) / sizeof(*(a)))
#define PRESET(label, changes) {label, changes, COUNT(changes)}
#define SET(key, value) {#key, value}

static const Change resources[] = {
    SET(difficulty, 0), SET(foodTicket, 999), SET(honor, 999), SET(friendPoint, 999),
    SET(totalExp, 99999999), SET(partyRank, 999), SET(treasureNotGetCount, 99),
    SET(itemStar, 99), SET(isUsedHannyZippo, 0), SET(hannyZippo, 99),
    SET(partyBonusMaxPoint, 99), SET(partyHp, 999999), SET(partyMaxHp, 999999),
    SET(partyAvailCount, 9), SET(partyIsFixStatus, 1), SET(partyAtk, 99999),
    SET(atkPerPhys, 999), SET(atkPerMag, 999), SET(defPerPhys, 999), SET(defPerMag, 999),
    SET(perAvoid, 999), SET(decBadCond, 999), SET(perAutoAtk, 999), SET(perCrit, 999),
    SET(perMulti, 999), SET(perExpUp, 999), SET(perHpUp, 999), SET(perAutoHeal, 999),
    SET(perFindTreasure, 999), SET(perInjection, 999), SET(addPerHit, 999), SET(statusMaxPer, 999),
};

static const Change treasure[] = {
    SET(perFindTreasure, 999), SET(treasureNotGetCount, 99), SET(rareLuck, 999),
};

static const Change cards[] = {
    SET(setCardCount, 99), SET(cardAtkBonus, 999), SET(cardHpBonus, 999),
    SET(setCharStar, 5), SET(setCharExp, 999999),
};

static const Change armies[] = {
    SET(humanCount, 350000), SET(humanDead, 0), SET(humanAlivePer, 100), SET(humanDeadPer, 0),
    SET(curseTurn, 0), SET(rareLuck, 999), SET(seaPlayerArmy, 99999), SET(seaEnemyArmy, 0),
    SET(leazasState, 0), SET(helmanState, 0), SET(zethState, 0), SET(freeCitiesState, 0),
    SET(leazasPArmy, 99999), SET(helmanPArmy, 99999), SET(zethPArmy, 99999),
    SET(freeCitiesPArmy, 99999), SET(leazasEArmy, 100), SET(helmanEArmy, 100),
    SET(zethEArmy, 100), SET(freeCitiesEArmy, 100), SET(subRemnant, 99999),
    SET(subDwarf, 99999), SET(subGeneral, 99999), SET(subHanny, 99999),
};

static const Change common[] = {
    SET(totalClearPoint, 999), SET(clearBPointMax, 999), SET(TotalClearCount, 99),
    SET(Clear_A_Count, 99), SET(Clear_B_Count, 99), SET(Clear_C_Count, 99),
    SET(AutoBattleMedal, 999), SET(AchievementPoint, 999), SET(BestScore, 999999),
    SET(LeaderSmallestTurn, 1), SET(RealSmallestTurn, 1), SET(CollectionClearB, 1),
    SET(OpenMonsterpedia, 1), SET(TrophyComplete, 1), SET(CardComplete, 1),
};

static const Change global_progress[] = {
    SET(gamePlayCount, 50), SET(itemStarRank, 20), SET(setAllCharRank, 10),
    SET(setTakeOverCardCount, 99),
};

static const Change collection[] = {
    SET(leaderAtkRatio, 10), SET(trophyExpBonus, 9999), SET(trophyStatusMaxPer, 999),
};

static const Preset local_presets[] = {
    PRESET("Max Resources & Stats", resources),
    PRESET("100% Treasure Drop", treasure),
    PRESET("All Cards x99 & 5★", cards),
    PRESET("Max Armies & 0 Casualties", armies),
};

size_t hub_save_preset_count(bool global)
{
    return global ? 1 : COUNT(local_presets);
}

const char *hub_save_preset_label(bool global, size_t index)
{
    if (index >= hub_save_preset_count(global))
        return NULL;
    return global ? "Max CP, Medals & Bonuses" : local_presets[index].label;
}

static bool add_changes(cJSON *json, const Change *changes, size_t count)
{
    for (size_t i = 0; i < count; ++i)
        if (!cJSON_AddNumberToObject(json, changes[i].key, changes[i].value))
            return false;
    return true;
}

cJSON *hub_save_preset_changes(bool global, size_t index, const cJSON *summary)
{
    if (index >= hub_save_preset_count(global))
        return NULL;
    cJSON *json = cJSON_CreateObject();
    if (!json)
        return NULL;
    bool ok;
    if (global) {
        bool g = cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(summary, "hasGlobal"));
        bool c = cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(summary, "hasCollection"));
        bool s = cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(summary, "hasCommonSystemData"));
        ok = (g || c || s) && add_changes(json, common, COUNT(common)) &&
            (!g || add_changes(json, global_progress, COUNT(global_progress))) &&
            (!c || add_changes(json, collection, COUNT(collection)));
    } else {
        const Preset *preset = &local_presets[index];
        ok = cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(summary, "valid")) &&
            add_changes(json, preset->changes, preset->count);
    }
    if (ok)
        return json;
    cJSON_Delete(json);
    return NULL;
}
