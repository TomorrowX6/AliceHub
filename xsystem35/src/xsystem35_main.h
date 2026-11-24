#ifndef XSYSTEM35_MAIN_H
#define XSYSTEM35_MAIN_H

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif
int xsystem35_main(int argc, char **argv);
void xsystem35_set_gamepad_config_json(const char *json);
void xsystem35_set_virtual_stick(int stick, float x, float y);
void xsystem35_set_virtual_dpad(int mode, bool left, bool right, bool up, bool down);
void xsystem35_set_virtual_button(int slot, int type, int code, int code2, bool toggle, bool pressed);
void xsystem35_clear_virtual_inputs(void);
bool xsystem35_save_screenshot(const char *path);
#ifdef __cplusplus
}
#endif

#endif
