/* Include the production TU so the test can call its file-local filter. */
#include "../../../src/parts/construction.c"
bool test_blur(struct parts_construction_process *cp, struct parts_cp_filter *filter, bool vertical)
{
    return build_blur_filter(cp, filter, vertical);
}
