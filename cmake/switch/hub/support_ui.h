#ifndef ALICEHUB_SUPPORT_UI_H
#define ALICEHUB_SUPPORT_UI_H

#include "hub.h"

const char *support_tr(const char *zh, const char *ja, const char *en);
void support_register_storage(HubApp *app);

static inline const char *support_back(void)
{
    return support_tr("返回", "戻る", "Back");
}

static inline HubItem support_item(const void *items, size_t i)
{
    return ((const HubItem *)items)[i];
}

#endif
