#include <jni.h>
#include <archive.h>
#include <archive_entry.h>
#include <errno.h>
#include <fcntl.h>
#include <locale.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#ifdef __ANDROID__
#include <android/log.h>
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ArchiveJNI", __VA_ARGS__)
#else
#define LOGE(...) do { fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while (0)
#endif

static pthread_once_t g_locale_once = PTHREAD_ONCE_INIT;

static void init_utf8_locale(void)
{
	if (!setlocale(LC_CTYPE, "C.UTF-8")) {
		setlocale(LC_CTYPE, "en_US.UTF-8");
	}
}

static int is_valid_utf8(const char *s, size_t len)
{
	const uint8_t *p = (const uint8_t *)s;
	size_t i = 0;
	while (i < len) {
		uint8_t b0 = p[i];
		if (b0 < 0x80) {
			if (b0 == 0) {
				return 0;
			}
			i += 1;
		} else if (b0 >= 0xC2 && b0 <= 0xDF) {
			if (i + 1 >= len || (p[i + 1] & 0xC0) != 0x80) {
				return 0;
			}
			i += 2;
		} else if (b0 >= 0xE0 && b0 <= 0xEF) {
			if (i + 2 >= len) {
				return 0;
			}
			uint8_t b1 = p[i + 1];
			uint8_t b2 = p[i + 2];
			if ((b2 & 0xC0) != 0x80) {
				return 0;
			}
			if (b0 == 0xE0) {
				if (b1 < 0xA0 || b1 > 0xBF) return 0;
			} else if (b0 == 0xED) {
				if (b1 < 0x80 || b1 > 0x9F) return 0;
			} else {
				if ((b1 & 0xC0) != 0x80) return 0;
			}
			i += 3;
		} else if (b0 >= 0xF0 && b0 <= 0xF4) {
			if (i + 3 >= len) {
				return 0;
			}
			uint8_t b1 = p[i + 1];
			uint8_t b2 = p[i + 2];
			uint8_t b3 = p[i + 3];
			if ((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80) {
				return 0;
			}
			if (b0 == 0xF0) {
				if (b1 < 0x90 || b1 > 0xBF) return 0;
			} else if (b0 == 0xF4) {
				if (b1 < 0x80 || b1 > 0x8F) return 0;
			} else {
				if ((b1 & 0xC0) != 0x80) return 0;
			}
			i += 4;
		} else {
			return 0;
		}
	}
	return 1;
}

static char *mutf8_to_utf8_alloc(const char *mutf8)
{
	if (!mutf8) {
		return NULL;
	}
	size_t in_len = strlen(mutf8);
	if (in_len > (SIZE_MAX - 1) / 3) {
		return NULL;
	}
	char *out = (char *)malloc(in_len * 3 + 1);
	if (!out) {
		return NULL;
	}
	const uint8_t *p = (const uint8_t *)mutf8;
	size_t i = 0;
	size_t j = 0;
	while (i < in_len) {
		uint8_t b0 = p[i];
		if (b0 == 0xC0 && i + 1 < in_len && p[i + 1] == 0x80) {
			/* Modified UTF-8 overlong NUL (0xC0 0x80) -> 0x00 */
			out[j++] = '\0';
			i += 2;
		} else if (b0 < 0x80) {
			out[j++] = (char)b0;
			i += 1;
		} else if (b0 >= 0xC2 && b0 <= 0xDF && i + 1 < in_len && (p[i + 1] & 0xC0) == 0x80) {
			out[j++] = (char)b0;
			out[j++] = (char)p[i + 1];
			i += 2;
		} else if (b0 == 0xED && i + 5 < in_len &&
			p[i + 1] >= 0xA0 && p[i + 1] <= 0xAF && (p[i + 2] & 0xC0) == 0x80 &&
			p[i + 3] == 0xED &&
			p[i + 4] >= 0xB0 && p[i + 4] <= 0xBF && (p[i + 5] & 0xC0) == 0x80) {
			/* CESU-8 6-byte surrogate pair -> standard 4-byte UTF-8 */
			uint32_t hi = 0xD800U | ((uint32_t)(p[i + 1] & 0x0FU) << 6) | (uint32_t)(p[i + 2] & 0x3FU);
			uint32_t lo = 0xDC00U | ((uint32_t)(p[i + 4] & 0x0FU) << 6) | (uint32_t)(p[i + 5] & 0x3FU);
			uint32_t cp = 0x10000U + (((hi - 0xD800U) << 10) | (lo - 0xDC00U));
			out[j++] = (char)(0xF0U | (cp >> 18));
			out[j++] = (char)(0x80U | ((cp >> 12) & 0x3FU));
			out[j++] = (char)(0x80U | ((cp >> 6) & 0x3FU));
			out[j++] = (char)(0x80U | (cp & 0x3FU));
			i += 6;
		} else if (b0 == 0xED && i + 2 < in_len &&
			p[i + 1] >= 0xA0 && p[i + 1] <= 0xBF && (p[i + 2] & 0xC0) == 0x80) {
			/* Unpaired UTF-16 surrogate -> U+FFFD */
			out[j++] = (char)0xEF;
			out[j++] = (char)0xBF;
			out[j++] = (char)0xBD;
			i += 3;
		} else if (b0 >= 0xE0 && b0 <= 0xEF && i + 2 < in_len &&
			(p[i + 2] & 0xC0) == 0x80 &&
			((b0 == 0xE0 && p[i + 1] >= 0xA0 && p[i + 1] <= 0xBF) ||
			 (b0 == 0xED && p[i + 1] >= 0x80 && p[i + 1] <= 0x9F) ||
			 (b0 != 0xE0 && b0 != 0xED && (p[i + 1] & 0xC0) == 0x80))) {
			out[j++] = (char)b0;
			out[j++] = (char)p[i + 1];
			out[j++] = (char)p[i + 2];
			i += 3;
		} else if (b0 >= 0xF0 && b0 <= 0xF4 && i + 3 < in_len &&
			(p[i + 2] & 0xC0) == 0x80 && (p[i + 3] & 0xC0) == 0x80 &&
			((b0 == 0xF0 && p[i + 1] >= 0x90 && p[i + 1] <= 0xBF) ||
			 (b0 == 0xF4 && p[i + 1] >= 0x80 && p[i + 1] <= 0x8F) ||
			 (b0 >= 0xF1 && b0 <= 0xF3 && (p[i + 1] & 0xC0) == 0x80))) {
			out[j++] = (char)b0;
			out[j++] = (char)p[i + 1];
			out[j++] = (char)p[i + 2];
			out[j++] = (char)p[i + 3];
			i += 4;
		} else {
			/* Malformed byte -> U+FFFD */
			out[j++] = (char)0xEF;
			out[j++] = (char)0xBF;
			out[j++] = (char)0xBD;
			i += 1;
		}
	}
	out[j] = '\0';
	return out;
}

static jstring jni_new_string_from_utf8(JNIEnv *env, const char *utf8)
{
	if (!utf8) {
		return NULL;
	}
	size_t len = strlen(utf8);
	if (len == 0) {
		jchar empty = 0;
		return (*env)->NewString(env, &empty, 0);
	}
	if (len > (size_t)INT32_MAX) {
		len = (size_t)INT32_MAX;
	}

	jchar stack_buf[256];
	jchar *u16_buf = stack_buf;
	if (len > 256) {
		u16_buf = (jchar *)malloc(len * sizeof(jchar));
		if (!u16_buf) {
			return NULL;
		}
	}

	const uint8_t *p = (const uint8_t *)utf8;
	size_t i = 0;
	size_t u16_len = 0;
	while (i < len) {
		uint8_t b0 = p[i];
		uint32_t cp = 0xFFFDU;
		if (b0 < 0x80) {
			cp = b0;
			i += 1;
		} else if (b0 >= 0xC2 && b0 <= 0xDF) {
			if (i + 1 < len && (p[i + 1] & 0xC0) == 0x80) {
				cp = ((uint32_t)(b0 & 0x1FU) << 6) | (uint32_t)(p[i + 1] & 0x3FU);
				i += 2;
			} else {
				i += 1;
			}
		} else if (b0 >= 0xE0 && b0 <= 0xEF) {
			if (i + 2 < len && (p[i + 2] & 0xC0) == 0x80 &&
				((b0 == 0xE0 && p[i + 1] >= 0xA0 && p[i + 1] <= 0xBF) ||
				 (b0 == 0xED && p[i + 1] >= 0x80 && p[i + 1] <= 0x9F) ||
				 (b0 != 0xE0 && b0 != 0xED && (p[i + 1] & 0xC0) == 0x80))) {
				cp = ((uint32_t)(b0 & 0x0FU) << 12) |
				     ((uint32_t)(p[i + 1] & 0x3FU) << 6) |
				     (uint32_t)(p[i + 2] & 0x3FU);
				i += 3;
			} else {
				i += 1;
			}
		} else if (b0 >= 0xF0 && b0 <= 0xF4) {
			if (i + 3 < len &&
				(p[i + 2] & 0xC0) == 0x80 && (p[i + 3] & 0xC0) == 0x80 &&
				((b0 == 0xF0 && p[i + 1] >= 0x90 && p[i + 1] <= 0xBF) ||
				 (b0 == 0xF4 && p[i + 1] >= 0x80 && p[i + 1] <= 0x8F) ||
				 (b0 >= 0xF1 && b0 <= 0xF3 && (p[i + 1] & 0xC0) == 0x80))) {
				cp = ((uint32_t)(b0 & 0x07U) << 18) |
				     ((uint32_t)(p[i + 1] & 0x3FU) << 12) |
				     ((uint32_t)(p[i + 2] & 0x3FU) << 6) |
				     (uint32_t)(p[i + 3] & 0x3FU);
				i += 4;
			} else {
				i += 1;
			}
		} else {
			i += 1;
		}

		if (cp <= 0xFFFFU) {
			u16_buf[u16_len++] = (jchar)cp;
		} else {
			uint32_t adj = cp - 0x10000U;
			u16_buf[u16_len++] = (jchar)(0xD800U + (adj >> 10));
			u16_buf[u16_len++] = (jchar)(0xDC00U + (adj & 0x3FFU));
		}
	}

	jstring result = (*env)->NewString(env, u16_buf, (jsize)u16_len);
	if (u16_buf != stack_buf) {
		free(u16_buf);
	}
	return result;
}

static void throw_io_exception(JNIEnv *env, const char *msg)
{
	if ((*env)->ExceptionCheck(env)) {
		return;
	}
	jclass ioExCls = (*env)->FindClass(env, "java/io/IOException");
	if (!ioExCls || (*env)->ExceptionCheck(env)) {
		return;
	}
	const char *safe_msg = msg ? msg : "Archive extraction error";
	jmethodID ctor = (*env)->GetMethodID(env, ioExCls, "<init>", "(Ljava/lang/String;)V");
	if (ctor && !(*env)->ExceptionCheck(env)) {
		jstring jMsg = jni_new_string_from_utf8(env, safe_msg);
		if (jMsg && !(*env)->ExceptionCheck(env)) {
			jthrowable exObj = (jthrowable)(*env)->NewObject(env, ioExCls, ctor, jMsg);
			(*env)->DeleteLocalRef(env, jMsg);
			if (exObj && !(*env)->ExceptionCheck(env)) {
				(*env)->Throw(env, exObj);
				(*env)->DeleteLocalRef(env, exObj);
				(*env)->DeleteLocalRef(env, ioExCls);
				return;
			}
			if (exObj) {
				(*env)->DeleteLocalRef(env, exObj);
			}
		} else if (jMsg) {
			(*env)->DeleteLocalRef(env, jMsg);
		}
	}
	if (!(*env)->ExceptionCheck(env)) {
		(*env)->ThrowNew(env, ioExCls, "Archive extraction error");
	}
	(*env)->DeleteLocalRef(env, ioExCls);
}

static int write_all_fd(int fd, const uint8_t *buf, size_t len, int *out_errno)
{
	size_t off = 0;
	while (off < len) {
		ssize_t n = write(fd, buf + off, len - off);
		if (n < 0) {
			if (errno == EINTR) {
				continue;
			}
			if (out_errno) {
				*out_errno = errno;
			}
			return -1;
		}
		if (n == 0) {
			if (out_errno) {
				*out_errno = EIO;
			}
			return -1;
		}
		off += (size_t)n;
	}
	return 0;
}

struct extracted_path {
	char *name;
	char *path;
	struct extracted_path *next;
};

static int copy_link_target(const char *source, const char *destination)
{
	int in_fd = open(source, O_RDONLY | O_CLOEXEC);
	if (in_fd < 0) return -1;
	int out_fd = open(destination, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, 0644);
	if (out_fd < 0) {
		int saved_errno = errno;
		close(in_fd);
		errno = saved_errno;
		return -1;
	}
	uint8_t buffer[131072];
	int error = 0;
	for (;;) {
		ssize_t n = read(in_fd, buffer, sizeof(buffer));
		if (n < 0 && errno == EINTR) continue;
		if (n < 0) {
			error = errno;
			break;
		}
		if (n == 0) break;
		if (write_all_fd(out_fd, buffer, (size_t)n, &error) != 0) break;
	}
	close(in_fd);
	if (close(out_fd) != 0 && !error) error = errno;
	if (error) {
		unlink(destination);
		errno = error;
		return -1;
	}
	return 0;
}

static int replace_hard_link(const char *source, const char *destination)
{
	struct stat st;
	if (lstat(destination, &st) == 0) {
		if (S_ISDIR(st.st_mode)) {
			errno = EISDIR;
			return -1;
		}
	} else if (errno != ENOENT) {
		return -1;
	}

	const char *slash = strrchr(destination, '/');
	size_t parent_len = slash ? (size_t)(slash - destination + 1) : 0;
	char *temp_dir = malloc(parent_len + sizeof(".archive_link_XXXXXX"));
	if (!temp_dir) return -1;
	memcpy(temp_dir, destination, parent_len);
	strcpy(temp_dir + parent_len, ".archive_link_XXXXXX");
	if (!mkdtemp(temp_dir)) {
		int error = errno;
		free(temp_dir);
		errno = error;
		return -1;
	}
	char *temp_path = malloc(strlen(temp_dir) + sizeof("/content"));
	int error = 0;
	if (!temp_path) {
		error = ENOMEM;
	} else {
		sprintf(temp_path, "%s/content", temp_dir);
		if (link(source, temp_path) != 0) {
			int link_error = errno;
			if ((link_error != EPERM && link_error != EXDEV && link_error != ENOTSUP &&
				link_error != EOPNOTSUPP && link_error != EMLINK) ||
				copy_link_target(source, temp_path) != 0) {
				error = errno;
			}
		}
		if (!error && rename(temp_path, destination) != 0) error = errno;
		unlink(temp_path);
		free(temp_path);
	}
	rmdir(temp_dir);
	free(temp_dir);
	if (error) {
		errno = error;
		return -1;
	}
	return 0;
}

static char *relative_link_name(const char *name)
{
	if (!name || !*name || *name == '/' || *name == '\\' || strchr(name, ':')) return NULL;
	char *result = strdup(name);
	if (!result) return NULL;
	char *out = result;
	const char *p = name;
	while (*p) {
		const char *start = p;
		while (*p && *p != '/' && *p != '\\') p++;
		size_t len = (size_t)(p - start);
		if (len == 2 && start[0] == '.' && start[1] == '.') {
			free(result);
			return NULL;
		}
		if (len && !(len == 1 && start[0] == '.')) {
			if (out != result) *out++ = '/';
			memcpy(out, start, len);
			out += len;
		}
		if (*p) p++;
	}
	*out = '\0';
	if (out == result) {
		free(result);
		return NULL;
	}
	return result;
}

JNIEXPORT jboolean JNICALL
Java_io_github_kichikuou_xsystem4_ArchiveExtractor_nativeIsFdSeekable(
	JNIEnv *env, jclass clazz, jint fd)
{
	(void)env;
	(void)clazz;
	if (fd < 0) {
		return JNI_FALSE;
	}
	off_t cur = lseek((int)fd, 0, SEEK_CUR);
	if (cur == (off_t)-1) {
		return JNI_FALSE;
	}
	off_t end = lseek((int)fd, 0, SEEK_END);
	if (end == (off_t)-1) {
		return JNI_FALSE;
	}
	if (lseek((int)fd, cur, SEEK_SET) == (off_t)-1) {
		return JNI_FALSE;
	}
	return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_io_github_kichikuou_xsystem4_ArchiveExtractor_nativeExtractFd(
	JNIEnv *env, jclass clazz, jint fd, jobject callback)
{
	(void)clazz;
	pthread_once(&g_locale_once, init_utf8_locale);

	if (fd < 0 || callback == NULL) {
		throw_io_exception(env, "Invalid file descriptor or callback");
		return -1;
	}

	jclass cbCls = (*env)->GetObjectClass(env, callback);
	if (!cbCls || (*env)->ExceptionCheck(env)) {
		return -1;
	}
	jmethodID midOnEntry = (*env)->GetMethodID(env, cbCls, "onEntry", "([BJ)Ljava/lang/String;");
	if (!midOnEntry || (*env)->ExceptionCheck(env)) {
		(*env)->DeleteLocalRef(env, cbCls);
		return -1;
	}
	jmethodID midOnProgress = (*env)->GetMethodID(env, cbCls, "onProgress", "(J)V");
	if (!midOnProgress || (*env)->ExceptionCheck(env)) {
		(*env)->DeleteLocalRef(env, cbCls);
		return -1;
	}
	(*env)->DeleteLocalRef(env, cbCls);

	(void)lseek((int)fd, 0, SEEK_SET);

	struct archive *a = archive_read_new();
	if (!a) {
		throw_io_exception(env, "Out of memory creating archive reader");
		return -1;
	}

	archive_read_support_format_zip(a);
	archive_read_support_format_rar(a);
	archive_read_support_format_rar5(a);
	archive_read_support_format_7zip(a);
	archive_read_support_format_tar(a);
	archive_read_support_filter_all(a);

	int extracted_files = -1;
	struct extracted_path *paths = NULL;
	char *pending_path = NULL;

	if (archive_read_open_fd(a, (int)fd, 131072) != ARCHIVE_OK) {
		const char *err = archive_error_string(a);
		char msg[512];
		snprintf(msg, sizeof(msg), "Cannot open archive: %s", err ? err : "unsupported or corrupt file");
		LOGE("%s", msg);
		throw_io_exception(env, msg);
		goto cleanup;
	}

	struct archive_entry *entry = NULL;
	int64_t total_written = 0;
	int64_t last_reported = 0;
	int count = 0;

	for (;;) {
		int hr = archive_read_next_header(a, &entry);
		if (hr == ARCHIVE_EOF) {
			break;
		}
		if (hr < ARCHIVE_OK && hr != ARCHIVE_WARN) {
			const char *err = archive_error_string(a);
			char msg[512];
			if (archive_read_has_encrypted_entries(a) > 0) {
				snprintf(msg, sizeof(msg), "ENCRYPTED: %s", err ? err : "Archive is password-protected");
			} else {
				snprintf(msg, sizeof(msg), "Archive header error: %s", err ? err : "corrupt or unsupported format");
			}
			LOGE("%s", msg);
			throw_io_exception(env, msg);
			goto cleanup;
		}

		if (archive_entry_is_encrypted(entry)) {
			throw_io_exception(env, "ENCRYPTED: Archive is password-protected");
			goto cleanup;
		}

		mode_t ft = archive_entry_filetype(entry);
		if (ft == AE_IFDIR || (ft != AE_IFREG && ft != 0)) {
			archive_read_data_skip(a);
			continue;
		}

		const char *raw_path = archive_entry_pathname_utf8(entry);
		if (!raw_path || !*raw_path) {
			raw_path = archive_entry_pathname(entry);
		}
		if (!raw_path || !*raw_path) {
			archive_read_data_skip(a);
			continue;
		}

		size_t path_len = strlen(raw_path);
		if (path_len > (size_t)INT32_MAX) {
			throw_io_exception(env, "Archive entry pathname exceeds maximum length");
			goto cleanup;
		}
		/* Only treat trailing '\\' (0x5C) as directory separator when raw_path is valid UTF-8,
		 * because 0x5C is a valid trail byte in Shift-JIS / GBK (e.g. ソ, 表, 十). */
		if (raw_path[path_len - 1] == '/' ||
			(raw_path[path_len - 1] == '\\' && is_valid_utf8(raw_path, path_len))) {
			archive_read_data_skip(a);
			continue;
		}

		jbyteArray jPathBytes = (*env)->NewByteArray(env, (jsize)path_len);
		if (!jPathBytes || (*env)->ExceptionCheck(env)) {
			goto cleanup;
		}
		(*env)->SetByteArrayRegion(env, jPathBytes, 0, (jsize)path_len, (const jbyte *)raw_path);
		if ((*env)->ExceptionCheck(env)) {
			(*env)->DeleteLocalRef(env, jPathBytes);
			goto cleanup;
		}

		jlong entry_size = archive_entry_size_is_set(entry) ? (jlong)archive_entry_size(entry) : (jlong)-1;
		jstring jOutPath = (jstring)(*env)->CallObjectMethod(env, callback, midOnEntry, jPathBytes, entry_size);
		(*env)->DeleteLocalRef(env, jPathBytes);
		if ((*env)->ExceptionCheck(env)) {
			if (jOutPath) {
				(*env)->DeleteLocalRef(env, jOutPath);
			}
			goto cleanup;
		}
		if (!jOutPath) {
			archive_read_data_skip(a);
			continue;
		}

		const char *mutf8_out_path = (*env)->GetStringUTFChars(env, jOutPath, NULL);
		if (!mutf8_out_path || (*env)->ExceptionCheck(env)) {
			(*env)->DeleteLocalRef(env, jOutPath);
			if (!(*env)->ExceptionCheck(env)) {
				throw_io_exception(env, "Out of memory reading output path");
			}
			goto cleanup;
		}
		jsize u16_out_len = (*env)->GetStringLength(env, jOutPath);
		int has_embedded_nul = (strstr(mutf8_out_path, "\xc0\x80") != NULL);
		char *utf8_out_path = has_embedded_nul ? NULL : mutf8_to_utf8_alloc(mutf8_out_path);
		(*env)->ReleaseStringUTFChars(env, jOutPath, mutf8_out_path);
		(*env)->DeleteLocalRef(env, jOutPath);

		if (has_embedded_nul || (u16_out_len > 0 && (!utf8_out_path || utf8_out_path[0] == '\0'))) {
			free(utf8_out_path);
			throw_io_exception(env, has_embedded_nul
				? "Invalid NUL character in output path"
				: "Out of memory converting output path to UTF-8");
			goto cleanup;
		}
		if (!utf8_out_path) {
			throw_io_exception(env, "Out of memory converting output path to UTF-8");
			goto cleanup;
		}

		pending_path = utf8_out_path;
		const char *hardlink = archive_entry_hardlink(entry);
		if (hardlink) {
			char *target = relative_link_name(hardlink);
			if (!target) {
				throw_io_exception(env, "Invalid or escaping archive hard link target");
				goto cleanup;
			}
			struct extracted_path *source = paths;
			while (source && strcmp(source->name, target)) source = source->next;
			free(target);
			if (!source) {
				throw_io_exception(env, "Archive hard link target was not previously extracted");
				goto cleanup;
			}
			if (replace_hard_link(source->path, pending_path) != 0) {
				int link_error = errno;
				char msg[512];
				snprintf(msg, sizeof(msg), "Cannot create archive hard link: %s%s",
					(link_error == ENOSPC || link_error == EDQUOT) ? "ENOSPC: " : "",
					strerror(link_error));
				throw_io_exception(env, msg);
				goto cleanup;
			}
			archive_read_data_skip(a);
			goto entry_complete;
		}

		int out_fd = open(utf8_out_path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
		int open_err = errno;

		if (out_fd < 0) {
			char msg[512];
			if (open_err == ENOSPC || open_err == EDQUOT) {
				snprintf(msg, sizeof(msg), "ENOSPC: No space left on device");
			} else {
				snprintf(msg, sizeof(msg), "Cannot create output file: %s", strerror(open_err));
			}
			LOGE("%s", msg);
			throw_io_exception(env, msg);
			goto cleanup;
		}

		const void *chunk = NULL;
		size_t chunk_len = 0;
		la_int64_t chunk_off = 0;
		int64_t cur_off = 0;

		for (;;) {
			int dr = archive_read_data_block(a, &chunk, &chunk_len, &chunk_off);
			if (dr == ARCHIVE_EOF) {
				break;
			}
			if (dr < ARCHIVE_OK && dr != ARCHIVE_WARN) {
				const char *err = archive_error_string(a);
				char msg[512];
				if (archive_read_has_encrypted_entries(a) > 0) {
					snprintf(msg, sizeof(msg), "ENCRYPTED: %s", err ? err : "Archive is password-protected");
				} else {
					snprintf(msg, sizeof(msg), "Archive data error: %s", err ? err : "corrupt or unsupported compression");
				}
				LOGE("%s", msg);
				close(out_fd);
				throw_io_exception(env, msg);
				goto cleanup;
			}
			if (chunk_off > cur_off) {
				if (lseek(out_fd, (off_t)chunk_off, SEEK_SET) == (off_t)-1) {
					int seek_err = errno;
					close(out_fd);
					throw_io_exception(env, (seek_err == ENOSPC || seek_err == EDQUOT)
						? "ENOSPC: No space left on device"
						: strerror(seek_err));
					goto cleanup;
				}
				cur_off = chunk_off;
			}
			if (chunk && chunk_len > 0) {
				int write_err = 0;
				if (write_all_fd(out_fd, (const uint8_t *)chunk, chunk_len, &write_err) != 0) {
					close(out_fd);
					char msg[512];
					if (write_err == ENOSPC || write_err == EDQUOT) {
						snprintf(msg, sizeof(msg), "ENOSPC: No space left on device");
					} else {
						snprintf(msg, sizeof(msg), "Write failed: %s", strerror(write_err));
					}
					LOGE("%s", msg);
					throw_io_exception(env, msg);
					goto cleanup;
				}
				cur_off += (int64_t)chunk_len;
				total_written += (int64_t)chunk_len;
				if (total_written - last_reported >= (2 * 1024 * 1024)) {
					last_reported = total_written;
					(*env)->CallVoidMethod(env, callback, midOnProgress, (jlong)total_written);
					if ((*env)->ExceptionCheck(env)) {
						close(out_fd);
						goto cleanup;
					}
				}
			}
		}

		if (entry_size > cur_off) {
			int tr_ret;
			do {
				tr_ret = ftruncate(out_fd, (off_t)entry_size);
			} while (tr_ret != 0 && errno == EINTR);
			if (tr_ret != 0) {
				int tr_err = errno;
				close(out_fd);
				char msg[512];
				if (tr_err == ENOSPC || tr_err == EDQUOT) {
					snprintf(msg, sizeof(msg), "ENOSPC: No space left on device");
				} else {
					snprintf(msg, sizeof(msg), "ftruncate failed: %s", strerror(tr_err));
				}
				LOGE("%s", msg);
				throw_io_exception(env, msg);
				goto cleanup;
			}
		}

		if (close(out_fd) != 0) {
			int close_err = errno;
			char msg[512];
			if (close_err == ENOSPC || close_err == EDQUOT) {
				snprintf(msg, sizeof(msg), "ENOSPC: No space left on device");
			} else {
				snprintf(msg, sizeof(msg), "Close failed: %s", strerror(close_err));
			}
			LOGE("%s", msg);
			throw_io_exception(env, msg);
			goto cleanup;
		}
	entry_complete:;
		char *name = relative_link_name(raw_path);
		if (name) {
			struct extracted_path *saved = malloc(sizeof(*saved));
			if (!saved) {
				free(name);
				throw_io_exception(env, "Out of memory recording extracted archive path");
				goto cleanup;
			}
			saved->name = name;
			saved->path = pending_path;
			saved->next = paths;
			paths = saved;
			pending_path = NULL;
		}
		free(pending_path);
		pending_path = NULL;
		count++;
	}

	if (total_written != last_reported) {
		(*env)->CallVoidMethod(env, callback, midOnProgress, (jlong)total_written);
		if ((*env)->ExceptionCheck(env)) {
			goto cleanup;
		}
	}

	extracted_files = count;

cleanup:
	free(pending_path);
	while (paths) {
		struct extracted_path *next = paths->next;
		free(paths->name);
		free(paths->path);
		free(paths);
		paths = next;
	}
	archive_read_free(a);
	return (jint)extracted_files;
}
