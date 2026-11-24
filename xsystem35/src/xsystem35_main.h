#ifndef XSYSTEM35_MAIN_H
#define XSYSTEM35_MAIN_H

#ifdef __cplusplus
extern "C" {
#endif
int xsystem35_main(int argc, char **argv);
char *xsystem35_modifier_live_state(void);
void xsystem35_modifier_set_locks(int mask);
int xsystem35_modifier_get_locks(void);
void xsystem35_modifier_apply_action(const char *action);
#ifdef __cplusplus
}
#endif

#endif
