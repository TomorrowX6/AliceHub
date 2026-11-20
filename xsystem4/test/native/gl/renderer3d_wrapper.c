#include <assert.h>
#include <math.h>
#include "../../../src/3d/renderer.c"

enum RE_plugin_version re_plugin_version;

static uint8_t test_bb_pixels[16] = {
    255, 0, 0, 255,   255, 0, 0, 255,
    255, 0, 0, 255,   255, 0, 0, 255,
};
static struct cg test_bb_cg = {
    .metrics = { .w = 2, .h = 2, .has_alpha = true },
    .pixels = test_bb_pixels,
};

struct cg *asset_cg_load(int no)
{
    return no == 77 ? &test_bb_cg : NULL;
}

GLuint test_load_3d_shader(const char *vertex, const char *fragment, int engine)
{
    re_plugin_version = engine;
    return load_shader(vertex, fragment, (enum RE_plugin_version)engine);
}

int test_bone_texture_unit(void)
{
    return BONE_TEXTURE_UNIT;
}

GLuint test_create_bone_texture(void)
{
    // Mirrors init_instance_bone_texture() in reign.c.
    GLuint tex;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, MAX_BONES * 3, 1, 0, GL_RGBA, GL_FLOAT, NULL);
    float bone[12] = { 1, 0, 0, 0.5f,  0, 1, 0, 0,  0, 0, 1, 0 };
    glTexSubImage2D(GL_TEXTURE_2D, 0, (MAX_BONES - 1) * 3, 0, 3, 1, GL_RGBA, GL_FLOAT, bone);
    glBindTexture(GL_TEXTURE_2D, 0);
    return glGetError() == GL_NO_ERROR ? tex : 0;
}

void *test_re_renderer_create_with_billboard(int cg_no)
{
    struct RE_renderer *r = RE_renderer_new(RE_SEAL_PLUGIN);
    RE_renderer_set_viewport_size(r, 32, 32);
    assert(RE_renderer_load_billboard_texture_by_no(r, cg_no));
    return r;
}

GLuint test_re_renderer_get_billboard_tex(void *ptr, int cg_no)
{
    struct RE_renderer *r = ptr;
    struct billboard_texture *bt = ht_get_int(r->billboard_textures_by_no, cg_no, NULL);
    return bt ? bt->texture : 0;
}

void test_re_renderer_assert_valid(void *ptr, int cg_no)
{
    struct RE_renderer *r = ptr;
    assert(r->program && glIsProgram(r->program));
    assert(r->depth_buffer && glIsRenderbuffer(r->depth_buffer));
    assert(r->shadow.program && glIsProgram(r->shadow.program));
    assert(r->shadow.fbo && glIsFramebuffer(r->shadow.fbo));
    assert(r->shadow.texture && glIsTexture(r->shadow.texture));
    assert(r->outline.program && glIsProgram(r->outline.program));
    assert(r->billboard_particle_vao && glIsVertexArray(r->billboard_particle_vao));
    assert(r->billboard_instance_vao && glIsVertexArray(r->billboard_instance_vao));
    struct billboard_texture *bt = ht_get_int(r->billboard_textures_by_no, cg_no, NULL);
    assert(bt && bt->texture && glIsTexture(bt->texture));
}

void test_re_renderer_destroy(void *ptr)
{
    RE_renderer_free((struct RE_renderer *)ptr);
}
