#include "hub.h"

void hub_register_save_editor(HubApp *);
void hub_register_support(HubApp *);

void hub_register_features(HubApp *app)
{
    hub_register_library_screen(app);
    hub_register_details_screen(app);
    hub_register_settings_screen(app);
    hub_register_save_editor(app);
    hub_register_support(app);
}
