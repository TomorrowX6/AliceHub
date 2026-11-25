#ifndef ALICEHUB_UPDATE_SCREEN_H
#define ALICEHUB_UPDATE_SCREEN_H

#include "hub.h"

void hub_register_update_check(HubApp *app);
void hub_start_update_check(void);
void hub_poll_update_check(HubApp *app);
void hub_stop_update_check(void);

#endif
