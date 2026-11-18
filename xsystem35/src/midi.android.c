/*
 * Copyright (C) 2020 <KichikuouChrome@gmail.com>
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
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
*/

#include "sdl_compat.h"
#include <jni.h>
#include "system.h"
#include "portab.h"
#include "midi.h"
#include "music_private.h"

static void midi_stop(void);
static int current_vol = 100;

static float calc_midi_gain(void) {
	float g = (float)(current_vol * prv.volval[BGM_VOLVAL_CH]) / 10000.0f;
	if (g < 0.0f) return 0.0f;
	if (g > 1.0f) return 1.0f;
	return g;
}

static void call_activity_void(const char *method) {
	JNIEnv *env = SDL_GetAndroidJNIEnv();
	if (!env || (*env)->PushLocalFrame(env, 16) < 0) {
		WARNING("Failed to allocate JVM local references");
		return;
	}
	jobject context = SDL_GetAndroidActivity();
	jmethodID mid = (*env)->GetMethodID(env, (*env)->GetObjectClass(env, context), method, "()V");
	(*env)->CallVoidMethod(env, context, mid);
	(*env)->PopLocalFrame(env, NULL);
}

static void midi_apply_volume(void) {
	JNIEnv *env = SDL_GetAndroidJNIEnv();
	if (!env || (*env)->PushLocalFrame(env, 16) < 0) {
		WARNING("Failed to allocate JVM local references");
		return;
	}
	jobject context = SDL_GetAndroidActivity();
	jmethodID mid = (*env)->GetMethodID(env, (*env)->GetObjectClass(env, context),
										"midiSetVolume", "(F)V");
	(*env)->CallVoidMethod(env, context, mid, (jfloat)calc_midi_gain());
	(*env)->PopLocalFrame(env, NULL);
}

static bool midi_initialize(int subdev) {
	current_vol = 100;
	return true;
}

static void midi_exit(void) {
	midi_stop();
}

static void midi_reset(void) {
	current_vol = 100;
	midi_stop();
}

static bool midi_start(int no, int loop, const uint8_t *data, int datalen) {
	JNIEnv *env = SDL_GetAndroidJNIEnv();
	if ((*env)->PushLocalFrame(env, 16) < 0) {
		WARNING("Failed to allocate JVM local references");
		return false;
	}

	char path[PATH_MAX];
	snprintf(path, PATH_MAX, "%s/tmp.mid", SDL_GetAndroidInternalStoragePath());
	FILE* fp = fopen(path, "w");
	if (!fp) {
		WARNING("Failed to create temporary file");
		(*env)->PopLocalFrame(env, NULL);
		return false;
	}
	fwrite(data, datalen, 1, fp);
	fclose(fp);

	jstring path_str = (*env)->NewStringUTF(env, path);
	if (!path_str) {
		WARNING("Failed to allocate a string");
		(*env)->PopLocalFrame(env, NULL);
		return false;
	}

	jobject context = SDL_GetAndroidActivity();
	jclass cls = (*env)->GetObjectClass(env, context);
	jmethodID setvol = (*env)->GetMethodID(env, cls, "midiSetVolume", "(F)V");
	(*env)->CallVoidMethod(env, context, setvol, (jfloat)calc_midi_gain());
	jmethodID mid = (*env)->GetMethodID(env, cls, "midiStart", "(Ljava/lang/String;I)V");
	(*env)->CallVoidMethod(env, context, mid, path_str, (jint)loop);
	(*env)->PopLocalFrame(env, NULL);

	return true;
}

static void midi_stop(void) {
	call_activity_void("midiStop");
}

static void midi_pause(void) {
	call_activity_void("midiPause");
}

static void midi_unpause(void) {
	call_activity_void("midiUnpause");
}

static bool midi_get_playing_info(midiplaystate *st) {
	st->in_play = false;
	st->loc_ms  = 0;

	JNIEnv *env = SDL_GetAndroidJNIEnv();
	if ((*env)->PushLocalFrame(env, 16) < 0) {
		WARNING("Failed to allocate JVM local references");
		return false;
	}
	jobject context = SDL_GetAndroidActivity();
	jmethodID mid = (*env)->GetMethodID(env, (*env)->GetObjectClass(env, context),
										"midiCurrentPosition", "()I");
	int pos = (*env)->CallIntMethod(env, context, mid);
	(*env)->PopLocalFrame(env, NULL);

	if (pos >= 0) {
		st->in_play = true;
		st->loc_ms = pos;
	}
	return true;
}

static int midi_getflag(int mode, int index) {
	return 0;
}

static bool midi_setflag(int mode, int index, int val) {
	return false;
}

static bool midi_fadestart(int time, int volume, int stop) {
	current_vol = volume < 0 ? 0 : (volume > 100 ? 100 : volume);
	if (time <= 0) {
		midi_apply_volume();
		if (stop)
			midi_stop();
		return true;
	}
	JNIEnv *env = SDL_GetAndroidJNIEnv();
	if (!env || (*env)->PushLocalFrame(env, 16) < 0) {
		WARNING("Failed to allocate JVM local references");
		return false;
	}
	jobject context = SDL_GetAndroidActivity();
	jmethodID mid = (*env)->GetMethodID(env, (*env)->GetObjectClass(env, context),
										"midiFadeStart", "(IFZ)V");
	(*env)->CallVoidMethod(env, context, mid, (jint)time, (jfloat)calc_midi_gain(), (jboolean)(stop != 0));
	(*env)->PopLocalFrame(env, NULL);
	return true;
}

static bool midi_fading(void) {
	JNIEnv *env = SDL_GetAndroidJNIEnv();
	if (!env || (*env)->PushLocalFrame(env, 16) < 0)
		return false;
	jobject context = SDL_GetAndroidActivity();
	jmethodID mid = (*env)->GetMethodID(env, (*env)->GetObjectClass(env, context),
										"midiIsFading", "()Z");
	jboolean fading = (*env)->CallBooleanMethod(env, context, mid);
	(*env)->PopLocalFrame(env, NULL);
	return fading != JNI_FALSE;
}

static void midi_reapply_volume(void) {
	midi_apply_volume();
}

mididevice_t midi_android = {
	.init = midi_initialize,
	.exit = midi_exit,
	.reset = midi_reset,
	.start = midi_start,
	.stop = midi_stop,
	.pause = midi_pause,
	.unpause = midi_unpause,
	.getpos = midi_get_playing_info,
	.getflag = midi_getflag,
	.setflag = midi_setflag,
	.fadestart = midi_fadestart,
	.fading = midi_fading,
	.reapply_volume = midi_reapply_volume,
};
