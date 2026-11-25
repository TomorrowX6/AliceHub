#include "hub.h"
#include "local_import.h"
#include "update_screen.h"

void hub_register_save_manager(HubApp *);
void hub_register_save_editor(HubApp *);
void hub_register_support(HubApp *);
void hub_register_instances(HubApp *);
void hub_register_controller_profiles(HubApp *app);
void hub_register_online(HubApp *);
void hub_register_mods(HubApp *);

void hub_register_features(HubApp *app)
{
    hub_register_library_screen(app);
    hub_register_details_screen(app);
    hub_register_settings_screen(app);
    hub_register_save_manager(app);
    hub_register_save_editor(app);
    hub_register_support(app);
    hub_register_update_check(app);
    hub_register_local_import(app);
    hub_register_instances(app);
    hub_register_controller_profiles(app);
    hub_register_online(app);
    hub_register_mods(app);
}
