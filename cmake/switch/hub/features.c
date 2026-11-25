#include "hub.h"

void hub_register_features(HubApp *app)
{
    hub_register_library_screen(app);
    hub_register_details_screen(app);
    hub_register_settings_screen(app);
}
