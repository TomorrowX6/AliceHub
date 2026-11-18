#include <math.h>
#include "../../../src/3d/renderer.c"

enum RE_plugin_version re_plugin_version;

GLuint test_load_3d_shader(const char *vertex, const char *fragment, int engine)
{
    re_plugin_version = engine;
    return load_shader(vertex, fragment, (enum RE_plugin_version)engine);
}
