/* Include the production TU so the test can call its file-local filter. */
#include "../../../src/parts/construction.c"

bool sys4_text_is_gbk = false;
struct cg *asset_cg_load_by_name(const char *name, int *id) { (void)name; (void)id; return NULL; }
void cg_free(struct cg *cg) { (void)cg; }
struct string *string_dup(const struct string *in) { (void)in; return NULL; }
void free_string(struct string *s) { (void)s; }

bool test_blur(struct parts_construction_process *cp, struct parts_cp_filter *filter, bool vertical)
{
    return build_blur_filter(cp, filter, vertical);
}
bool test_gray(struct parts_construction_process *cp, struct parts_cp_filter *filter)
{
    return build_gray_filter(cp, filter);
}
bool test_v14(struct parts_construction_process *cp, struct parts_cp_v14 *op)
{
    return build_v14(cp, op);
}
