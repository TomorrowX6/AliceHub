/* Genuine GLES3 integration test. Uses real video/draw/Parts code + shaders.
 * Synthetic asymmetric artwork only; not a Dohna Dohna game screenshot.
 */
#include <assert.h>
#include <stdarg.h>
#include "../../../src/video.c"
#include "../../../src/parts/parts_internal.h"

struct config config = { .view_width = 128, .view_height = 128 };
extern bool test_blur(struct parts_construction_process *, struct parts_cp_filter *, bool);
extern bool test_gray(struct parts_construction_process *, struct parts_cp_filter *);
extern bool test_v14(struct parts_construction_process *, struct parts_cp_v14 *);
extern void test_render_cg(struct parts *, struct parts_common *);
extern void parts_render_init(void);
extern void test_set_reverse(bool, bool);
extern void test_activity_scale(struct parts *, bool, bool, float, float);
extern GLuint test_load_3d_shader(const char *, const char *, int);
extern void *test_re_renderer_create_with_billboard(int);
extern GLuint test_re_renderer_get_billboard_tex(void *, int);
extern void test_re_renderer_assert_valid(void *, int);
extern void test_re_renderer_destroy(void *);
static struct parts *fixture_part;
struct parts *__wrap_parts_get(int no) { assert(no==1 && fixture_part); return fixture_part; }

void *_xmalloc(size_t n, const char *f) { (void)f; void *p=malloc(n); assert(p); return p; }
void *_xcalloc(size_t n, size_t z, const char *f) { (void)f; void *p=calloc(n,z); assert(p); return p; }
void *_xrealloc(void *old, size_t n, const char *f) { (void)f; void *p=realloc(old,n); assert(p); return p; }
_Noreturn void sys_error(const char *fmt, ...) {
    va_list ap; va_start(ap,fmt); vfprintf(stderr,fmt,ap); va_end(ap); abort();
}
void sys_warning(const char *fmt, ...) {
    va_list ap; va_start(ap,fmt); vfprintf(stderr,fmt,ap); va_end(ap);
}
static int draws, feedbacks;
void __real_glDrawElements(GLenum mode, GLsizei count, GLenum type, const void *indices);
void __wrap_glDrawElements(GLenum mode, GLsizei count, GLenum type, const void *indices) {
    GLint fbo=0, dst=0, src=0, active=0;
    glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING,&fbo);
    glGetIntegerv(GL_ACTIVE_TEXTURE,&active);
    glActiveTexture(GL_TEXTURE0); glGetIntegerv(GL_TEXTURE_BINDING_2D,&src);
    glActiveTexture(active);
    if (fbo) glGetFramebufferAttachmentParameteriv(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,
                                                  GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME,&dst);
    draws++;
    if (dst && dst==src) { feedbacks++; fprintf(stderr,"framebuffer feedback detected\n"); abort(); }
    __real_glDrawElements(mode,count,type,indices);
}
static void no_error(void) { GLenum e=glGetError(); if(e)fprintf(stderr,"GL error 0x%x\n",e); assert(e==GL_NO_ERROR); }
static void write_ppm(const char *name, const unsigned char *px, int w, int h) {
    FILE *f=fopen(name,"wb"); assert(f); fprintf(f,"P6\n%d %d\n255\n",w,h);
    for(int i=0;i<w*h;i++) fwrite(px+4*i,1,3,f);
    fclose(f);
}
static void cpu_blur(unsigned char *out,const unsigned char *in,int W,int H,int x0,int y0,int w,int h,int r,bool v) {
    memcpy(out,in,(size_t)W*H*4);
    for(int y=0;y<h;y++)for(int x=0;x<w;x++)for(int c=0;c<3;c++) {
        int sum=0;
        for(int k=-r;k<=r;k++) {
            int xx=x+(v?0:k), yy=y+(v?k:0);
            if(xx>=0&&xx<w&&yy>=0&&yy<h)sum+=in[((y0+yy)*W+x0+xx)*4+c];
        }
        out[((y0+y)*W+x0+x)*4+c]=(unsigned char)round((double)sum/(2*r+1));
    }
}
static void check_pixels(unsigned char *got,const unsigned char *expected,size_t n,int tolerance) {
    for(size_t i=0;i<n;i++) if(abs(got[i]-expected[i])>tolerance) {
        fprintf(stderr,"pixel byte %zu: got %d expected %d\n",i,got[i],expected[i]); abort();
    }
}
// modifier_on_frame is a post-present gameplay hook, not part of rendering.
void modifier_on_frame(void) {}
static bool expect_resumed_frame;
static int resumed_frames;
static unsigned char expected_resumed_rgba[4]={37,91,173,255};
void __real_SDL_GL_SwapWindow(SDL_Window *window);
void __wrap_SDL_GL_SwapWindow(SDL_Window *window) {
    if (expect_resumed_frame) {
        unsigned char pixels[128*128*4];
        glReadPixels(0,0,128,128,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        for (int i=0;i<128*128;i++) {
            check_pixels(pixels+4*i,expected_resumed_rgba,4,0);
        }
        if (!resumed_frames) write_ppm("resume-surface-gles-synthetic.ppm",pixels,128,128);
        resumed_frames++;
        expect_resumed_frame=false;
    }
    __real_SDL_GL_SwapWindow(window);
}
static void resume_surface_tests(void) {
    // Keep the production context and its scene texture, but replace the real
    // window/drawable repeatedly: the Android surface recreation case, not a
    // context-loss simulation and not a full game/Activity lifecycle test.
    glBindFramebuffer(GL_FRAMEBUFFER,main_surface_fb);
    glClearColor(37/255.f,91/255.f,173/255.f,1); glClear(GL_COLOR_BUFFER_BIT);
    gfx_initialized=true;
    gfx_reset_view();
    for (int n=0;n<32;n++) {
        SDL_Window *previous=sdl.window;
        sdl.window=SDL_CreateWindow("Synthetic recreated surface",0,0,128,128,
                                   SDL_WINDOW_OPENGL|SDL_WINDOW_HIDDEN);
        assert(sdl.window);
        assert(SDL_GL_MakeCurrent(sdl.window,sdl.gl.context)==0);
        SDL_DestroyWindow(previous);
        assert(glIsTexture(main_surface.handle));
        glBindFramebuffer(GL_FRAMEBUFFER,0);
        glViewport(0,0,128,128);
        glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
        unsigned char black[4];
        glReadPixels(64,64,1,1,GL_RGBA,GL_UNSIGNED_BYTE,black);
        const unsigned char expected_black[4]={0,0,0,255};
        check_pixels(black,expected_black,4,0);
        expect_resumed_frame=true;
        gfx_restore_window();
        assert(!expect_resumed_frame); // Must present without a new script frame.
        GLint framebuffer=0;
        glGetIntegerv(GL_FRAMEBUFFER_BINDING,&framebuffer);
        assert(framebuffer==(GLint)main_surface_fb);
        no_error();
    }
    assert(resumed_frames==32);
    puts("PASS: 32 real SDL/GLES drawable recreations retain and re-present the scene without script redraw");
}
static void replacement_context_negative_control(void) {
    // A new *unshared* context is different from the retained-context drawable
    // test. It intentionally has none of the game's textures. This does not
    // claim to reproduce a particular phone's context loss or recover from it.
    GLuint retained_texture=gfx_main_surface()->handle;
    assert(glIsTexture(retained_texture));
    assert(SDL_GL_SetAttribute(SDL_GL_SHARE_WITH_CURRENT_CONTEXT,0)==0);
    SDL_GLContext replacement=SDL_GL_CreateContext(sdl.window);
    assert(replacement && replacement!=sdl.gl.context);
    assert(SDL_GL_GetCurrentContext()==replacement);
    assert(!glIsTexture(retained_texture));
    no_error();
    assert(SDL_GL_MakeCurrent(sdl.window,sdl.gl.context)==0);
    assert(glIsTexture(retained_texture));
    SDL_GL_DeleteContext(replacement);
    no_error();
    puts("PASS: replacement-context negative control has no retained texture; re-present is not context recovery");
}
static void replacement_context_recovery_with_backup(void) {
    gfx_initialized=true;
    gfx_reset_view();
    glBindFramebuffer(GL_FRAMEBUFFER,main_surface_fb);
    glViewport(0,0,128,128);
    glClearColor(220/255.f,110/255.f,40/255.f,1); glClear(GL_COLOR_BUFFER_BIT);
    unsigned char cg_px[16*16*4];
    for (int i=0;i<16*16;i++) { cg_px[i*4+0]=30; cg_px[i*4+1]=200; cg_px[i*4+2]=170; cg_px[i*4+3]=255; }
    Texture cg_tex={0}; gfx_init_texture_with_pixels(&cg_tex,16,16,cg_px);
    GLuint old_main_handle=main_surface.handle;
    GLuint old_cg_handle=cg_tex.handle;
    SDL_GLContext old_ctx=sdl.gl.context;
    gfx_prepare_context_backup();
    assert(SDL_GL_SetAttribute(SDL_GL_SHARE_WITH_CURRENT_CONTEXT,0)==0);
    SDL_GLContext rep=SDL_GL_CreateContext(sdl.window);
    assert(rep && rep!=old_ctx);
    assert(SDL_GL_GetCurrentContext()==rep);
    assert(!glIsTexture(old_main_handle));
    assert(!glIsTexture(old_cg_handle));
    glBindFramebuffer(GL_FRAMEBUFFER,0);
    glViewport(0,0,128,128);
    glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
    expected_resumed_rgba[0]=220; expected_resumed_rgba[1]=110;
    expected_resumed_rgba[2]=40;  expected_resumed_rgba[3]=255;
    expect_resumed_frame=true;
    gfx_restore_window();
    assert(!expect_resumed_frame);
    assert(sdl.gl.context==rep);
    assert(glIsProgram(default_shader.program));
    assert(glIsFramebuffer(main_surface_fb));
    assert(glIsTexture(main_surface.handle));
    assert(gfx_remap_texture(&cg_tex));
    assert(cg_tex.handle && glIsTexture(cg_tex.handle));
    gfx_copy(&main_surface,0,0,&cg_tex,0,0,16,16);
    SDL_Color px=gfx_get_pixel(&main_surface,8,8);
    if (!(px.r==30 && px.g==200 && px.b==170)) {
        fprintf(stderr,"restored cg_tex pixel got (%d,%d,%d,%d) expected (30,200,170,255)\n",px.r,px.g,px.b,px.a);
        abort();
    }
    gfx_delete_texture(&cg_tex);
    SDL_GL_DeleteContext(old_ctx);
    no_error();
    puts("PASS: replacement EGLContext recovery with backup restores non-black surface and sprites");
}
static void replacement_context_recovery_without_backup(void) {
    gfx_release_context_backup();
    unsigned char stale_px[8*8*4]; memset(stale_px,99,sizeof(stale_px));
    Texture stale={0}; gfx_init_texture_with_pixels(&stale,8,8,stale_px);
    void *r3d=test_re_renderer_create_with_billboard(77);
    test_re_renderer_assert_valid(r3d,77);
    GLuint old_bb_tex=test_re_renderer_get_billboard_tex(r3d,77);
    assert(old_bb_tex && glIsTexture(old_bb_tex));
    SDL_GLContext old_ctx=sdl.gl.context;
    assert(SDL_GL_SetAttribute(SDL_GL_SHARE_WITH_CURRENT_CONTEXT,0)==0);
    SDL_GLContext rep2=SDL_GL_CreateContext(sdl.window);
    assert(rep2 && rep2!=old_ctx);
    assert(SDL_GL_GetCurrentContext()==rep2);
    assert(!glIsTexture(stale.handle));
    assert(!glIsTexture(old_bb_tex));
    gfx_restore_window();
    assert(sdl.gl.context==rep2);
    assert(glIsProgram(default_shader.program));
    assert(glIsFramebuffer(main_surface_fb));
    assert(glIsTexture(main_surface.handle));
    assert(!gfx_remap_texture(&stale));
    assert(stale.handle==0);
    test_re_renderer_assert_valid(r3d,77);
    test_re_renderer_destroy(r3d);
    SDL_GL_DeleteContext(old_ctx);
    no_error();
    puts("PASS: unbacked replacement EGLContext recovery rebuilds GL objects and clears stale handles");
}
static void shader3d_tests(void) {
    // Compile/link the production 3D shader pairs through the real loader.
    // UBO storage is separate from default-block vertex uniform limits.
    GLint max_block=0;
    glGetIntegerv(GL_MAX_UNIFORM_BLOCK_SIZE,&max_block);
    assert(max_block >= 308*48);
    const char *names[]={"reign","reign_shadow","reign_outline"};
    for (int engine=0;engine<3;engine++)for(int i=0;i<3;i++) {
        char vertex[128],fragment[128];
        snprintf(vertex,sizeof(vertex),"shaders/%s.v.glsl",names[i]);
        snprintf(fragment,sizeof(fragment),"shaders/%s.f.glsl",names[i]);
        GLuint program=test_load_3d_shader(vertex,fragment,engine);
        GLuint block=glGetUniformBlockIndex(program,"BoneTransforms");
        assert(block!=GL_INVALID_INDEX);
        GLint bytes=0;
        glGetActiveUniformBlockiv(program,block,GL_UNIFORM_BLOCK_DATA_SIZE,&bytes);
        assert(bytes==308*48 && bytes<=max_block);
        glDeleteProgram(program); no_error();
    }
    puts("PASS: 9 production 3D shader pairs link; 308-bone UBO is 14784 bytes");
}
static void blur_tests(void) {
    enum { W=15,H=11,N=W*H*4 };
    unsigned char input[N], expected[N], next[N];
    for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
        int i=(y*W+x)*4;
        input[i]=(x%3==0)?255:0; input[i+1]=(y%3==0)?255:0;
        input[i+2]=((x+y)%4==0)?255:0; input[i+3]=(x*11+y*13)%256;
    }
    for(int vertical=0;vertical<2;vertical++)for(int full=0;full<2;full++)for(int pinned=0;pinned<2;pinned++) {
        struct parts_construction_process cp={0};
        gfx_init_texture_with_pixels(&cp.common.texture,W,H,input);
        struct parts_cp_filter op={.x=2,.y=1,.w=10,.h=8,.blur=1,.full_size=full};
        cpu_blur(expected,input,W,H,full?0:2,full?0:1,full?W:10,full?H:8,1,vertical);
        if(pinned)gfx_begin_draw_target(&cp.common.texture);
        assert(test_blur(&cp,&op,vertical));
        if(pinned)gfx_end_draw_target();
        unsigned char *got=gfx_get_pixels(&cp.common.texture);
        check_pixels(got,expected,N,1); free(got);
        if(full&&!vertical&&!pinned) {
            cpu_blur(next,expected,W,H,0,0,W,H,1,true);
            assert(test_blur(&cp,&op,true));
            got=gfx_get_pixels(&cp.common.texture); check_pixels(got,next,N,2);
            write_ppm("blur-gles-synthetic.ppm",got,W,H); free(got);
        }
        gfx_delete_texture(&cp.common.texture); no_error();
    }
    puts("PASS: GLES H/V blur, full/subrect, pinned/unpinned, RGBA preservation, two-pass blur");
}
static void gray_v14_and_fill_shader_tests(void) {
    enum { W=15,H=11,N=W*H*4 };
    unsigned char input[N], expected[N];
    for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
        int i=(y*W+x)*4;
        input[i]=(unsigned char)((x*17+y*3)%256);
        input[i+1]=(unsigned char)((x*5+y*23)%256);
        input[i+2]=(unsigned char)((x*13+y*7)%256);
        input[i+3]=(unsigned char)((x*19+y*29)%256);
    }
    // 1. build_gray_filter (full_size & subrect, pinned & unpinned, no feedback loop)
    for(int full=0;full<2;full++)for(int pinned=0;pinned<2;pinned++) {
        struct parts_construction_process cp={0};
        gfx_init_texture_with_pixels(&cp.common.texture,W,H,input);
        struct parts_cp_filter op={.x=2,.y=1,.w=10,.h=8,.full_size=full};
        memcpy(expected,input,N);
        int x0=full?0:2, y0=full?0:1, rw=full?W:10, rh=full?H:8;
        for(int y=0;y<rh;y++)for(int x=0;x<rw;x++) {
            int idx=((y0+y)*W+(x0+x))*4;
            unsigned char g=(unsigned char)round(0.299*input[idx]+0.587*input[idx+1]+0.114*input[idx+2]);
            expected[idx]=expected[idx+1]=expected[idx+2]=g;
        }
        if(pinned)gfx_begin_draw_target(&cp.common.texture);
        assert(test_gray(&cp,&op));
        if(pinned)gfx_end_draw_target();
        unsigned char *got=gfx_get_pixels(&cp.common.texture);
        check_pixels(got,expected,N,1); free(got);
        gfx_delete_texture(&cp.common.texture); no_error();
    }
    // 2. build_v14 cmd 132 color reverse (full_size & subrect, no feedback loop)
    for(int full=0;full<2;full++) {
        struct parts_construction_process cp={0};
        gfx_init_texture_with_pixels(&cp.common.texture,W,H,input);
        struct parts_cp_v14 op={.command=132,.dx=2,.dy=1,.dw=10,.dh=8,.full_size=full};
        memcpy(expected,input,N);
        int x0=full?0:2, y0=full?0:1, rw=full?W:10, rh=full?H:8;
        for(int y=0;y<rh;y++)for(int x=0;x<rw;x++) {
            int idx=((y0+y)*W+(x0+x))*4;
            expected[idx]=255-input[idx];
            expected[idx+1]=255-input[idx+1];
            expected[idx+2]=255-input[idx+2];
        }
        assert(test_v14(&cp,&op));
        unsigned char *got=gfx_get_pixels(&cp.common.texture);
        check_pixels(got,expected,N,1); free(got);
        gfx_delete_texture(&cp.common.texture); no_error();
    }
    // 3. run_fill_shader callers: gfx_fill_amap_over_border, under_border, gradation_ud
    {
        Texture dst={0};
        gfx_init_texture_with_pixels(&dst,W,H,input);
        memcpy(expected,input,N);
        for(int y=1;y<1+8;y++)for(int x=2;x<2+10;x++) {
            int idx=(y*W+x)*4;
            if(input[idx+3]>=120) expected[idx+3]=42;
        }
        gfx_fill_amap_over_border(&dst,2,1,10,8,42,120);
        unsigned char *got=gfx_get_pixels(&dst);
        check_pixels(got,expected,N,1); free(got);
        gfx_delete_texture(&dst); no_error();
    }
    {
        Texture dst={0};
        gfx_init_texture_with_pixels(&dst,W,H,input);
        memcpy(expected,input,N);
        for(int y=1;y<1+8;y++)for(int x=2;x<2+10;x++) {
            int idx=(y*W+x)*4;
            if(input[idx+3]<=120) expected[idx+3]=210;
        }
        gfx_fill_amap_under_border(&dst,2,1,10,8,210,120);
        unsigned char *got=gfx_get_pixels(&dst);
        check_pixels(got,expected,N,1); free(got);
        gfx_delete_texture(&dst); no_error();
    }
    {
        Texture dst={0};
        gfx_init_texture_with_pixels(&dst,W,H,input);
        memcpy(expected,input,N);
        for(int y=0;y<8;y++)for(int x=0;x<10;x++) {
            int idx=((1+y)*W+(2+x))*4;
            double ty=((double)y+0.5)/8.0;
            expected[idx+3]=(unsigned char)round(20.0+(220.0-20.0)*ty);
        }
        gfx_fill_amap_gradation_ud(&dst,2,1,10,8,20,220);
        unsigned char *got=gfx_get_pixels(&dst);
        check_pixels(got,expected,N,2); free(got);
        gfx_delete_texture(&dst); no_error();
    }
    puts("PASS: GLES gray filter, v14 color reverse, and fill_amap shaders (0 feedback loops)");
}
static void sprite_tests(bool activity, bool v2) {
    enum { W=32,H=24,N=W*H*4,S=128 };
    unsigned char input[N];
    for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
        int i=(y*W+x)*4;
        input[i]=x<8?255:20; input[i+1]=y<6?255:30;
        input[i+2]=(x>=8&&x<24&&abs(y-12)<x/3)?255:40; input[i+3]=255;
    }
    struct parts p={0}; struct parts_common c={0};
    parts_v2=v2;
    p.local.pos=p.global.pos=(SDL_FPoint){64,64}; p.local.scale.x=1; p.local.scale.y=1;
    p.global.scale.x=p.global.scale.y=1;
    p.world_matrix_self=&p; fixture_part=&p;
    p.global.alpha=255; p.global.multiply_color=(SDL_Color){255,255,255,255};
    c.w=W; c.h=H; c.origin_offset=(Point){-W/2,-H/2};
    gfx_init_texture_with_pixels(&c.texture,W,H,input);
    // Use an RGBA offscreen target so clear/output has exact known channels.
    Texture target={0}; gfx_init_texture_blank(&target,S,S);
    for(int mags=0;mags<(activity?4:1);mags++)for(int flags=0;flags<4;flags++) {
        if (activity) {
            test_activity_scale(&p, flags&1, flags&2, mags&1?-1:1, mags&2?-1:1);
            assert(p.reverse_lr == !!(flags&1) && p.reverse_tb == !!(flags&2));
            assert(p.local.scale.x == (((!v2 && (flags&1)) ^ !!(mags&1)) ? -1 : 1));
            assert(p.local.scale.y == (((!v2 && (flags&2)) ^ !!(mags&2)) ? -1 : 1));
        } else {
            test_set_reverse(flags&1,flags&2);
        }
        GLuint fb=gfx_set_framebuffer(GL_DRAW_FRAMEBUFFER,&target,0,0,S,S);
        glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
        test_render_cg(&p,&c);
        gfx_reset_framebuffer(GL_DRAW_FRAMEBUFFER,fb);
        unsigned char *got=gfx_get_pixels(&target);
        // Offscreen Parts projection and texture readback both use texture row order.
        for(int y=0;y<S;y++)for(int x=0;x<S;x++) {
            int ox=x-(64-W/2), oy=y-(64-H/2);
            if(ox>=0&&ox<W&&oy>=0&&oy<H) {
                int flip=flags^mags;
                int sx=(flip&1)?W-1-ox:ox, sy=(flip&2)?H-1-oy:oy;
                check_pixels(got+(y*S+x)*4,input+(sy*W+sx)*4,4,0);
            } else {
                static const unsigned char black[4]={0,0,0,255};
                check_pixels(got+(y*S+x)*4,black,4,0);
            }
        }
        char name[96]; snprintf(name,sizeof name,"sprite-%s-v%d-%d-mag%d-gles-synthetic.ppm",
                               activity?"activity":"api",v2?2:1,flags,mags);
        write_ppm(name,got,S,S); free(got); no_error();
    }
    gfx_delete_texture(&target); gfx_delete_texture(&c.texture);
    printf("PASS: actual %s v%d CG renderer normal/LR/TB/LR+TB pixels and placement (no double flip)\n",
           activity?"activity loader":"HLL API", v2?2:1);
}
int main(void) {
    assert(SDL_Init(SDL_INIT_VIDEO)==0);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION,3);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION,0);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK,SDL_GL_CONTEXT_PROFILE_ES);
    sdl.window=SDL_CreateWindow("Synthetic renderer regression",0,0,128,128,SDL_WINDOW_OPENGL|SDL_WINDOW_HIDDEN);
    if(!sdl.window)sys_error("SDL: %s",SDL_GetError());
    sdl.gl.context=SDL_GL_CreateContext(sdl.window);
    if(!sdl.gl.context)sys_error("GLES: %s",SDL_GetError());
    printf("GL_VERSION=%s\nGL_RENDERER=%s\n",glGetString(GL_VERSION),glGetString(GL_RENDERER));
    sdl.format=SDL_AllocFormat(SDL_PIXELFORMAT_RGBA32); sdl.w=sdl.h=128;
    gl_initialize(); main_surface_init(128,128); gfx_draw_init(); parts_render_init(); no_error();
    shader3d_tests(); blur_tests(); gray_v14_and_fill_shader_tests(); sprite_tests(false,true);
    sprite_tests(true,false); sprite_tests(true,true);
    resume_surface_tests(); replacement_context_negative_control();
    replacement_context_recovery_with_backup(); replacement_context_recovery_without_backup();
    blur_tests(); sprite_tests(false,true); no_error();
    puts("PASS: 2D copy shaders and Parts CG shader render cleanly on recovered replacement context");
    printf("PASS: %d actual GL draws; %d feedback loops\n",draws,feedbacks);
    gfx_fini(); return 0;
}
