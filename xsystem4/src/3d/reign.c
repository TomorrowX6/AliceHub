/* Copyright (C) 2022 kichikuou <KichikuouChrome@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://gnu.org/licenses/>.
 */

#include <assert.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <cglm/cglm.h>

#include "system4.h"
#include "system4/aar.h"
#include "system4/archive.h"
#include "system4/cg.h"
#include "system4/hashtable.h"
#include "system4/string.h"

#include "3d_internal.h"
#include "asset_manager.h"
#include "reign.h"
#include "sact.h"
#include "sprite.h"
#include "vm.h"
#include "xsystem4.h"
#include "../hll/iarray.h"

struct instance_mesh_vis {
	const struct RE_instance *inst;
	int nr_meshes;
	uint8_t *hidden;
};

static struct instance_mesh_vis *mesh_vis_entries;
static int nr_mesh_vis_entries;
static int cap_mesh_vis_entries;

bool RE_instance_is_mesh_hidden(const struct RE_instance *inst, int mesh_index)
{
	if (!inst || mesh_index < 0)
		return false;
	for (int i = 0; i < nr_mesh_vis_entries; i++) {
		if (mesh_vis_entries[i].inst == inst)
			return mesh_index < mesh_vis_entries[i].nr_meshes && mesh_vis_entries[i].hidden[mesh_index];
	}
	return false;
}

static void set_instance_mesh_hidden(struct RE_instance *inst, int mesh_index, bool hidden)
{
	if (!inst || !inst->model || mesh_index < 0 || mesh_index >= inst->model->nr_meshes)
		return;
	struct instance_mesh_vis *entry = NULL;
	for (int i = 0; i < nr_mesh_vis_entries; i++) {
		if (mesh_vis_entries[i].inst == inst) {
			entry = &mesh_vis_entries[i];
			break;
		}
	}
	if (!entry) {
		if (!hidden)
			return;
		if (nr_mesh_vis_entries == cap_mesh_vis_entries) {
			int new_cap = cap_mesh_vis_entries ? cap_mesh_vis_entries * 2 : 16;
			mesh_vis_entries = xrealloc_array(mesh_vis_entries, cap_mesh_vis_entries, new_cap, sizeof(*mesh_vis_entries));
			cap_mesh_vis_entries = new_cap;
		}
		entry = &mesh_vis_entries[nr_mesh_vis_entries++];
		entry->inst = inst;
		entry->nr_meshes = inst->model->nr_meshes;
		entry->hidden = xcalloc(entry->nr_meshes, 1);
	} else if (mesh_index >= entry->nr_meshes) {
		entry->hidden = xrealloc_array(entry->hidden, entry->nr_meshes, inst->model->nr_meshes, 1);
		entry->nr_meshes = inst->model->nr_meshes;
	}
	entry->hidden[mesh_index] = hidden ? 1 : 0;
}

static void clear_instance_mesh_hidden(const struct RE_instance *inst)
{
	for (int i = 0; i < nr_mesh_vis_entries; i++) {
		if (mesh_vis_entries[i].inst == inst) {
			free(mesh_vis_entries[i].hidden);
			mesh_vis_entries[i] = mesh_vis_entries[--nr_mesh_vis_entries];
			if (nr_mesh_vis_entries == 0) {
				free(mesh_vis_entries);
				mesh_vis_entries = NULL;
				cap_mesh_vis_entries = 0;
			}
			return;
		}
	}
}

static struct RE_instance *create_instance(struct RE_plugin *plugin)
{
	struct RE_instance *instance = xcalloc_aligned(1, struct RE_instance);
	instance->plugin = plugin;
	instance->draw = plugin->version >= RE_SEAL_PLUGIN;
	instance->draw_edge = plugin->version == RE_TAPIR_PLUGIN;
	for (int i = 0; i < RE_NR_INSTANCE_TARGETS; i++)
		instance->target[i] = -1;
	glm_vec3_one(instance->scale);
	instance->alpha = 1.0f;
	glm_vec3_one(instance->diffuse);
	instance->shadow_volume_bone_radius = 0.1f;
	instance->draw_bump = true;
	instance->fps = 30.0f;
	instance->use_mag_speed = true;
	instance->column_height = 1.0f;
	instance->column_radius = 1.0f;
	glm_vec3_copy((vec3){-1.0f, 2.0f, 0.0f}, instance->vertex_pos[0]);
	glm_vec3_copy((vec3){-1.0f, 0.0f, 0.0f}, instance->vertex_pos[1]);
	glm_vec3_copy((vec3){ 1.0f, 2.0f, 0.0f}, instance->vertex_pos[2]);
	glm_vec3_copy((vec3){ 1.0f, 0.0f, 0.0f}, instance->vertex_pos[3]);
	glm_vec2_copy((vec2){0.0f, 0.0f}, instance->vertex_uv[0]);
	glm_vec2_copy((vec2){0.0f, 1.0f}, instance->vertex_uv[1]);
	glm_vec2_copy((vec2){1.0f, 0.0f}, instance->vertex_uv[2]);
	glm_vec2_copy((vec2){1.0f, 1.0f}, instance->vertex_uv[3]);
	glm_mat4_identity(instance->local_transform);
	glm_mat3_identity(instance->normal_transform);
	return instance;
}

static void free_instance(struct RE_instance *instance);

static void unload_instance(struct RE_instance *instance)
{
	clear_instance_mesh_hidden(instance);
	// A billboard's motion holds frame animation state that the game can set
	// before loading the instance data, so it must survive unloads.
	if (instance->motion && instance->type != RE_ITYPE_BILLBOARD) {
		motion_free(instance->motion);
		instance->motion = NULL;
	}
	if (instance->next_motion) {
		motion_free(instance->next_motion);
		instance->next_motion = NULL;
	}
	if (instance->model) {
		// No need to free, model is owned by plugin->model_cache.
		instance->model = NULL;
	}
	if (instance->effect) {
		particle_effect_free(instance->effect);
		instance->effect = NULL;
	}
	if (instance->s3de_effect) {
		s3de_effect_free(instance->s3de_effect);
		instance->s3de_effect = NULL;
	}
	if (instance->bone_transforms) {
		glDeleteBuffers(1, &instance->bone_transforms_ubo);
		free(instance->bone_transforms);
		instance->bone_transforms = NULL;
	}
	if (instance->billboard_frames) {
		// No need to free the textures, they are owned by the renderer.
		free(instance->billboard_frames);
		instance->billboard_frames = NULL;
		instance->nr_billboard_frames = 0;
	}
	if (instance->height_detector) {
		RE_renderer_free_height_detector(instance->height_detector);
		instance->height_detector = NULL;
	}
	if (instance->shadow_volume_instance) {
		free_instance(instance->shadow_volume_instance);
		instance->shadow_volume_instance = NULL;
	}
	free(instance->load_name);
	instance->load_name = NULL;
}

static void free_instance(struct RE_instance *instance)
{
	unload_instance(instance);
	if (instance->motion)
		motion_free(instance->motion);
	free(instance->light_params);
	xfree_aligned(instance);
}

static void RE_back_cg_init(struct RE_back_cg *bcg)
{
	bcg->blend_rate = 1.0;
	bcg->mag = 1.0;
}

static void RE_back_cg_destroy(struct RE_back_cg *bcg)
{
	gfx_delete_texture(&bcg->texture);
	if (bcg->name)
		free_string(bcg->name);
}

static void interpolate_motion_frame(struct mot_frame *m1, struct mot_frame *m2, float t, struct mot_frame *out)
{
	glm_vec3_lerp(m1->pos, m2->pos, t, out->pos);
	glm_quat_nlerp(m1->rotq, m2->rotq, t, out->rotq);
}

static void update_motion(struct motion *m, float delta_frame)
{
	if (!m || m->state == RE_MOTION_STATE_STOP)
		return;
	m->current_frame += delta_frame;
	if (m->current_frame < m->frame_begin)
		m->current_frame = m->frame_begin;
	switch (m->state) {
	case RE_MOTION_STATE_NOLOOP:
		if (m->current_frame > m->frame_end) {
			m->current_frame = m->frame_end;
			m->state = RE_MOTION_STATE_STOP;
		}
		break;
	case RE_MOTION_STATE_LOOP:
		if (m->loop_frame_end <= m->loop_frame_begin)
			m->current_frame = m->loop_frame_begin;
		else if (m->current_frame > m->loop_frame_end)
			m->current_frame = m->loop_frame_begin + fmodf(m->current_frame - m->loop_frame_begin, m->loop_frame_end - m->loop_frame_begin);
		break;
	default:
		VM_ERROR("Invalid motion state %d", m->state);
	}

	float anim_f = m->current_frame - m->frame_begin;
	uint32_t anim_frame = anim_f >= 0.0f ? (uint32_t)anim_f : 0;
	m->texture_index = (m->mot && anim_frame < m->mot->nr_texture_indices)
		? m->mot->texture_indices[anim_frame] : 0;
}

static void calc_motion_frame(struct motion *m, int bone, struct mot_frame *out)
{
	if (!m->mot || m->mot->nr_frames == 0) {
		glm_vec3_zero(out->pos);
		glm_quat_identity(out->rotq);
		return;
	}
	float cf = m->current_frame >= 0.0f ? m->current_frame : 0.0f;
	uint32_t cur_frame = (uint32_t)cf;
	uint32_t next_frame = (uint32_t)ceilf(cf);
	if (next_frame >= m->mot->nr_frames) {
		*out = m->mot->motions[bone]->frames[m->mot->nr_frames - 1];
		return;
	}
	float t = cf - cur_frame;
	struct mot_frame *frames = m->mot->motions[bone]->frames;
	interpolate_motion_frame(&frames[cur_frame], &frames[next_frame], t, out);
}

static void update_bones(struct RE_instance *inst)
{
	if (!inst->motion || !inst->model || !inst->bone_transforms)
		return;

	mat4 parent_transforms[MAX_BONES];
	vec3 aabb[2];
	glm_aabb_invalidate(aabb);

	// Update inst->bone_transforms.
	for (int i = 0; i < inst->model->nr_bones; i++) {
		struct mot_frame mf;
		calc_motion_frame(inst->motion, i, &mf);
		if (inst->motion_blend && inst->next_motion) {
			struct mot_frame next_mf;
			calc_motion_frame(inst->next_motion, i, &next_mf);
			interpolate_motion_frame(&mf, &next_mf, inst->motion_blend_rate, &mf);
		}
		mat4 bone_transform;
		glm_translate_make(bone_transform, mf.pos);
		glm_quat_rotate(bone_transform, mf.rotq, bone_transform);

		if (inst->model->bones[i].parent >= 0) {
			assert(inst->model->bones[i].parent < i);
			glm_mat4_mul(parent_transforms[inst->model->bones[i].parent], bone_transform, bone_transform);
		}
		glm_mat4_copy(bone_transform, parent_transforms[i]);

		glm_vec3_minv(aabb[0], bone_transform[3], aabb[0]);
		glm_vec3_maxv(aabb[1], bone_transform[3], aabb[1]);

		glm_mat4_mul(bone_transform, inst->model->bones[i].inverse_bind_matrix, bone_transform);
		glm_mat4_transpose(bone_transform);  // column major -> row major
		glm_mat3x4_copy(bone_transform, inst->bone_transforms[i]);
	}
	glBindBuffer(GL_UNIFORM_BUFFER, inst->bone_transforms_ubo);
	glBufferSubData(GL_UNIFORM_BUFFER, 0, inst->model->nr_bones * sizeof(mat3x4), inst->bone_transforms);
	glBindBuffer(GL_UNIFORM_BUFFER, 0);

	// Update inst->bounding_sphere.
	vec3 center;
	glm_aabb_center(aabb, center);
	if (inst->local_transform_needs_update)
		RE_instance_update_local_transform(inst);
	glm_mat4_mulv3(inst->local_transform, center, 1.0f, inst->bounding_sphere);
	inst->bounding_sphere[3] = glm_aabb_radius(aabb) * inst->scale[0] + inst->shadow_volume_bone_radius;

	struct RE_instance *shvol = inst->shadow_volume_instance;
	if (shvol) {
		glm_vec3_copy(inst->bounding_sphere, shvol->pos);
		glm_vec3_broadcast(inst->bounding_sphere[3], shvol->scale);
		RE_instance_update_local_transform(shvol);
	}
}

static void init_instance_bone_ubo(struct RE_instance *instance)
{
	glGenBuffers(1, &instance->bone_transforms_ubo);
	glBindBuffer(GL_UNIFORM_BUFFER, instance->bone_transforms_ubo);
	glBufferData(GL_UNIFORM_BUFFER, MAX_BONES * sizeof(mat3x4), NULL, GL_DYNAMIC_DRAW);
	glBindBuffer(GL_UNIFORM_BUFFER, 0);
}

static void reset_cached_model_cb(struct ht_slot *slot, void *data)
{
	struct RE_plugin *plugin = data;
	model_reset_gl((struct model *)slot->value, plugin->aar, plugin->version);
}

static void reset_cached_effect_cb(struct ht_slot *slot, void *data)
{
	struct RE_plugin *plugin = data;
	if (!slot->value)
		return;
	if (plugin->version < RE_SEAL_PLUGIN)
		pae_reset_gl((struct pae *)slot->value, plugin->aar, plugin->version);
	else
		s3de_reset_gl((struct s3de *)slot->value, plugin->aar);
}

static void reset_instance_gl(struct RE_instance *inst, struct archive *aar, enum RE_plugin_version version)
{
	if (!inst)
		return;
	if (inst->bone_transforms) {
		inst->bone_transforms_ubo = 0;
		init_instance_bone_ubo(inst);
	}
	if (inst->shadow_volume_instance) {
		reset_instance_gl(inst->shadow_volume_instance, aar, version);
		if (inst->shadow_volume_instance->model)
			model_reset_gl(inst->shadow_volume_instance->model, aar, version);
	}
}

static void re_plugin_on_gl_reset(void *user)
{
	struct RE_plugin *plugin = user;
	if (!plugin)
		return;
	if (plugin->model_cache)
		ht_foreach(plugin->model_cache, reset_cached_model_cb, plugin);
	if (plugin->effect_cache)
		ht_foreach(plugin->effect_cache, reset_cached_effect_cb, plugin);
	for (int i = 0; i < plugin->nr_instances; i++)
		reset_instance_gl(plugin->instances[i], plugin->aar, plugin->version);
	for (int i = 0; i < RE_NR_BACK_CGS; i++) {
		struct RE_back_cg *bcg = &plugin->back_cg[i];
		if (!bcg->texture.handle || gfx_remap_texture(&bcg->texture))
			continue;
		if (bcg->name && bcg->name->size > 0) {
			struct string *name = string_ref(bcg->name);
			RE_back_cg_set_name(bcg, name, plugin->aar);
			free_string(name);
		} else if (bcg->no) {
			RE_back_cg_set(bcg, bcg->no);
		}
	}
	if (plugin->renderer)
		RE_renderer_reload_billboard_textures_by_path(plugin->renderer, plugin->aar);
}

struct RE_plugin *RE_plugin_new(enum RE_plugin_version version)
{
	re_debug_init();

	char *aar_path = gamedir_path(version <= RE_TAPIR_PLUGIN ? "Data/ReignData.red" : "Data/3DData.red");
	int error = ARCHIVE_SUCCESS;
	struct archive *aar = (struct archive *)aar_open(aar_path, MMAP_IF_64BIT, &error);
	if (error == ARCHIVE_FILE_ERROR) {
		WARNING("aar_open(\"%s\"): %s", display_utf0(aar_path), strerror(errno));
	} else if (error == ARCHIVE_BAD_ARCHIVE_ERROR) {
		WARNING("aar_open(\"%s\"): invalid AAR archive", display_utf0(aar_path));
	}
	free(aar_path);
	if (!aar)
		return NULL;

	struct RE_plugin *plugin = xcalloc_aligned(1, struct RE_plugin);
	plugin->version = version;
	plugin->plugin.name = "ReignEngine";
	plugin->plugin.update = RE_render;
	plugin->plugin.to_json = RE_to_json;
	plugin->nr_instances = 16;
	plugin->instances = xcalloc(plugin->nr_instances, sizeof(struct RE_instance *));
	plugin->aar = aar;
	plugin->model_cache = ht_create(32);
	plugin->effect_cache = ht_create(32);
	for (int i = 0; i < RE_NR_BACK_CGS; i++)
		RE_back_cg_init(&plugin->back_cg[i]);
	plugin->mag_speed = 1;
	if (version == RE_TAPIR_PLUGIN)
		plugin->draw_options[RE_DRAW_OPTION_EDGE] = 1;
	plugin->draw_options[RE_DRAW_OPTION_LIGHTING] = 1;
	plugin->edge_length = 0.02f;
	plugin->fog_type = RE_FOG_NONE;
	plugin->fog_near = 1.0f;
	plugin->fog_far = 10.0f;
	glm_vec3_one(plugin->fog_color);
	lit_reset(plugin->light_params);
	gfx_register_reset_callback(re_plugin_on_gl_reset, plugin);
	return plugin;
}

void RE_plugin_free(struct RE_plugin *plugin)
{
	if (!plugin)
		return;
	gfx_unregister_reset_callback(re_plugin_on_gl_reset, plugin);
	RE_plugin_unbind(plugin);
	for (int i = 0; i < plugin->nr_instances; i++) {
		if (plugin->instances[i])
			free_instance(plugin->instances[i]);
	}
	free(plugin->instances);
	archive_free(plugin->aar);
	ht_foreach_value(plugin->model_cache, (void(*)(void*))model_free);
	ht_free(plugin->model_cache);
	ht_foreach_value(plugin->effect_cache, plugin->version < RE_SEAL_PLUGIN ?
		(void(*)(void*))pae_free : (void(*)(void*))s3de_free);
	ht_free(plugin->effect_cache);
	for (int i = 0; i < RE_NR_BACK_CGS; i++)
		RE_back_cg_destroy(&plugin->back_cg[i]);
	if (plugin->collider)
		collider_free(plugin->collider);
	xfree_aligned(plugin);
}

void RE_plugin_detach_sprite(struct draw_plugin *dp)
{
	if (!dp)
		return;
	struct RE_plugin *plugin = (struct RE_plugin *)((char *)dp - offsetof(struct RE_plugin, plugin));
	if (plugin->renderer) {
		struct RE_renderer *r = plugin->renderer;
		plugin->renderer = NULL;
		RE_renderer_free(r);
	}
	plugin->sprite = 0;
}

bool RE_plugin_bind(struct RE_plugin *plugin, int sprite)
{
	if (!plugin)
		return false;
	struct sact_sprite *sp = sact_try_get_sprite(sprite);
	if (!sp)
		return false;
	if (plugin->renderer)
		RE_plugin_unbind(plugin);
	sprite_bind_plugin(sp, NULL);
	plugin->renderer = RE_renderer_new(plugin->version);
	plugin->sprite = sprite;
	sprite_bind_plugin(sp, &plugin->plugin);
	struct texture *texture = sprite_get_texture(sp);
	RE_renderer_set_viewport_size(plugin->renderer, texture->w, texture->h);
	return true;
}

bool RE_plugin_unbind(struct RE_plugin *plugin)
{
	if (!plugin || !plugin->renderer)
		return false;
	struct RE_renderer *r = plugin->renderer;
	int sp_no = plugin->sprite;
	plugin->renderer = NULL;
	plugin->sprite = 0;
	struct sact_sprite *sp = sact_try_get_sprite(sp_no);
	if (sp && sp->plugin == &plugin->plugin)
		sprite_bind_plugin(sp, NULL);
	RE_renderer_free(r);
	return true;
}

bool RE_plugin_suspend(struct RE_plugin *plugin)
{
	if (!plugin)
		return false;
	if (!plugin->suspended) {
		struct sact_sprite *sp = sact_try_get_sprite(plugin->sprite);
		plugin->resume_show_sprite = sp && !sp->sp.hidden;
		if (sp)
			sprite_set_show(sp, false);
	}
	plugin->suspended = true;
	return true;
}

bool RE_plugin_resume(struct RE_plugin *plugin)
{
	if (!plugin)
		return false;
	if (plugin->suspended && plugin->resume_show_sprite) {
		struct sact_sprite *sp = sact_try_get_sprite(plugin->sprite);
		if (sp)
			sprite_set_show(sp, true);
	}
	plugin->resume_show_sprite = false;
	plugin->suspended = false;
	return true;
}

bool RE_build_model(struct RE_plugin *plugin, int elapsed_ms)
{
	if (!plugin)
		return false;

	plugin->camera.quake_pitch = plugin->camera.quake_yaw = 0.0;
	plugin->camera.override_active = false;

	for (int i = 0; i < plugin->nr_instances; i++) {
		struct RE_instance *inst = plugin->instances[i];
		if (!inst)
			continue;

		float delta_frame = inst->fps * elapsed_ms / 1000.0;
		if (inst->use_mag_speed)
			delta_frame *= plugin->mag_speed;
		update_motion(inst->motion, delta_frame);
		update_motion(inst->next_motion, delta_frame);

		if (inst->type == RE_ITYPE_SKINNED)
			update_bones(inst);
		inst->texture_animation_index = inst->motion ? inst->motion->texture_index : 0;
	}

	// Update particle effects in a second pass, because it uses the results of
	// update_bones().
	for (int i = 0; i < plugin->nr_instances; i++) {
		struct RE_instance *inst = plugin->instances[i];
		if (!inst || inst->type != RE_ITYPE_PARTICLE_EFFECT)
			continue;
		if (inst->s3de_effect)
			s3de_effect_update(inst);
		else
			particle_effect_update(inst);
	}
	return true;
}

bool RE_set_viewport(struct RE_plugin *plugin, int x, int y, int width, int height)
{
	if (!plugin)
		return false;
	struct sact_sprite *sp = sact_try_get_sprite(plugin->sprite);
	if (!sp || sp->plugin != &plugin->plugin) {
		WARNING("No sprite bound to plugin");
		return false;
	}
	sprite_init_color(sp, width, height, 0, 0, 0, 255);
	sprite_set_pos(sp, x, y);
	RE_renderer_set_viewport_size(plugin->renderer, width, height);
	return true;
}

bool RE_set_projection(struct RE_plugin *plugin, float width, float height, float near, float far, float deg)
{
	if (!plugin || height == 0.0f)
		return false;

	float aspect = width / height;
	glm_perspective(glm_rad(deg), aspect, near, far, plugin->proj_transform);

	// Adjust the X and Y scaling factor, because ReignEngine calculates the
	// projection matrix based on the horizontal FOV while glm_perspective takes
	// the vertical FOV.
	plugin->proj_transform[0][0] *= aspect;
	plugin->proj_transform[1][1] *= aspect;
	return true;
}

int RE_create_instance(struct RE_plugin *plugin)
{
	if (!plugin)
		return -1;
	for (int i = 0; i < plugin->nr_instances; i++) {
		if (!plugin->instances[i]) {
			plugin->instances[i] = create_instance(plugin);
			return i;
		}
	}
	int old_size = plugin->nr_instances;
	plugin->nr_instances *= 2;
	plugin->instances = xrealloc_array(plugin->instances, old_size, plugin->nr_instances, sizeof(struct RE_instance *));
	plugin->instances[old_size] = create_instance(plugin);
	return old_size;
}

bool RE_release_instance(struct RE_plugin *plugin, int instance)
{
	if (!plugin || (unsigned)instance >= (unsigned)plugin->nr_instances || !plugin->instances[instance])
		return false;
	free_instance(plugin->instances[instance]);
	plugin->instances[instance] = NULL;
	return true;
}

bool RE_instance_set_type(struct RE_instance *instance, int type)
{
	if (!instance)
		return false;
	if (instance->type != RE_ITYPE_UNINITIALIZED && instance->type != (enum RE_instance_type)type)
		ERROR("instance type cannot be changed");
	switch (type) {
	case RE_ITYPE_STATIC:
		break;
	case RE_ITYPE_SKINNED:
		break;
	case RE_ITYPE_BILLBOARD:
		if (!instance->motion) {
			instance->motion = xcalloc(1, sizeof(struct motion));
			instance->motion->instance = instance;
		}
		break;
	case RE_ITYPE_DIRECTIONAL_LIGHT:
		if (instance->plugin && instance->plugin->version >= RE_SEAL_PLUGIN && !instance->light_params) {
			instance->light_params = xmalloc(RE_NR_LIGHT_PARAMS * sizeof(float));
			lit_reset(instance->light_params);
		}
		break;
	case RE_ITYPE_SPECULAR_LIGHT:
		break;
	case RE_ITYPE_PARTICLE_EFFECT:
		break;
	case RE_ITYPE_PATH_LINE:
		break;
	default:
		VM_ERROR("unknown instance type %d", type);
	}
	instance->type = type;
	return true;
}

bool RE_instance_data_exists(struct RE_instance *instance, const char *name)
{
	if (!instance || !name)
		return false;
	const char *ext;
	switch (instance->type) {
	case RE_ITYPE_STATIC:
	case RE_ITYPE_SKINNED:
		ext = ".POL";
		break;
	case RE_ITYPE_PARTICLE_EFFECT:
		ext = instance->plugin && instance->plugin->version >= RE_SEAL_PLUGIN ? ".3de" : ".pae";
		break;
	default:
		if (archive_exists_by_name(instance->plugin->aar, name, NULL))
			return true;
		if (!strncasecmp(name, "Data\\", 5))
			return archive_exists_by_name(instance->plugin->aar, name + 4, NULL);
		return false;
	}
	const char *basename = strrchr(name, '\\');
	basename = basename ? basename + 1 : name;
	char *path = xmalloc(strlen(name) + strlen(basename) + strlen(ext) + 2);
	sprintf(path, "%s\\%s%s", name, basename, ext);
	bool exists = archive_exists_by_name(instance->plugin->aar, path, NULL);
	free(path);
	return exists;
}

// Loads the frame images of a billboard instance. They are stored in the
// archive as "<name>\<basename>.png", "<name>\<basename>[1].png", ...
static bool load_billboard_frames(struct RE_instance *instance, const char *name)
{
	if (!instance->plugin || !instance->plugin->renderer)
		return false;
	const char *basename = strrchr(name, '\\');
	basename = basename ? basename + 1 : name;
	char *path = xmalloc(strlen(name) + strlen(basename) + 22);

	int capacity = 8;
	instance->billboard_frames = xcalloc(capacity, sizeof(struct billboard_texture *));
	for (int i = 0;; i++) {
		if (i == 0)
			sprintf(path, "%s\\%s.png", name, basename);
		else
			sprintf(path, "%s\\%s[%d].png", name, basename, i);
		struct billboard_texture *bt = RE_renderer_load_billboard_texture_by_path(
			instance->plugin->renderer, instance->plugin->aar, path);
		if (!bt)
			break;
		if (i == capacity) {
			instance->billboard_frames = xrealloc_array(
				instance->billboard_frames, capacity, capacity * 2, sizeof(struct billboard_texture *));
			capacity *= 2;
		}
		instance->billboard_frames[i] = bt;
		instance->nr_billboard_frames = i + 1;
	}
	free(path);
	return instance->nr_billboard_frames > 0;
}

bool RE_instance_load(struct RE_instance *instance, const char *name)
{
	if (!instance || !instance->plugin)
		return false;
	enum RE_plugin_version version = instance->plugin->version;
	char *name_copy = name ? xstrdup(name) : NULL;
	unload_instance(instance);
	if (!name_copy || name_copy[0] == '\0') {
		free(name_copy);
		return true;  // empty name just unloads the instance
	}
	instance->load_name = name_copy;
	name = name_copy;

	switch (instance->type) {
	case RE_ITYPE_STATIC:
	case RE_ITYPE_SKINNED:
		instance->model = ht_get(instance->plugin->model_cache, name, NULL);
		if (!instance->model) {
			instance->model = model_load(instance->plugin->aar, name, version);
			if (instance->model)
				ht_put(instance->plugin->model_cache, name, instance->model);
		}
		if (instance->model && instance->model->nr_bones > 0) {
			instance->bone_transforms = xcalloc(instance->model->nr_bones, sizeof(mat3x4));
			init_instance_bone_ubo(instance);
		}
		return !!instance->model;
	case RE_ITYPE_PARTICLE_EFFECT:
		if (version >= RE_SEAL_PLUGIN) {
			struct s3de *s3de = ht_get(instance->plugin->effect_cache, name, NULL);
			if (!s3de) {
				s3de = s3de_load(instance->plugin->aar, name);
				if (!s3de)
					return false;
				ht_put(instance->plugin->effect_cache, name, s3de);
			}
			instance->s3de_effect = s3de_effect_create(s3de, instance->plugin->aar);
			if (!instance->motion) {
				instance->motion = xcalloc(1, sizeof(struct motion));
				instance->motion->instance = instance;
				s3de_calc_frame_range(instance->s3de_effect->s3de, instance->motion);
			}
		} else {
			struct pae *pae = ht_get(instance->plugin->effect_cache, name, NULL);
			if (!pae) {
				pae = pae_load(instance->plugin->aar, name, version);
				if (!pae)
					return false;
				ht_put(instance->plugin->effect_cache, name, pae);
			}
			instance->effect = particle_effect_create(pae);
			if (!instance->motion) {
				instance->motion = xcalloc(1, sizeof(struct motion));
				instance->motion->instance = instance;
				pae_calc_frame_range(instance->effect->pae, instance->motion);
			}
		}
		return true;
	case RE_ITYPE_BILLBOARD:
		if (version >= RE_SEAL_PLUGIN)
			return load_billboard_frames(instance, name);
		return false;
	case RE_ITYPE_DIRECTIONAL_LIGHT:
		if (version >= RE_SEAL_PLUGIN && instance->light_params) {
			struct archive_data *dfile = archive_get_by_name(instance->plugin->aar, name);
			if (!dfile && !strncasecmp(name, "Data\\", 5))
				dfile = archive_get_by_name(instance->plugin->aar, name + 4);
			if (!dfile)
				return false;
			bool ok = lit_parse(dfile->data, dfile->size, instance->light_params);
			archive_free_data(dfile);
			return ok;
		}
		return false;
	default:
		WARNING("Invalid instance type %d", instance->type);
		return false;
	}
}

bool RE_instance_motion_exists(struct RE_instance *instance, const char *name)
{
	if (!instance || !instance->model || !name)
		return false;
	if (instance->model->mot_cache && ht_get(instance->model->mot_cache, name, NULL))
		return true;
	char *path = xmalloc(strlen(instance->model->path) + strlen(name) + 6);
	sprintf(path, "%s\\%s.MOT", instance->model->path, name);
	bool exists = archive_exists_by_name(instance->plugin->aar, path, NULL);
	free(path);
	return exists;
}

bool RE_instance_load_motion(struct RE_instance *instance, const char *name)
{
	if (!instance || !name)
		return false;
	if (instance->motion)
		motion_free(instance->motion);
	instance->motion = motion_load(name, instance, instance->plugin->aar);
	return !!instance->motion;
}

bool RE_instance_load_next_motion(struct RE_instance *instance, const char *name)
{
	if (!instance || !name)
		return false;
	if (instance->next_motion)
		motion_free(instance->next_motion);
	instance->next_motion = motion_load(name, instance, instance->plugin->aar);
	return !!instance->next_motion;
}

bool RE_instance_free_next_motion(struct RE_instance *instance)
{
	if (!instance || !instance->next_motion)
		return false;
	motion_free(instance->next_motion);
	instance->next_motion = NULL;
	return true;
}

bool RE_instance_set_mesh_show(struct RE_instance *instance, const char *mesh_name, bool show)
{
	if (!instance || !instance->model || !mesh_name)
		return false;
	bool found = false;
	for (int i = 0; i < instance->model->nr_meshes; i++) {
		if (!strcmp(instance->model->meshes[i].name, mesh_name)) {
			set_instance_mesh_hidden(instance, i, !show);
			found = true;
		}
	}
	return found;
}

// Converts from the (top-left, top-right, bottom-left, bottom-right) order used
// by the API to the vertex order of the billboard mesh.
static const int billboard_vertex_index[4] = {0, 2, 1, 3};

bool RE_instance_set_vertex_pos(struct RE_instance *instance, int index, float x, float y, float z)
{
	if (!instance || (unsigned)index >= 4)
		return false;
	if (instance->type != RE_ITYPE_BILLBOARD)
		ERROR("not implemented");
	glm_vec3_copy((vec3){x, y, z}, instance->vertex_pos[billboard_vertex_index[index]]);
	return true;
}

bool RE_instance_set_vertex_uv(struct RE_instance *instance, int index, float u, float v)
{
	if (!instance || (unsigned)index >= 4)
		return false;
	if (instance->type != RE_ITYPE_BILLBOARD)
		ERROR("not implemented");
	glm_vec2_copy((vec2){u, v}, instance->vertex_uv[billboard_vertex_index[index]]);
	return true;
}

int RE_instance_get_bone_index(struct RE_instance *instance, const char *name)
{
	if (!instance || !instance->model || !name)
		return -1;
	for (int i = 0; i < instance->model->nr_bones; i++) {
		if (!strcmp(instance->model->bones[i].name, name))
			return i;
	}
	return -1;
}

bool RE_instance_trans_local_pos_to_world_pos_by_bone(struct RE_instance *instance, int bone, vec3 offset, vec3 out)
{
	if (!instance || !instance->model || !instance->bone_transforms || (unsigned)bone >= (unsigned)instance->model->nr_bones)
		return false;

	if (instance->local_transform_needs_update)
		RE_instance_update_local_transform(instance);

	mat4 bone_transform = GLM_MAT4_IDENTITY_INIT;
	glm_mat3x4_copy(instance->bone_transforms[bone], bone_transform);
	glm_mat4_transpose(bone_transform);  // row major -> column major
	glm_mat4_mulv3(bone_transform, offset, 1.0, out);
	glm_mat4_mulv3(instance->local_transform, out, 1.0, out);
	return true;
}

bool RE_instance_calc_height_ex(struct RE_instance *instance, float x, float z, float *y_out)
{
	if (!instance || !instance->model)
		return false;

	float sx = instance->scale[0] != 0.0f ? instance->scale[0] : 1.0f;
	float sy = instance->scale[1] != 0.0f ? instance->scale[1] : 1.0f;
	float sz = instance->scale[2] != 0.0f ? instance->scale[2] : 1.0f;
	float lx = (x - instance->pos[0]) / sx;
	float lz = (z - instance->pos[2]) / sz;

	float y;
	if (instance->model->collider && collider_height(instance->model->collider, (vec2){ lx, lz }, &y)) {
		*y_out = y * sy + instance->pos[1];
		return true;
	}

	if (!instance->height_detector) {
		if (!instance->plugin || !instance->plugin->renderer)
			return false;
		instance->height_detector = RE_renderer_create_height_detector(instance->plugin->renderer, instance->model);
	}

	if (RE_renderer_detect_height(instance->height_detector, lx, lz, &y)) {
		*y_out = y * sy + instance->pos[1];
		return true;
	}

	// Return the average of 4 neighbors.
	int cnt = 0;
	float total = 0.0f;
	if (RE_renderer_detect_height(instance->height_detector, lx - 0.5f, lz, &y)) { cnt++; total += y; }
	if (RE_renderer_detect_height(instance->height_detector, lx + 0.5f, lz, &y)) { cnt++; total += y; }
	if (RE_renderer_detect_height(instance->height_detector, lx, lz - 0.5f, &y)) { cnt++; total += y; }
	if (RE_renderer_detect_height(instance->height_detector, lx, lz + 0.5f, &y)) { cnt++; total += y; }
	if (!cnt)
		return false;
	*y_out = (total / cnt) * sy + instance->pos[1];
	return true;
}

float RE_instance_calc_height(struct RE_instance *instance, float x, float z)
{
	float y = 0.0f;
	return RE_instance_calc_height_ex(instance, x, z, &y) ? y : 0.0f;
}

float RE_instance_calc_2d_detection_height(struct RE_instance *instance, float x, float z)
{
	if (!instance || !instance->model || !instance->model->collider)
		return 0.f;
	vec2 xz = { x, -z };
	float h;
	if (collider_height(instance->model->collider, xz, &h))
		return h;
	return 0.f;
}

bool RE_instance_calc_2d_detection(struct RE_instance *instance, float x0, float y0, float z0, float x1, float y1, float z1, float *x2, float *y2, float *z2, float radius)
{
	if (!instance || !instance->model || !instance->model->collider)
		return false;
	vec2 p0 = { x0, -z0 };
	vec2 p1 = { x1, -z1 };
	vec2 p2;
	if (!check_collision(instance->model->collider, p0, p1, radius, p2))
		return false;
	*x2 = p2[0];
	*z2 = -p2[1];
	return collider_height(instance->model->collider, p2, y2);
}

bool RE_instance_set_debug_draw_shadow_volume(struct RE_instance *inst, bool draw)
{
	if (!inst)
		return false;
	if (draw && !inst->shadow_volume_instance) {
		inst->shadow_volume_instance = create_instance(inst->plugin);
		inst->shadow_volume_instance->model = model_create_sphere(255, 0, 0, 64);
	}
	if (inst->shadow_volume_instance)
		inst->shadow_volume_instance->draw = draw;
	return true;
}

bool RE_instance_find_path(struct RE_instance *inst, vec3 start, vec3 goal)
{
	if (!inst || !inst->model || !inst->model->collider)
		return false;
	mat4 vp_transform;
	RE_calc_view_matrix(&inst->plugin->camera, NULL, vp_transform);
	glm_mat4_mul(inst->plugin->proj_transform, vp_transform, vp_transform);
	return collider_find_path(inst->model->collider, start, goal, vp_transform);
}

const vec3 *RE_instance_get_path_line(struct RE_instance *inst, int *nr_path_points)
{
	if (!inst || !inst->model || !inst->model->collider)
		return NULL;
	*nr_path_points = inst->model->collider->nr_path_points;
	return inst->model->collider->path_points;
}

bool RE_instance_optimize_path_line(struct RE_instance *inst)
{
	if (!inst || !inst->model || !inst->model->collider)
		return false;
	return collider_optimize_path(inst->model->collider);
}

static void calc_eye_ray(struct RE_plugin *plugin, int view_x, int view_y, vec3 origin, vec3 direction)
{
	float vw = plugin->renderer->viewport_width > 0 ? plugin->renderer->viewport_width : 1.f;
	float vh = plugin->renderer->viewport_height > 0 ? plugin->renderer->viewport_height : 1.f;
	float ndc_x = 2.f * view_x / vw - 1.f;
	float ndc_y = 1.f - 2.f * view_y / vh;
	vec4 near = { ndc_x, ndc_y, -1.f, 1.f };
	vec4 far  = { ndc_x, ndc_y,  1.f, 1.f };

	mat4 inv_vp;
	RE_calc_view_matrix(&plugin->camera, NULL, inv_vp);
	glm_mat4_mul(plugin->proj_transform, inv_vp, inv_vp);
	glm_mat4_inv(inv_vp, inv_vp);

	glm_mat4_mulv(inv_vp, near, near);
	glm_mat4_mulv(inv_vp, far, far);
	glm_vec3_divs(near, near[3], near);
	glm_vec3_divs(far, far[3], far);

	glm_vec3_copy(near, origin);
	glm_vec3_sub(far, near, direction);
	glm_vec3_normalize(direction);
}

bool RE_instance_calc_path_finder_intersect_eye_vec(struct RE_instance *inst, int mouse_x, int mouse_y, vec3 out)
{
	if (!inst || !inst->model || !inst->model->collider || !inst->plugin || !inst->plugin->renderer)
		return false;

	vec3 origin, direction;
	calc_eye_ray(inst->plugin, mouse_x, mouse_y, origin, direction);

	if (inst->local_transform_needs_update)
		RE_instance_update_local_transform(inst);
	mat4 inv_local;
	glm_mat4_inv(inst->local_transform, inv_local);

	vec3 local_origin, far_pt, local_far, local_dir, local_hit;
	glm_vec3_add(origin, direction, far_pt);
	glm_mat4_mulv3(inv_local, origin, 1.0f, local_origin);
	glm_mat4_mulv3(inv_local, far_pt, 1.0f, local_far);
	glm_vec3_sub(local_far, local_origin, local_dir);
	glm_vec3_normalize(local_dir);

	if (!collider_raycast(inst->model->collider, local_origin, local_dir, local_hit))
		return false;
	glm_mat4_mulv3(inst->local_transform, local_hit, 1.0f, out);
	return true;
}

static uint64_t fnv1a_bytes(uint64_t h, const void *data, size_t len)
{
	const uint8_t *p = (const uint8_t *)data;
	for (size_t i = 0; i < len; i++) {
		h ^= p[i];
		h *= 1099511628211ULL;
	}
	return h;
}

static struct collider *RE_plugin_get_collider(struct RE_plugin *plugin)
{
	if (!plugin)
		return NULL;

	uint64_t h = 14695981039346656037ULL;
	int count = 0;
	for (int i = 0; i < plugin->nr_instances; i++) {
		struct RE_instance *inst = plugin->instances[i];
		if (!inst || !inst->draw || !inst->model || !inst->model->collider || !inst->model->collider->is_navmesh)
			continue;
		count++;
		h = fnv1a_bytes(h, &i, sizeof(i));
		h = fnv1a_bytes(h, &inst->model->collider, sizeof(inst->model->collider));
		h = fnv1a_bytes(h, inst->pos, sizeof(inst->pos));
		h = fnv1a_bytes(h, &inst->pitch, sizeof(inst->pitch));
		h = fnv1a_bytes(h, &inst->roll, sizeof(inst->roll));
		h = fnv1a_bytes(h, &inst->yaw, sizeof(inst->yaw));
		h = fnv1a_bytes(h, inst->scale, sizeof(inst->scale));
	}

	if (count == 0) {
		if (plugin->collider) {
			collider_free(plugin->collider);
			plugin->collider = NULL;
		}
		plugin->collider_hash_valid = false;
		return NULL;
	}

	if (plugin->collider_hash_valid && plugin->collider_hash == h && plugin->collider)
		return plugin->collider;

	if (plugin->collider)
		collider_free(plugin->collider);
	plugin->collider = collider_create_from_plugin(plugin);
	plugin->collider_hash = h;
	plugin->collider_hash_valid = true;
	return plugin->collider;
}

bool RE_plugin_calc_2d_detection_height(struct RE_plugin *plugin, float x, float z, float *height_out)
{
	struct collider *col = RE_plugin_get_collider(plugin);
	if (!col)
		return false;
	return collider_height(col, (vec2){ x, -z }, height_out);
}

bool RE_plugin_calc_2d_detection(struct RE_plugin *plugin, float x0, float y0, float z0, float x1, float y1, float z1, float radius, float *x2, float *y2, float *z2)
{
	struct collider *col = RE_plugin_get_collider(plugin);
	if (!col)
		return false;
	vec2 p0 = { x0, -z0 };
	vec2 p1 = { x1, -z1 };
	vec2 p2;
	if (!check_collision(col, p0, p1, radius, p2))
		return false;
	*x2 = p2[0];
	*z2 = -p2[1];
	if (!collider_height(col, p2, y2)) {
		if (!collider_height(col, p0, y2))
			*y2 = y0;
	}
	return true;
}

bool RE_plugin_calc_2d_detection_intersect_eye_vec(struct RE_plugin *plugin, int view_x, int view_y, vec3 out)
{
	if (!plugin || !plugin->renderer)
		return false;

	vec3 origin, direction;
	calc_eye_ray(plugin, view_x, view_y, origin, direction);

	struct collider *col = RE_plugin_get_collider(plugin);
	if (col)
		return collider_raycast(col, origin, direction, out);

	// Fallback for battle stages without a "collision" navmesh: intersect Y=0 plane.
	if (fabsf(direction[1]) > 1e-5f) {
		float t = -origin[1] / direction[1];
		if (t > 0.0f) {
			glm_vec3_scale(direction, t, out);
			glm_vec3_add(origin, out, out);
			return true;
		}
	}
	return false;
}

bool RE_plugin_find_path(struct RE_plugin *plugin, vec3 start, vec3 goal)
{
	struct collider *col = RE_plugin_get_collider(plugin);
	if (!col)
		return false;
	mat4 vp_transform;
	RE_calc_view_matrix(&plugin->camera, NULL, vp_transform);
	glm_mat4_mul(plugin->proj_transform, vp_transform, vp_transform);
	return collider_find_path(col, start, goal, vp_transform);
}

const vec3 *RE_plugin_get_path_line(struct RE_plugin *plugin, int *nr_path_points)
{
	struct collider *col = RE_plugin_get_collider(plugin);
	if (!col)
		return NULL;
	*nr_path_points = col->nr_path_points;
	return col->path_points;
}

bool RE_plugin_optimize_path_line(struct RE_plugin *plugin)
{
	struct collider *col = RE_plugin_get_collider(plugin);
	if (!col)
		return false;
	return collider_optimize_path(col);
}

bool RE_plugin_transform_pos_to_view_pos(struct RE_plugin *plugin, float x, float y, float z, int *view_x, int *view_y)
{
	if (!plugin || !plugin->renderer)
		return false;

	mat4 vp;
	RE_calc_view_matrix(&plugin->camera, NULL, vp);
	glm_mat4_mul(plugin->proj_transform, vp, vp);

	vec4 pos = { x, y, z, 1.f };
	glm_mat4_mulv(vp, pos, pos);

	if (pos[3] == 0.f)
		return false;

	float ndc_x = pos[0] / pos[3];
	float ndc_y = pos[1] / pos[3];

	*view_x = (int)((ndc_x + 1.f) * 0.5f * plugin->renderer->viewport_width);
	*view_y = (int)((1.f - ndc_y) * 0.5f * plugin->renderer->viewport_height);
	return true;
}

static bool RE_plugin_get_camera_basis_vector(struct RE_plugin *plugin, vec3 axis, vec3 out)
{
	if (!plugin)
		return false;

	vec3 euler;
	if (plugin->camera.override_active) {
		euler[0] = glm_rad(plugin->camera.override_pitch);
		euler[1] = glm_rad(plugin->camera.override_yaw);
		euler[2] = glm_rad(plugin->camera.override_roll);
	} else {
		euler[0] = glm_rad(plugin->camera.pitch + plugin->camera.quake_pitch);
		euler[1] = glm_rad(plugin->camera.yaw + plugin->camera.quake_yaw);
		euler[2] = glm_rad(plugin->camera.roll);
	}
	mat4 rot;
	glm_euler_yxz(euler, rot);
	glm_mat4_mulv3(rot, axis, 0.f, out);
	return true;
}

bool RE_plugin_get_camera_x_vector(struct RE_plugin *plugin, vec3 out)
{
	return RE_plugin_get_camera_basis_vector(plugin, GLM_XUP, out);
}

bool RE_plugin_get_camera_y_vector(struct RE_plugin *plugin, vec3 out)
{
	return RE_plugin_get_camera_basis_vector(plugin, GLM_YUP, out);
}

bool RE_plugin_get_camera_z_vector(struct RE_plugin *plugin, vec3 out)
{
	return RE_plugin_get_camera_basis_vector(plugin, GLM_FORWARD, out);
}

void RE_plugin_reset_light_param(struct RE_plugin *plugin)
{
	if (plugin)
		lit_reset(plugin->light_params);
}

bool RE_plugin_set_fog_type(struct RE_plugin *plugin, int type)
{
	if (!plugin)
		return false;
	if (plugin->version == RE_REIGN_PLUGIN) {
		switch (type) {
		case 0: plugin->fog_type = RE_FOG_NONE; return true;
		case 1: plugin->fog_type = RE_FOG_LINEAR; return true;
		case 2: plugin->fog_type = RE_FOG_LIGHT_SCATTERING; return true;
		default: break;
		}
	} else {
		switch (type) {
		case 0: plugin->fog_type = RE_FOG_NONE; return true;
		case 1: plugin->fog_type = RE_FOG_LIGHT_SCATTERING; return true;
		default: break;
		}
	}
	WARNING("unknown fog type %d", type);
	return false;
}

int RE_plugin_get_fog_type(struct RE_plugin *plugin)
{
	if (!plugin)
		return 0;
	if (plugin->version == RE_REIGN_PLUGIN) {
		switch (plugin->fog_type) {
		case RE_FOG_NONE: return 0;
		case RE_FOG_LINEAR: return 1;
		case RE_FOG_LIGHT_SCATTERING: return 2;
		default: break;
		}
	} else {
		switch (plugin->fog_type) {
		case RE_FOG_NONE: return 0;
		case RE_FOG_LIGHT_SCATTERING: return 1;
		default: break;
		}
	}
	ERROR("[BUG] unexpected fog type %d", plugin->fog_type);
}

void RE_instance_update_local_transform(struct RE_instance *inst)
{
	vec3 euler = {
		glm_rad(inst->pitch),
		glm_rad(inst->yaw),
		glm_rad(inst->roll)
	};
	mat4 rot;
	glm_euler(euler, rot);

	glm_translate_make(inst->local_transform, inst->pos);
	glm_mat4_mul(inst->local_transform, rot, inst->local_transform);
	glm_scale(inst->local_transform, inst->scale);

	mat3 normal;
	glm_mat4_pick3(inst->local_transform, normal);
	glm_mat3_inv(normal, normal);
	glm_mat3_transpose_to(normal, inst->normal_transform);

	inst->local_transform_needs_update = false;
}

int RE_motion_get_state(struct motion *motion)
{
	return motion ? motion->state : RE_MOTION_STATE_STOP;
}

bool RE_motion_set_state(struct motion *motion, int state)
{
	if (!motion)
		return false;
	motion->state = state;
	return true;
}

float RE_motion_get_frame(struct motion *motion)
{
	return motion ? motion->current_frame : 0.0;
}

bool RE_motion_set_frame(struct motion *motion, float frame)
{
	if (!motion)
		return false;
	motion->current_frame = frame;
	return true;
}

bool RE_motion_set_frame_range(struct motion *motion, float begin, float end)
{
	if (!motion)
		return false;
	motion->frame_begin = begin;
	motion->frame_end = end;
	if (motion->instance && motion->instance->type == RE_ITYPE_BILLBOARD &&
	    motion->instance->plugin && motion->instance->plugin->version < RE_SEAL_PLUGIN &&
	    motion->instance->plugin->renderer) {
		for (int i = begin; i < end; i++) {
			if (!RE_renderer_load_billboard_texture_by_no(motion->instance->plugin->renderer, i))
				return false;
		}
	}
	return true;
}

bool RE_motion_set_loop_frame_range(struct motion *motion, float begin, float end)
{
	if (!motion)
		return false;
	motion->loop_frame_begin = begin;
	motion->loop_frame_end = end;
	if (motion->instance && motion->instance->type == RE_ITYPE_BILLBOARD &&
	    motion->instance->plugin && motion->instance->plugin->version < RE_SEAL_PLUGIN &&
	    motion->instance->plugin->renderer) {
		for (int i = begin; i < end; i++) {
			if (!RE_renderer_load_billboard_texture_by_no(motion->instance->plugin->renderer, i))
				return false;
		}
	}
	return true;
}

int RE_effect_get_num_object(struct particle_effect *effect)
{
	return effect ? effect->pae->nr_objects : 0;
}

bool RE_effect_get_camera_quake_flag(struct particle_effect *effect)
{
	return effect && effect->camera_quake_enabled;
}

bool RE_effect_set_camera_quake_flag(struct particle_effect *effect, bool enable)
{
	if (!effect)
		return false;
	effect->camera_quake_enabled = enable;
	return true;
}

struct particle_object *RE_get_effect_object(struct particle_effect *effect, unsigned object)
{
	if (!effect || object >= (unsigned)effect->pae->nr_objects)
		return NULL;
	return &effect->objects[object];
}

int RE_particle_get_type(struct particle_object *po)
{
	return po ? po->pae_obj->type : 0;
}

int RE_particle_get_move_type(struct particle_object *po)
{
	return po ? po->pae_obj->move_type : 0;
}

int RE_particle_get_up_vec_type(struct particle_object *po)
{
	return po ? po->pae_obj->up_vector_type : 0;
}

float RE_particle_get_move_curve(struct particle_object *po)
{
	return po ? po->pae_obj->move_curve : 0.0;
}

void RE_particle_get_frame(struct particle_object *po, int *begin_frame, int *end_frame)
{
	if (!po)
		return;
	*begin_frame = po->pae_obj->begin_frame;
	*end_frame = po->pae_obj->end_frame;
}

int RE_particle_get_stop_frame(struct particle_object *po)
{
	return po ? po->pae_obj->stop_frame : 0;
}

const char *RE_particle_get_name(struct particle_object *po)
{
	return po ? po->pae_obj->name : NULL;
}

int RE_particle_get_num_pos(struct particle_object *po)
{
	return po ? NR_PARTICLE_POSITIONS : 0;
}

int RE_particle_get_num_pos_unit(struct particle_object *po, int pos)
{
	return po ? NR_PARTICLE_POSITION_UNITS : 0;
}

static struct particle_position_unit *get_position_unit(struct particle_object *po, int pos, int unit)
{
	if (!po || pos < 0 || pos >= NR_PARTICLE_POSITIONS || !po->pae_obj->position[pos] || unit < 0 || unit >= NR_PARTICLE_POSITION_UNITS)
		return NULL;
	return &po->pae_obj->position[pos]->units[unit];
}

int RE_particle_get_pos_unit_type(struct particle_object *po, int pos, int unit)
{
	struct particle_position_unit *u = get_position_unit(po, pos, unit);
	return u ? u->type : 0;
}

int RE_particle_get_pos_unit_index(struct particle_object *po, int pos, int unit)
{
	struct particle_position_unit *u = get_position_unit(po, pos, unit);
	if (!u)
		return 0;
	switch (u->type) {
	case PARTICLE_POS_UNIT_BONE:
		return u->bone.target_index;
	case PARTICLE_POS_UNIT_TARGET:
		return u->target.index;
	default:
		return 0;
	}
}

int RE_particle_get_num_texture(struct particle_object *po)
{
	return po ? po->pae_obj->nr_textures : 0;
}

const char *RE_particle_get_texture(struct particle_object *po, int texture)
{
	if (!po || texture < 0 || texture >= po->pae_obj->nr_textures)
		return NULL;
	return po->pae_obj->textures[texture];
}

bool RE_particle_get_size(struct particle_object *po, float *begin_size, float *end_size)
{
	if (!po)
		return false;
	*begin_size = po->pae_obj->size[0];
	*end_size = po->pae_obj->size[1];
	return true;
}

bool RE_particle_get_size2(struct particle_object *po, int frame, float *size)
{
	if (!po || frame < 0 || frame >= po->pae_obj->nr_sizes2)
		return false;
	*size = po->pae_obj->sizes2[frame];
	return true;
}

bool RE_particle_get_size_x(struct particle_object *po, int frame, float *size)
{
	if (!po || frame < 0 || frame >= po->pae_obj->nr_sizes_x)
		return false;
	*size = po->pae_obj->sizes_x[frame];
	return true;
}

bool RE_particle_get_size_y(struct particle_object *po, int frame, float *size)
{
	if (!po || frame < 0 || frame >= po->pae_obj->nr_sizes_y)
		return false;
	*size = po->pae_obj->sizes_y[frame];
	return true;
}

bool RE_particle_get_size_type(struct particle_object *po, int frame, int *type)
{
	if (!po || frame < 0 || frame >= po->pae_obj->nr_size_types)
		return false;
	*type = po->pae_obj->size_types[frame];
	return true;
}

bool RE_particle_get_size_x_type(struct particle_object *po, int frame, int *type)
{
	if (!po || frame < 0 || frame >= po->pae_obj->nr_size_x_types)
		return false;
	*type = po->pae_obj->size_x_types[frame];
	return true;
}

bool RE_particle_get_size_y_type(struct particle_object *po, int frame, int *type)
{
	if (!po || frame < 0 || frame >= po->pae_obj->nr_size_y_types)
		return false;
	*type = po->pae_obj->size_y_types[frame];
	return true;
}

int RE_particle_get_num_size2(struct particle_object *po)
{
	return po ? po->pae_obj->nr_sizes2 : 0;
}

int RE_particle_get_num_size_x(struct particle_object *po)
{
	return po ? po->pae_obj->nr_sizes_x : 0;
}

int RE_particle_get_num_size_y(struct particle_object *po)
{
	return po ? po->pae_obj->nr_sizes_y : 0;
}

int RE_particle_get_num_size_type(struct particle_object *po)
{
	return po ? po->pae_obj->nr_size_types : 0;
}

int RE_particle_get_num_size_x_type(struct particle_object *po)
{
	return po ? po->pae_obj->nr_size_x_types : 0;
}

int RE_particle_get_num_size_y_type(struct particle_object *po)
{
	return po ? po->pae_obj->nr_size_y_types : 0;
}

int RE_particle_get_blend_type(struct particle_object *po)
{
	return po ? po->pae_obj->blend_type : 0;
}

const char *RE_particle_get_polygon_name(struct particle_object *po)
{
	return po ? po->pae_obj->polygon_name : NULL;
}

int RE_particle_get_num_particles(struct particle_object *po)
{
	return po ? po->pae_obj->nr_particles : 0;
}

int RE_particle_get_alpha_fadein_time(struct particle_object *po)
{
	return po ? po->pae_obj->alpha_fadein_time : 0;
}

int RE_particle_get_alpha_fadeout_time(struct particle_object *po)
{
	return po ? po->pae_obj->alpha_fadeout_time : 0;
}

int RE_particle_get_texture_anime_time(struct particle_object *po)
{
	return po ? po->pae_obj->texture_anime_time : 0;
}

float RE_particle_get_alpha_fadein_frame(struct particle_object *po)
{
	return po ? po->pae_obj->alpha_fadein_frame : 0.0;
}

float RE_particle_get_alpha_fadeout_frame(struct particle_object *po)
{
	return po ? po->pae_obj->alpha_fadeout_frame : 0.0;
}

float RE_particle_get_texture_anime_frame(struct particle_object *po)
{
	return po ? po->pae_obj->texture_anime_frame : 0.0;
}

int RE_particle_get_frame_ref_type(struct particle_object *po)
{
	return po ? po->pae_obj->frame_ref_type : 0;
}

int RE_particle_get_frame_ref_param(struct particle_object *po)
{
	return po ? po->pae_obj->frame_ref_param : 0;
}

void RE_particle_get_x_rotation_angle(struct particle_object *po, float *begin_angle, float *end_angle)
{
	if (!po)
		return;
	*begin_angle = po->pae_obj->rotation.begin[0];
	*end_angle = po->pae_obj->rotation.end[0];
}

void RE_particle_get_y_rotation_angle(struct particle_object *po, float *begin_angle, float *end_angle)
{
	if (!po)
		return;
	*begin_angle = po->pae_obj->rotation.begin[1];
	*end_angle = po->pae_obj->rotation.end[1];
}

void RE_particle_get_z_rotation_angle(struct particle_object *po, float *begin_angle, float *end_angle)
{
	if (!po)
		return;
	*begin_angle = po->pae_obj->rotation.begin[2];
	*end_angle = po->pae_obj->rotation.end[2];
}

void RE_particle_get_x_revolution_angle(struct particle_object *po, float *begin_angle, float *end_angle)
{
	if (!po)
		return;
	*begin_angle = po->pae_obj->revolution_angle.begin[0];
	*end_angle = po->pae_obj->revolution_angle.end[0];
}

void RE_particle_get_x_revolution_distance(struct particle_object *po, float *begin_distance, float *end_distance)
{
	if (!po)
		return;
	*begin_distance = po->pae_obj->revolution_distance.begin[0];
	*end_distance = po->pae_obj->revolution_distance.end[0];
}

void RE_particle_get_y_revolution_angle(struct particle_object *po, float *begin_angle, float *end_angle)
{
	if (!po)
		return;
	*begin_angle = po->pae_obj->revolution_angle.begin[1];
	*end_angle = po->pae_obj->revolution_angle.end[1];
}

void RE_particle_get_y_revolution_distance(struct particle_object *po, float *begin_distance, float *end_distance)
{
	if (!po)
		return;
	*begin_distance = po->pae_obj->revolution_distance.begin[1];
	*end_distance = po->pae_obj->revolution_distance.end[1];
}

void RE_particle_get_z_revolution_angle(struct particle_object *po, float *begin_angle, float *end_angle)
{
	if (!po)
		return;
	*begin_angle = po->pae_obj->revolution_angle.begin[2];
	*end_angle = po->pae_obj->revolution_angle.end[2];
}

void RE_particle_get_z_revolution_distance(struct particle_object *po, float *begin_distance, float *end_distance)
{
	if (!po)
		return;
	*begin_distance = po->pae_obj->revolution_distance.begin[2];
	*end_distance = po->pae_obj->revolution_distance.end[2];
}

bool RE_particle_get_curve_length(struct particle_object *po, vec3 out)
{
	if (!po)
		return false;
	glm_vec3_copy(po->pae_obj->curve_length, out);
	return true;
}

int RE_particle_get_child_frame(struct particle_object *po)
{
	return po ? po->pae_obj->child_frame : 0;
}

float RE_particle_get_child_length(struct particle_object *po)
{
	return po ? po->pae_obj->child_length : 0.0;
}

float RE_particle_get_child_begin_slope(struct particle_object *po)
{
	return po ? po->pae_obj->child_begin_slope : 0.0;
}

float RE_particle_get_child_end_slope(struct particle_object *po)
{
	return po ? po->pae_obj->child_end_slope : 0.0;
}

float RE_particle_get_child_create_begin_frame(struct particle_object *po)
{
	return po ? po->pae_obj->child_create_begin_frame : 0.0;
}

float RE_particle_get_child_create_end_frame(struct particle_object *po)
{
	return po ? po->pae_obj->child_create_end_frame : 0.0;
}

int RE_particle_get_child_move_dir_type(struct particle_object *po)
{
	return po ? po->pae_obj->child_move_dir_type : 0;
}

int RE_particle_get_dir_type(struct particle_object *po)
{
	return po ? po->pae_obj->dir_type : 0;
}

int RE_particle_get_num_damage(struct particle_object *po)
{
	return po ? po->pae_obj->nr_damages : 0;
}

int RE_particle_get_damage(struct particle_object *po, int frame)
{
	if (!po || frame < 0 || frame >= po->pae_obj->nr_damages)
		return 0;
	return po->pae_obj->damages[frame];
}

float RE_particle_get_offset_x(struct particle_object *po)
{
	return po ? po->pae_obj->offset_x : 0.0;
}

float RE_particle_get_offset_y(struct particle_object *po)
{
	return po ? po->pae_obj->offset_y : 0.0;
}

bool RE_effect_get_frame_range(struct RE_instance *instance, int *begin_frame, int *end_frame)
{
	if (!instance || !instance->effect || !instance->motion)
		return false;
	*begin_frame = instance->motion->frame_begin;
	*end_frame = instance->motion->frame_end;
	return true;
}

bool RE_back_cg_set(struct RE_back_cg *bcg, int no)
{
	if (!bcg)
		return false;
	if (bcg->name) {
		free_string(bcg->name);
		bcg->name = NULL;
	}
	bcg->no = no;
	gfx_delete_texture(&bcg->texture);

	if (!no)  // unload only.
		return true;

	struct cg *cg = asset_cg_load(no);
	if (!cg) {
		WARNING("cannot load back CG: %d", no);
		return false;
	}
	gfx_init_texture_with_cg(&bcg->texture, cg);
	cg_free(cg);
	return true;
}

bool RE_back_cg_set_name(struct RE_back_cg *bcg, struct string *name, struct archive *aar)
{
	if (!bcg)
		return false;
	if (bcg->name)
		free_string(bcg->name);
	bcg->name = string_ref(name);
	bcg->no = 0;
	gfx_delete_texture(&bcg->texture);

	if (name->size == 0)  // unload only.
		return true;

	char *cg_path = xmalloc(name->size + 5);
	sprintf(cg_path, "%s.bmp", name->text);
	struct archive_data *dfile = archive_get_by_name(aar, cg_path);
	free(cg_path);
	if (!dfile) {
		WARNING("cannot load back CG %s", name->text);
		return false;
	}
	struct cg *cg = cg_load_data(dfile);
	archive_free_data(dfile);
	if (!cg) {
		WARNING("cg_load_data failed: %s", name->text);
		return false;
	}
	gfx_init_texture_with_cg(&bcg->texture, cg);
	cg_free(cg);
	return true;
}

static void save_cstr(struct iarray_writer *w, const char *s)
{
	if (!s || !s[0]) {
		iarray_write_string_or_null(w, NULL);
		return;
	}
	struct string *str = make_string(s, strlen(s));
	iarray_write_string_or_null(w, str);
	free_string(str);
}

static char *load_cstr(struct iarray_reader *r)
{
	struct string *str = iarray_read_string_or_null(r);
	if (!str)
		return NULL;
	char *out = xstrdup(str->text);
	free_string(str);
	return out;
}

static void save_vec2(struct iarray_writer *w, vec2 v)
{
	iarray_write_float(w, v[0]);
	iarray_write_float(w, v[1]);
}

static void load_vec2(struct iarray_reader *r, vec2 v)
{
	v[0] = iarray_read_float(r);
	v[1] = iarray_read_float(r);
}

static void save_vec3(struct iarray_writer *w, vec3 v)
{
	iarray_write_float(w, v[0]);
	iarray_write_float(w, v[1]);
	iarray_write_float(w, v[2]);
}

static void load_vec3(struct iarray_reader *r, vec3 v)
{
	v[0] = iarray_read_float(r);
	v[1] = iarray_read_float(r);
	v[2] = iarray_read_float(r);
}

static void save_re_motion(struct iarray_writer *w, const struct motion *m)
{
	iarray_write(w, m ? 1 : 0);
	if (!m)
		return;
	save_cstr(w, m->mot ? m->mot->name : NULL);
	iarray_write(w, m->state);
	iarray_write_float(w, m->current_frame);
	iarray_write_float(w, m->frame_begin);
	iarray_write_float(w, m->frame_end);
	iarray_write_float(w, m->loop_frame_begin);
	iarray_write_float(w, m->loop_frame_end);
	iarray_write(w, m->texture_index);
}

static void load_re_motion(struct iarray_reader *r, struct RE_instance *inst, bool is_next)
{
	if (!iarray_read(r))
		return;
	char *mot_name = load_cstr(r);
	int state = iarray_read(r);
	float cur_frame = iarray_read_float(r);
	float fb = iarray_read_float(r);
	float fe = iarray_read_float(r);
	float lfb = iarray_read_float(r);
	float lfe = iarray_read_float(r);
	int tex_idx = iarray_read(r);

	if (!inst) {
		free(mot_name);
		return;
	}

	struct motion *m = NULL;
	if (!is_next) {
		if (mot_name && mot_name[0] && inst->model)
			RE_instance_load_motion(inst, mot_name);
		if (!inst->motion && inst->type == RE_ITYPE_BILLBOARD) {
			inst->motion = xcalloc(1, sizeof(struct motion));
			inst->motion->instance = inst;
		}
		m = inst->motion;
	} else {
		if (mot_name && mot_name[0] && inst->model)
			RE_instance_load_next_motion(inst, mot_name);
		m = inst->next_motion;
	}
	if (m) {
		if (!is_next && inst->type == RE_ITYPE_BILLBOARD && inst->plugin && inst->plugin->version < RE_SEAL_PLUGIN) {
			RE_motion_set_frame_range(m, fb, fe);
			RE_motion_set_loop_frame_range(m, lfb, lfe);
		}
		m->state = state;
		m->current_frame = cur_frame;
		m->frame_begin = fb;
		m->frame_end = fe;
		m->loop_frame_begin = lfb;
		m->loop_frame_end = lfe;
		m->texture_index = tex_idx;
	}
	free(mot_name);
}

static void save_re_instance(struct iarray_writer *w, struct RE_instance *inst)
{
	iarray_write(w, inst->type);
	save_cstr(w, inst->load_name);
	for (int i = 0; i < RE_NR_INSTANCE_TARGETS; i++)
		iarray_write(w, inst->target[i]);
	save_vec3(w, inst->pos);
	iarray_write_float(w, inst->pitch);
	iarray_write_float(w, inst->roll);
	iarray_write_float(w, inst->yaw);
	save_vec3(w, inst->scale);
	iarray_write_float(w, inst->alpha);
	iarray_write_float(w, inst->grayscale_rate);
	iarray_write(w, inst->draw);
	iarray_write(w, inst->draw_edge);
	iarray_write(w, inst->draw_shadow);
	iarray_write(w, inst->make_shadow);
	iarray_write_float(w, inst->shadow_volume_bone_radius);
	iarray_write(w, inst->draw_bump);
	iarray_write_float(w, inst->fps);
	iarray_write(w, inst->use_mag_speed);
	iarray_write(w, inst->motion_blend);
	iarray_write_float(w, inst->motion_blend_rate);
	save_vec3(w, inst->diffuse);
	save_vec3(w, inst->ambient);
	save_vec3(w, inst->column_pos);
	iarray_write_float(w, inst->column_height);
	iarray_write_float(w, inst->column_radius);
	iarray_write_float(w, inst->column_angle);
	iarray_write(w, inst->draw_type);
	for (int i = 0; i < 4; i++) {
		save_vec3(w, inst->vertex_pos[i]);
		save_vec2(w, inst->vertex_uv[i]);
	}
	save_vec3(w, inst->vec);
	save_vec3(w, inst->globe_diffuse);
	iarray_write(w, inst->light_params ? 1 : 0);
	if (inst->light_params) {
		for (int i = 0; i < RE_NR_LIGHT_PARAMS; i++)
			iarray_write_float(w, inst->light_params[i]);
	}
	iarray_write(w, (inst->shadow_volume_instance && inst->shadow_volume_instance->draw) ? 1 : 0);
	iarray_write(w, inst->texture_animation_index);

	int nr_meshes = inst->model ? inst->model->nr_meshes : 0;
	iarray_write(w, nr_meshes);
	for (int i = 0; i < nr_meshes; i++)
		iarray_write(w, RE_instance_is_mesh_hidden(inst, i));

	save_re_motion(w, inst->motion);
	save_re_motion(w, inst->next_motion);
}

static void load_re_instance(struct RE_plugin *plugin, int slot, struct iarray_reader *r)
{
	enum RE_instance_type type = iarray_read(r);
	char *load_name = load_cstr(r);
	struct RE_instance *inst = plugin ? create_instance(plugin) : NULL;
	if (plugin)
		plugin->instances[slot] = inst;
	if (inst && type != RE_ITYPE_UNINITIALIZED)
		RE_instance_set_type(inst, type);

	vec3 dummy3;
	vec2 dummy2;
	for (int i = 0; i < RE_NR_INSTANCE_TARGETS; i++) {
		int t = iarray_read(r);
		if (inst) inst->target[i] = t;
	}
	load_vec3(r, inst ? inst->pos : dummy3);
	float pitch = iarray_read_float(r);
	float roll = iarray_read_float(r);
	float yaw = iarray_read_float(r);
	vec3 scale;
	load_vec3(r, scale);
	float alpha = iarray_read_float(r);
	float gray = iarray_read_float(r);
	bool draw = !!iarray_read(r);
	bool draw_edge = !!iarray_read(r);
	bool draw_shadow = !!iarray_read(r);
	bool make_shadow = !!iarray_read(r);
	float sh_rad = iarray_read_float(r);
	bool draw_bump = !!iarray_read(r);
	float fps = iarray_read_float(r);
	bool use_mag = !!iarray_read(r);
	bool mblend = !!iarray_read(r);
	float mbrate = iarray_read_float(r);
	vec3 diffuse, ambient, col_pos;
	load_vec3(r, diffuse);
	load_vec3(r, ambient);
	load_vec3(r, col_pos);
	float col_h = iarray_read_float(r);
	float col_r = iarray_read_float(r);
	float col_a = iarray_read_float(r);
	int dtype = iarray_read(r);

	if (inst) {
		inst->pitch = pitch;
		inst->roll = roll;
		inst->yaw = yaw;
		glm_vec3_copy(scale, inst->scale);
		inst->alpha = alpha;
		inst->grayscale_rate = gray;
		inst->draw = draw;
		inst->draw_edge = draw_edge;
		inst->draw_shadow = draw_shadow;
		inst->make_shadow = make_shadow;
		inst->shadow_volume_bone_radius = sh_rad;
		inst->draw_bump = draw_bump;
		inst->fps = fps;
		inst->use_mag_speed = use_mag;
		inst->motion_blend = mblend;
		inst->motion_blend_rate = mbrate;
		glm_vec3_copy(diffuse, inst->diffuse);
		glm_vec3_copy(ambient, inst->ambient);
		glm_vec3_copy(col_pos, inst->column_pos);
		inst->column_height = col_h;
		inst->column_radius = col_r;
		inst->column_angle = col_a;
		inst->draw_type = dtype;
	}
	for (int i = 0; i < 4; i++) {
		load_vec3(r, inst ? inst->vertex_pos[i] : dummy3);
		load_vec2(r, inst ? inst->vertex_uv[i] : dummy2);
	}
	load_vec3(r, inst ? inst->vec : dummy3);
	load_vec3(r, inst ? inst->globe_diffuse : dummy3);

	int has_lp = iarray_read(r);
	float lp_tmp[RE_NR_LIGHT_PARAMS];
	if (has_lp) {
		for (int i = 0; i < RE_NR_LIGHT_PARAMS; i++)
			lp_tmp[i] = iarray_read_float(r);
	}
	bool debug_shvol = !!iarray_read(r);
	int tex_anim_idx = iarray_read(r);

	if (inst && load_name && load_name[0])
		RE_instance_load(inst, load_name);
	free(load_name);

	if (inst && has_lp) {
		if (!inst->light_params)
			inst->light_params = xmalloc(RE_NR_LIGHT_PARAMS * sizeof(float));
		memcpy(inst->light_params, lp_tmp, sizeof(lp_tmp));
	}

	int nr_meshes = iarray_read(r);
	for (int i = 0; i < nr_meshes; i++) {
		bool hidden = !!iarray_read(r);
		if (inst && inst->model && i < inst->model->nr_meshes)
			set_instance_mesh_hidden(inst, i, hidden);
	}

	load_re_motion(r, inst, false);
	load_re_motion(r, inst, true);

	if (inst) {
		inst->texture_animation_index = tex_anim_idx;
		if (debug_shvol)
			RE_instance_set_debug_draw_shadow_volume(inst, true);
		RE_instance_update_local_transform(inst);
		if (inst->type == RE_ITYPE_SKINNED && inst->model && inst->model->nr_bones > 0 && inst->motion)
			update_bones(inst);
	}
}

void RE_plugin_save(struct RE_plugin *p, struct iarray_writer *w)
{
	iarray_write(w, p->suspended);
	save_vec3(w, p->camera.pos);
	iarray_write_float(w, p->camera.pitch);
	iarray_write_float(w, p->camera.roll);
	iarray_write_float(w, p->camera.yaw);
	iarray_write_float(w, p->camera.quake_pitch);
	iarray_write_float(w, p->camera.quake_yaw);
	iarray_write(w, p->camera.override_active);
	save_vec3(w, p->camera.override_pos);
	iarray_write_float(w, p->camera.override_pitch);
	iarray_write_float(w, p->camera.override_yaw);
	iarray_write_float(w, p->camera.override_roll);
	for (int c = 0; c < 4; c++)
		for (int r = 0; r < 4; r++)
			iarray_write_float(w, p->proj_transform[c][r]);

	for (int i = 0; i < RE_NR_BACK_CGS; i++) {
		iarray_write(w, p->back_cg[i].no);
		iarray_write_string_or_null(w, p->back_cg[i].name);
		iarray_write_float(w, p->back_cg[i].blend_rate);
		iarray_write_float(w, p->back_cg[i].x);
		iarray_write_float(w, p->back_cg[i].y);
		iarray_write_float(w, p->back_cg[i].mag);
		iarray_write(w, p->back_cg[i].show);
	}

	iarray_write(w, p->render_mode);
	iarray_write(w, p->shadow_mode);
	save_vec3(w, p->shadow_map_light_dir);
	iarray_write_float(w, p->shadow_bias);
	iarray_write(w, p->bump_mode);
	iarray_write(w, p->bloom_mode);
	iarray_write(w, p->glare_mode);
	iarray_write(w, p->fog_mode);
	iarray_write(w, p->fog_type);
	iarray_write_float(w, p->fog_near);
	iarray_write_float(w, p->fog_far);
	save_vec3(w, p->fog_color);
	iarray_write_float(w, p->ls_beta_r);
	iarray_write_float(w, p->ls_beta_m);
	iarray_write_float(w, p->ls_g);
	iarray_write_float(w, p->ls_distance);
	save_vec3(w, p->ls_light_dir);
	save_vec3(w, p->ls_light_color);
	save_vec3(w, p->ls_sun_color);
	iarray_write(w, p->specular_mode);
	iarray_write(w, p->light_map_mode);
	iarray_write(w, p->soft_fog_edge_mode);
	iarray_write(w, p->ssao_mode);
	iarray_write(w, p->vertex_blend_mode);
	iarray_write(w, p->shader_precision_mode);
	iarray_write(w, p->texture_filter_mode);
	iarray_write(w, p->use_power2_texture);
	iarray_write(w, p->shadow_map_resolution_level);
	iarray_write(w, p->texture_resolution_level);
	save_vec3(w, p->global_ambient);
	iarray_write_float(w, p->post_effect_filter_y);
	iarray_write_float(w, p->post_effect_filter_cb);
	iarray_write_float(w, p->post_effect_filter_cr);
	for (int i = 0; i < RE_DRAW_OPTION_MAX; i++)
		iarray_write(w, p->draw_options[i]);
	iarray_write(w, p->mag_speed);
	for (int i = 0; i < RE_NR_LIGHT_PARAMS; i++)
		iarray_write_float(w, p->light_params[i]);
	iarray_write_float(w, p->edge_length);
	iarray_write_float(w, p->edge_reduction_rate);
	save_vec3(w, p->edge_color);

	iarray_write(w, p->nr_instances);
	for (int i = 0; i < p->nr_instances; i++) {
		iarray_write(w, p->instances[i] ? 1 : 0);
		if (p->instances[i])
			save_re_instance(w, p->instances[i]);
	}
}

bool RE_plugin_load(struct RE_plugin *p, struct iarray_reader *r)
{
	bool suspended = !!iarray_read(r);
	vec3 dummy3;
	load_vec3(r, p ? p->camera.pos : dummy3);
	float cp = iarray_read_float(r);
	float cr = iarray_read_float(r);
	float cy = iarray_read_float(r);
	float qp = iarray_read_float(r);
	float qy = iarray_read_float(r);
	bool oa = !!iarray_read(r);
	vec3 op;
	load_vec3(r, op);
	float opp = iarray_read_float(r);
	float opy = iarray_read_float(r);
	float opr = iarray_read_float(r);
	if (p) {
		p->suspended = suspended;
		p->camera.pitch = cp;
		p->camera.roll = cr;
		p->camera.yaw = cy;
		p->camera.quake_pitch = qp;
		p->camera.quake_yaw = qy;
		p->camera.override_active = oa;
		glm_vec3_copy(op, p->camera.override_pos);
		p->camera.override_pitch = opp;
		p->camera.override_yaw = opy;
		p->camera.override_roll = opr;
	}
	for (int c = 0; c < 4; c++) {
		for (int row = 0; row < 4; row++) {
			float v = iarray_read_float(r);
			if (p) p->proj_transform[c][row] = v;
		}
	}

	for (int i = 0; i < RE_NR_BACK_CGS; i++) {
		int no = iarray_read(r);
		struct string *name = iarray_read_string_or_null(r);
		float br = iarray_read_float(r);
		float bx = iarray_read_float(r);
		float by = iarray_read_float(r);
		float bmag = iarray_read_float(r);
		bool bshow = !!iarray_read(r);
		if (p) {
			if (name && name->size > 0)
				RE_back_cg_set_name(&p->back_cg[i], name, p->aar);
			else if (no != 0)
				RE_back_cg_set(&p->back_cg[i], no);
			p->back_cg[i].blend_rate = br;
			p->back_cg[i].x = bx;
			p->back_cg[i].y = by;
			p->back_cg[i].mag = bmag;
			p->back_cg[i].show = bshow;
		}
		if (name)
			free_string(name);
	}

	int render_mode = iarray_read(r);
	int shadow_mode = iarray_read(r);
	vec3 sh_dir; load_vec3(r, sh_dir);
	float sh_bias = iarray_read_float(r);
	int bump_mode = iarray_read(r);
	int bloom_mode = iarray_read(r);
	int glare_mode = iarray_read(r);
	int fog_mode = iarray_read(r);
	int fog_type = iarray_read(r);
	float fog_near = iarray_read_float(r);
	float fog_far = iarray_read_float(r);
	vec3 fog_color; load_vec3(r, fog_color);
	float ls_br = iarray_read_float(r);
	float ls_bm = iarray_read_float(r);
	float ls_g = iarray_read_float(r);
	float ls_dist = iarray_read_float(r);
	vec3 ls_ld, ls_lc, ls_sc;
	load_vec3(r, ls_ld);
	load_vec3(r, ls_lc);
	load_vec3(r, ls_sc);
	int spec_mode = iarray_read(r);
	int lm_mode = iarray_read(r);
	int sfe_mode = iarray_read(r);
	int ssao_mode = iarray_read(r);
	int vb_mode = iarray_read(r);
	int sp_mode = iarray_read(r);
	int tf_mode = iarray_read(r);
	bool p2_tex = !!iarray_read(r);
	int sm_res = iarray_read(r);
	int tex_res = iarray_read(r);
	vec3 g_amb; load_vec3(r, g_amb);
	float pef_y = iarray_read_float(r);
	float pef_cb = iarray_read_float(r);
	float pef_cr = iarray_read_float(r);
	if (p) {
		p->render_mode = render_mode;
		p->shadow_mode = shadow_mode;
		glm_vec3_copy(sh_dir, p->shadow_map_light_dir);
		p->shadow_bias = sh_bias;
		p->bump_mode = bump_mode;
		p->bloom_mode = bloom_mode;
		p->glare_mode = glare_mode;
		p->fog_mode = fog_mode;
		p->fog_type = fog_type;
		p->fog_near = fog_near;
		p->fog_far = fog_far;
		glm_vec3_copy(fog_color, p->fog_color);
		p->ls_beta_r = ls_br;
		p->ls_beta_m = ls_bm;
		p->ls_g = ls_g;
		p->ls_distance = ls_dist;
		glm_vec3_copy(ls_ld, p->ls_light_dir);
		glm_vec3_copy(ls_lc, p->ls_light_color);
		glm_vec3_copy(ls_sc, p->ls_sun_color);
		p->specular_mode = spec_mode;
		p->light_map_mode = lm_mode;
		p->soft_fog_edge_mode = sfe_mode;
		p->ssao_mode = ssao_mode;
		p->vertex_blend_mode = vb_mode;
		p->shader_precision_mode = sp_mode;
		p->texture_filter_mode = tf_mode;
		p->use_power2_texture = p2_tex;
		p->shadow_map_resolution_level = sm_res;
		p->texture_resolution_level = tex_res;
		glm_vec3_copy(g_amb, p->global_ambient);
		p->post_effect_filter_y = pef_y;
		p->post_effect_filter_cb = pef_cb;
		p->post_effect_filter_cr = pef_cr;
	}
	for (int i = 0; i < RE_DRAW_OPTION_MAX; i++) {
		int opt = iarray_read(r);
		if (p) p->draw_options[i] = opt;
	}
	int mag_spd = iarray_read(r);
	if (p) p->mag_speed = mag_spd;
	for (int i = 0; i < RE_NR_LIGHT_PARAMS; i++) {
		float v = iarray_read_float(r);
		if (p) p->light_params[i] = v;
	}
	float elen = iarray_read_float(r);
	float erate = iarray_read_float(r);
	vec3 ecol; load_vec3(r, ecol);
	if (p) {
		p->edge_length = elen;
		p->edge_reduction_rate = erate;
		glm_vec3_copy(ecol, p->edge_color);
	}

	int saved_nr = iarray_read(r);
	if (saved_nr < 0)
		return false;
	if (p && saved_nr > p->nr_instances) {
		p->instances = xrealloc_array(p->instances, p->nr_instances, saved_nr, sizeof(struct RE_instance *));
		p->nr_instances = saved_nr;
	}
	for (int i = 0; i < saved_nr; i++) {
		if (iarray_read(r))
			load_re_instance(p, i, r);
	}
	return true;
}
