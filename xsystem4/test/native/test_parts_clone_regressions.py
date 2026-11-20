#!/usr/bin/env python3
"""Host test of single-part serialization with resource/rendering fixtures.

Uses the production part record, parameter and widget serializers. GPU state,
message windows and motion resources are fixtures, not rendered game coverage.
Run: python3 xsystem4/test/native/test_parts_clone_regressions.py
"""
from pathlib import Path
import subprocess
import tempfile
import unittest

from test_parts_render_regressions import function

ROOT = Path(__file__).resolve().parents[2]

FIXTURE = r'''
#include <assert.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include "system4.h"
#include "system4/string.h"
#include "vm/page.h"
#include "vm/heap.h"
#include "vm/v12.h"
#include "parts.h"
#include "parts/parts_internal.h"
#include "hll/iarray.h"
#define CURRENT_SAVE_VERSION 32
bool parts_v2 = true, parts_multi_controller = false;
struct string EMPTY_STRING;
static struct parts *objects[8];
static int updates, rebuilds;
static struct ain program = {.version=14};
struct ain *ain = &program;
int PE_GetComponentType(int n,int s) { (void)n; (void)s; return 11; }
void *_xcalloc(size_t n,size_t s,const char *f) { (void)f; return calloc(n,s); }
void *_xmalloc(size_t n,const char *f) { (void)f; return malloc(n); }
struct string *make_string(const char *s, size_t n) {
    struct string *r = calloc(1, sizeof(*r)+n+1);
    r->size=n; r->ref=1; memcpy(r->text,s,n); return r;
}
struct string *string_ref(struct string *s) {
    return make_string(s->text,s->size);
}
void free_string(struct string *s) { if(s != &EMPTY_STRING) free(s); }
void iarray_init_writer(struct iarray_writer *w,const char *s) {
    *w=(struct iarray_writer){0}; do { iarray_write(w,*s); } while(*s++);
}
void iarray_write(struct iarray_writer *w,int v) {
    w->data=realloc(w->data,(w->size+1)*sizeof(int)); w->data[w->size++]=v;
}
void iarray_write_at(struct iarray_writer *w,unsigned p,int v) { w->data[p]=v; }
void iarray_free_writer(struct iarray_writer *w) { free(w->data); }
struct page *alloc_page(enum page_type t,int index,int n) {
    struct page *p=calloc(1,sizeof(*p)+n*sizeof(union vm_value));
    p->type=t; p->index=index; p->nr_vars=n; return p;
}
_Noreturn void _vm_error(const char *fmt,...) { (void)fmt; abort(); }
void sys_warning(const char *fmt,...) { (void)fmt; abort(); }
int ain_type_slot_width(const struct ain_type *t) { assert(t->data==AIN_INT); return 1; }
int variable_init_typed(union vm_value *v,const struct ain_type *t,bool a,bool b) {
    (void)t; (void)a; (void)b; v->i=0; return 1;
}
union vm_value variable_initval(enum ain_data_type t) { (void)t; return (union vm_value){.i=0}; }
int alloc_struct(int n) { (void)n; abort(); }
void init_struct(int n,int slot) { (void)n; (void)slot; abort(); }
void create_struct(int n,union vm_value *v) { (void)n; (void)v; abort(); }
int32_t heap_alloc_slot(enum vm_pointer_type t) { (void)t; abort(); }
void heap_set_page(int slot,struct page *p) { (void)slot; (void)p; abort(); }
bool iarray_init_reader(struct iarray_reader *r,struct page *p,const char *s) {
    *r=(struct iarray_reader){.data=p->values,.size=p->nr_vars};
    do { if(iarray_read(r)!=*s) return false; } while(*s++); return !r->error;
}
int iarray_read(struct iarray_reader *r) {
    if(r->pos>=r->size) { r->error=1; return 0; } return r->data[r->pos++].i;
}
void iarray_write_float(struct iarray_writer *w,float f) {
    union vm_value v={.f=f}; iarray_write(w,v.i);
}
float iarray_read_float(struct iarray_reader *r) {
    union vm_value v={.i=iarray_read(r)}; return v.f;
}
void iarray_write_string(struct iarray_writer *w,struct string *s) {
    for(int i=0;s && i<s->size;i++) iarray_write(w,s->text[i]); iarray_write(w,0);
}
struct string *iarray_read_string(struct iarray_reader *r) {
    char s[256]; int n=0,c; while((c=iarray_read(r))) { assert(n<255); s[n++]=c; }
    return make_string(s,n);
}
void iarray_write_string_or_null(struct iarray_writer *w,struct string *s) { iarray_write_string(w,s); }
struct string *iarray_read_string_or_null(struct iarray_reader *r) { return iarray_read_string(r); }
void iarray_write_color(struct iarray_writer *w,SDL_Color *c) {
    iarray_write(w,c->r); iarray_write(w,c->g); iarray_write(w,c->b); iarray_write(w,c->a);
}
void iarray_read_color(struct iarray_reader *r,SDL_Color *c) {
    c->r=iarray_read(r); c->g=iarray_read(r); c->b=iarray_read(r); c->a=iarray_read(r);
}
void iarray_write_text_style(struct iarray_writer *w,struct text_style *s) { (void)w; (void)s; }
void iarray_read_text_style(struct iarray_reader *r,struct text_style *s) { (void)r; (void)s; }
void delete_page_vars(struct page *p) { (void)p; }
void free_page(struct page *p) { free(p); }
struct parts *parts_try_get(int n) { return n>=0 && n<8 ? objects[n]:NULL; }
struct parts *parts_get(int n) {
    assert(n>=0 && n<8);
    if(!objects[n]) {
        objects[n]=calloc(1,sizeof(struct parts)); objects[n]->no=n;
        TAILQ_INIT(&objects[n]->children); TAILQ_INIT(&objects[n]->motion);
    }
    return objects[n];
}
void parts_release(int n) {
    struct parts *p=parts_try_get(n); if(!p) return;
    free_string(p->comment); free_string(p->user_component_name);
    free_string(p->text_box_text);
    for(int i=0;i<3;i++) free_string(p->sound_names[i]);
    if(p->widget) {
        free_string(p->widget->text); free_string(p->widget->cg_name); free_string(p->widget->flat_name);
        for(int i=0;i<2;i++) { free_string(p->widget->override_cg[i]); free_string(p->widget->override_flat[i]); }
        free(p->widget);
    }
    free(p->radio_children); free(p); objects[n]=NULL;
}
void PE_UpdateComponent(int t) { (void)t; updates++; }
void parts_widget_rebuild(struct parts *p) { assert(p->component_type==0); rebuilds++; }
void parts_state_reset(struct parts_state *s,enum parts_type t) { memset(s,0,sizeof(*s)); s->type=t; }
void parts_panel_rebuild(struct parts *p) { (void)p; }
void parts_scrollbar_rebuild(struct parts *p) { (void)p; }
void parts_list_resort(struct parts *p) { (void)p; }
void parts_component_dirty(struct parts *p) { (void)p; }
void parts_recalculate_hitbox(struct parts *p) { (void)p; }
void PE_SuspendTexture(int n) { (void)n; }
void parts_add_motion(struct parts *p,struct parts_motion *m) { (void)p; (void)m; abort(); }
static void save_parts_state(struct iarray_writer *w,struct parts *p,struct parts_state *s) {
    (void)p; iarray_write(w,s->type); iarray_write(w,s->common.w);
}
static void load_parts_state(struct iarray_reader *r,struct parts *p,struct parts_state *s,int v) {
    (void)p; (void)v; s->type=iarray_read(r); s->common.w=iarray_read(r);
}
static void save_message_window(struct iarray_writer *w,struct parts *p,struct parts_message_window *m) {
    (void)w; (void)p; assert(!m);
}
static void load_message_window(struct iarray_reader *r,struct parts *p,int v) { (void)r; (void)p; (void)v; }
static void save_parts_motion(struct iarray_writer *w,struct parts_motion *m) { (void)w; (void)m; abort(); }
static struct parts_motion *load_parts_motion(struct iarray_reader *r) { (void)r; abort(); }
'''

TEST = r'''
static void roundtrip(int version) {
    program.version=version; rebuilds=0;
    struct page *buf=NULL;
    assert(!PE_SaveParts(7,&buf)); assert(!buf);
    assert(!PE_LoadParts(2,&buf)); assert(!PE_LoadParts(2,NULL));
    struct parts *src=parts_get(1), *parent=parts_get(3), *child=parts_get(4);
    src->parent=parent; TAILQ_INSERT_TAIL(&src->children,child,child_list_entry);
    src->comment=make_string("button",6); src->component_type=0;
    src->widget=calloc(1,sizeof(*src->widget));
    src->widget->enabled=true; src->widget->width=321; src->widget->height=45;
    src->widget->checked=true; src->widget->button_mode=true;
    src->widget->text=make_string("label",5);
    src->local.pos.x=17.5f; src->local.scale.x=1.25f; src->local.alpha=123;
    src->reverse_lr=true; src->state=2;
    for(int i=0;i<3;i++) { src->states[i].type=PARTS_CG; src->states[i].common.w=50+i; }
    assert(PE_SaveParts(1,&buf));
    assert(buf->a_type==(version>=11 ? AIN_ARRAY : AIN_ARRAY_INT));
    int source_no=buf->values[6].i;
    assert(PE_LoadParts(2,&buf));
    struct parts *dst=parts_get(2);
    assert(dst->no==2 && dst->parent==NULL && dst->pending_parent==-1);
    assert(TAILQ_EMPTY(&dst->children) && TAILQ_FIRST(&src->children)==child);
    assert(src->parent==parent && buf->values[6].i==source_no);
    assert(dst->widget && dst->component_type==0 && PE_GetSpecialComponentType(2)==0);
    assert(PartsEngine_GetComponentType_v12(2,1)==0);
    assert(dst->widget->width==321 && dst->widget->height==45);
    assert(dst->widget->checked && dst->widget->button_mode);
    assert(!strcmp(dst->widget->text->text,"label"));
    assert(!strcmp(dst->comment->text,"button"));
    assert(dst->local.pos.x==17.5f && dst->local.scale.x==1.25f && dst->local.alpha==123);
    assert(dst->reverse_lr && dst->state==2);
    for(int i=0;i<3;i++) assert(dst->states[i].common.w==50+i);
    assert(PE_LoadParts(2,&buf)); assert(rebuilds==2);
    assert(PE_SaveParts(1,&buf));
    int save_version=buf->values[4].i; buf->values[4].i=save_version+1;
    assert(!PE_LoadParts(2,&buf)); assert(parts_try_get(2)); buf->values[4].i=save_version;
    buf->nr_vars--; assert(!PE_LoadParts(2,&buf)); buf->nr_vars++;
    buf->array.rank=2; assert(!PE_LoadParts(2,&buf)); buf->array.rank=1;
    int atype=buf->a_type; buf->a_type=AIN_ARRAY_FLOAT;
    assert(!PE_LoadParts(2,&buf)); buf->a_type=atype;
    if(atype==AIN_ARRAY) {
        buf->array.element_type.data=AIN_FLOAT; assert(!PE_LoadParts(2,&buf));
        buf->array.element_type.data=AIN_INT;
    }
    int count=buf->nr_vars; buf->nr_vars=0; assert(!PE_LoadParts(2,&buf)); buf->nr_vars=count;
    buf->values[0].i='!'; assert(!PE_LoadParts(2,&buf));
    assert(!PE_LoadParts(0,&buf));
    free_page(buf); for(int i=0;i<8;i++) parts_release(i);
    assert(updates>=4);
}
int main(void) {
    roundtrip(10); roundtrip(12); roundtrip(14);
    puts("single-part save/load: legacy, AIN v12 and v14 roundtrips and invalid buffers: OK");
}
'''


class PartsCloneTests(unittest.TestCase):
    def test_single_part_roundtrip(self):
        names = ["save_parts_params", "load_parts_params", "save_parts", "load_parts",
                 "PE_SaveParts", "PE_LoadParts"]
        code = FIXTURE
        for path, funcs in [
            ("src/page.c", ["array_type", "unref_array_type", "alloc_array"]),
            ("src/vm_v12.c", ["type_stride", "v12_array_new"]),
            ("src/hll/iarray.c", ["iarray_to_page"]),
        ]:
            code += "\n".join(function(path, n) for n in funcs)
        code += "\n".join(function("src/parts/save.c", n) for n in names)
        code += function("src/parts/activity.c", "PE_GetSpecialComponentType")
        code += function("src/hll/PartsEngine.c", "PartsEngine_GetComponentType_v12") + TEST
        flags = subprocess.check_output(["pkg-config", "--cflags", "sdl2"], text=True).split()
        with tempfile.TemporaryDirectory() as tmp:
            src, exe = Path(tmp) / "test.c", Path(tmp) / "test"
            src.write_text(code)
            subprocess.run(["cc", "-std=c11", "-D_DEFAULT_SOURCE", "-DUSE_GLES", "-Wall", "-Wextra",
                            "-Werror", "-Wno-misleading-indentation", *flags,
                            f"-I{ROOT / 'include'}", f"-I{ROOT / 'src'}",
                            f"-I{ROOT / 'subprojects/libsys4/include'}", str(src), "-o", str(exe)], check=True)
            subprocess.run([str(exe)], check=True)


if __name__ == "__main__":
    unittest.main()
