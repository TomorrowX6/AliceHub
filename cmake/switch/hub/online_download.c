#include "online_download.h"

#include <curl/curl.h>
#include <errno.h>
#include <inttypes.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <switch.h>
#include <sys/stat.h>
#include <unistd.h>

static bool stop(OnlineTransfer *transfer)
{
    return transfer->stopping(transfer->context);
}

static int progress(void *data, curl_off_t total, curl_off_t now,
                    curl_off_t upload_total, curl_off_t uploaded)
{
    (void)total;
    (void)now;
    (void)upload_total;
    (void)uploaded;
    return stop(data);
}

static CURL *connect_to(const char *url, OnlineTransfer *transfer)
{
    CURL *curl = curl_easy_init();
    if (!curl) {
        snprintf(transfer->error, sizeof(transfer->error), "Could not initialize HTTPS");
        return NULL;
    }
    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_PROTOCOLS, (long)CURLPROTO_HTTPS);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 0L);
    curl_easy_setopt(curl, CURLOPT_USERAGENT, "AliceHub");
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 2L);
    curl_easy_setopt(curl, CURLOPT_ACCEPT_ENCODING, "identity");
    curl_easy_setopt(curl, CURLOPT_HTTP_CONTENT_DECODING, 0L);
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, 8L);
    curl_easy_setopt(curl, CURLOPT_LOW_SPEED_LIMIT, 1L);
    curl_easy_setopt(curl, CURLOPT_LOW_SPEED_TIME, 12L);
    curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
    curl_easy_setopt(curl, CURLOPT_NOPROGRESS, 0L);
    curl_easy_setopt(curl, CURLOPT_XFERINFOFUNCTION, progress);
    curl_easy_setopt(curl, CURLOPT_XFERINFODATA, transfer);
    return curl;
}

typedef struct {
    char *text;
    size_t length, capacity, limit;
} ManifestBody;

static size_t manifest_body(char *body, size_t size, size_t count, void *data)
{
    ManifestBody *buffer = data;
    size_t n = size * count;
    if (n > buffer->limit - buffer->length || memchr(body, 0, n))
        return 0;
    size_t required = buffer->length + n + 1;
    if (required > buffer->capacity) {
        size_t next = buffer->capacity ? buffer->capacity : 4096;
        while (next < required)
            next *= 2;
        char *text = realloc(buffer->text, next);
        if (!text)
            return 0;
        buffer->text = text;
        buffer->capacity = next;
    }
    memcpy(buffer->text + buffer->length, body, n);
    buffer->length += n;
    buffer->text[buffer->length] = 0;
    return n;
}

static void http_error(OnlineTransfer *transfer, const char *subject, long status, CURLcode result)
{
    if (status >= 300)
        snprintf(transfer->error, sizeof(transfer->error), "%s: HTTP %ld", subject, status);
    else
        snprintf(transfer->error, sizeof(transfer->error), "%s: %s",
                 subject, curl_easy_strerror(result));
}

bool online_fetch_json(const char *id, const char *key, size_t limit, char **json, OnlineTransfer *transfer)
{
    char url[ONLINE_URL_SIZE];
    if (!limit || limit > ONLINE_MANIFEST_LIMIT || !online_payload_url(id, key, url))
        return false;
    CURL *curl = connect_to(url, transfer);
    if (!curl)
        return false;
    ManifestBody body = {.limit = limit};
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, manifest_body);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &body);
    CURLcode result = curl_easy_perform(curl);
    long status = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &status);
    curl_easy_cleanup(curl);
    if (result != CURLE_OK || status != 200) {
        http_error(transfer, "Manifest", status, result);
        free(body.text);
        return false;
    }
    *json = body.text;
    return true;
}

bool online_fetch_manifest(const char *id, OnlineManifest *manifest, OnlineTransfer *transfer)
{
    char *json;
    if (!online_fetch_json(id, NULL, ONLINE_MANIFEST_LIMIT, &json, transfer))
        return false;
    bool okay = json && online_manifest_parse(json, manifest);
    if (!okay)
        snprintf(transfer->error, sizeof(transfer->error), "Invalid manifest or file exceeds FAT32 limit");
    free(json);
    return okay;
}

static bool cache_paths(const char *cache, const OnlineEntry *entry,
                        char path[HUB_PATH_SIZE], char meta[HUB_PATH_SIZE])
{
    return (size_t)snprintf(path, HUB_PATH_SIZE, "%s/%s.part", cache, entry->sha256) < HUB_PATH_SIZE &&
           (size_t)snprintf(meta, HUB_PATH_SIZE, "%s/%s.resume", cache, entry->sha256) < HUB_PATH_SIZE;
}

static bool read_line(FILE *file, char *line, size_t cap)
{
    if (!fgets(line, cap, file))
        return false;
    size_t n = strlen(line);
    if (!n || line[n - 1] != '\n')
        return false;
    line[n - 1] = 0;
    return true;
}

static bool read_resume(const char *meta, const char *url, const OnlineEntry *entry,
                        char tag[ONLINE_ETAG_SIZE])
{
    char line[ONLINE_URL_SIZE + 1], size[32];
    snprintf(size, sizeof(size), "%" PRIu64, entry->size);
    FILE *file = fopen(meta, "rb");
    if (!file)
        return false;
    bool okay = read_line(file, line, sizeof(line)) && !strcmp(line, url) &&
                read_line(file, line, sizeof(line)) && !strcmp(line, size) &&
                read_line(file, line, sizeof(line)) && online_strong_etag(line) && fgetc(file) == EOF;
    if (okay)
        strcpy(tag, line);
    fclose(file);
    return okay;
}

static bool write_resume(const char *meta, const char *url, const OnlineEntry *entry, const char *tag)
{
    bool removed = !unlink(meta) || errno == ENOENT;
    if (!removed || !online_strong_etag(tag))
        return removed;
    FILE *file = fopen(meta, "wb");
    if (!file)
        return false;
    bool okay = fprintf(file, "%s\n%" PRIu64 "\n%s\n", url, entry->size, tag) >= 0 &&
                !fflush(file) && !fsync(fileno(file));
    return !fclose(file) && okay;
}

static uint64_t present_bytes(const char *path, uint64_t max)
{
    struct stat st;
    if (stat(path, &st) || !S_ISREG(st.st_mode) || st.st_size < 0 || (uint64_t)st.st_size > max)
        return 0;
    return (uint64_t)st.st_size;
}

uint64_t online_cached_bytes(const char *id, const OnlineEntry *entry, const char *cache)
{
    char path[HUB_PATH_SIZE], meta[HUB_PATH_SIZE], url[ONLINE_URL_SIZE], tag[ONLINE_ETAG_SIZE];
    if (!cache_paths(cache, entry, path, meta) || !online_payload_url(id, entry->key, url))
        return 0;
    uint64_t size = present_bytes(path, entry->size);
    return size == entry->size || (size && read_resume(meta, url, entry, tag)) ? size : 0;
}

bool online_verified_file(const char *path, const OnlineEntry *entry, OnlineTransfer *transfer)
{
    struct stat st;
    if (stat(path, &st) || !S_ISREG(st.st_mode) || (uint64_t)st.st_size != entry->size || stop(transfer))
        return false;
    FILE *file = fopen(path, "rb");
    if (!file)
        return false;
    Sha256Context sha;
    sha256ContextCreate(&sha);
    unsigned char block[32768], hash[32];
    uint64_t read = 0;
    for (size_t n; !stop(transfer) && (n = fread(block, 1, sizeof(block), file));) {
        sha256ContextUpdate(&sha, block, n);
        read += n;
    }
    bool complete = !ferror(file) && read == entry->size && !stop(transfer);
    fclose(file);
    if (!complete)
        return false;
    sha256ContextGetHash(&sha, hash);
    char actual[ONLINE_SHA_SIZE];
    for (size_t i = 0; i < sizeof(hash); ++i)
        snprintf(actual + i * 2, 3, "%02x", hash[i]);
    return !strcmp(actual, entry->sha256);
}

typedef struct {
    const OnlineEntry *entry;
    const char *path, *meta, *url;
    OnlineTransfer *transfer;
    FILE *output;
    uint64_t offset, present, received;
    long status;
    bool rejected, malformed, encoding, storage_error;
    char previous_tag[ONLINE_ETAG_SIZE], tag[ONLINE_ETAG_SIZE], range[96], length[32];
} Payload;

static bool header_value(const char *line, const char *name, char *out, size_t cap)
{
    size_t key = strlen(name);
    if (strncasecmp(line, name, key) || line[key] != ':')
        return false;
    line += key + 1;
    while (*line == ' ' || *line == '\t')
        ++line;
    size_t length = strlen(line);
    while (length && (line[length - 1] == ' ' || line[length - 1] == '\t'))
        --length;
    if (!length || length >= cap || *out) {
        *out = 0;
        return true;
    }
    memcpy(out, line, length);
    out[length] = 0;
    return false;
}

static bool prepare_payload(Payload *p)
{
    bool append = p->status == 206 &&
                  online_resume_matches(p->range, p->tag, p->previous_tag, p->offset, p->entry->size);
    p->present = append ? p->offset : 0;
    char length[32];
    snprintf(length, sizeof(length), "%" PRIu64, p->entry->size - p->present);
    if (p->malformed || p->encoding || (!append && p->status != 200) ||
        (*p->length && strcmp(p->length, length)) || (!append && *p->range)) {
        p->rejected = true;
        return false;
    }
    p->output = fopen(p->path, append ? "ab" : "wb");
    if (!p->output || (!append && p->meta && !write_resume(p->meta, p->url, p->entry, p->tag))) {
        p->storage_error = true;
        return false;
    }
    return true;
}

static size_t payload_header(char *header, size_t size, size_t count, void *data)
{
    Payload *p = data;
    size_t n = size * count;
    if (p->output)
        return n;
    if (n >= 4096 || memchr(header, 0, n))
        return 0;
    char line[4096];
    memcpy(line, header, n);
    line[n] = 0;
    line[strcspn(line, "\r\n")] = 0;
    if (!strncmp(line, "HTTP/", 5)) {
        p->status = 0;
        sscanf(line, "HTTP/%*s %ld", &p->status);
        *p->tag = *p->range = *p->length = 0;
        p->malformed = p->encoding = false;
    } else if (!*line && p->status >= 200) {
        if (!prepare_payload(p))
            return 0;
    } else if (*line) {
        char encoding[24] = {0};
        p->malformed |= header_value(line, "ETag", p->tag, sizeof(p->tag));
        p->malformed |= header_value(line, "Content-Range", p->range, sizeof(p->range));
        p->malformed |= header_value(line, "Content-Length", p->length, sizeof(p->length));
        p->malformed |= header_value(line, "Content-Encoding", encoding, sizeof(encoding));
        p->encoding |= *encoding && strcasecmp(encoding, "identity");
    }
    return n;
}

static size_t payload_body(char *body, size_t size, size_t count, void *data)
{
    Payload *p = data;
    size_t n = size * count;
    if (!p->output || stop(p->transfer) || n > p->entry->size - p->present)
        return 0;
    size_t written = fwrite(body, 1, n, p->output);
    p->present += written;
    p->received += written;
    if (written != n)
        p->storage_error = true;
    p->transfer->report(p->transfer->context, p->present, p->received);
    return written;
}

static bool fetch_payload(Payload *p)
{
    CURL *curl = connect_to(p->url, p->transfer);
    if (!curl)
        return false;
    struct curl_slist *headers = NULL;
    if (p->offset) {
        char range[32], if_range[ONLINE_ETAG_SIZE + 16];
        snprintf(range, sizeof(range), "%" PRIu64 "-", p->offset);
        snprintf(if_range, sizeof(if_range), "If-Range: %s", p->previous_tag);
        headers = curl_slist_append(NULL, if_range);
        if (!headers) {
            curl_easy_cleanup(curl);
            return false;
        }
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
        curl_easy_setopt(curl, CURLOPT_RANGE, range);
    }
    curl_easy_setopt(curl, CURLOPT_HEADERFUNCTION, payload_header);
    curl_easy_setopt(curl, CURLOPT_HEADERDATA, p);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, payload_body);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, p);
    CURLcode result = curl_easy_perform(curl);
    long status = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &status);
    if (p->output) {
        if (fflush(p->output) || fsync(fileno(p->output)))
            p->storage_error = true;
        if (fclose(p->output))
            p->storage_error = true;
    }
    curl_easy_cleanup(curl);
    curl_slist_free_all(headers);
    if (p->storage_error)
        snprintf(p->transfer->error, sizeof(p->transfer->error), "Could not write download to SD");
    else if (p->rejected && status < 300)
        snprintf(p->transfer->error, sizeof(p->transfer->error), "Server sent an invalid download range or length");
    else if (result != CURLE_OK)
        http_error(p->transfer, "Download", status, result);
    else if (p->present != p->entry->size)
        snprintf(p->transfer->error, sizeof(p->transfer->error), "Incomplete response");
    else
        return true;
    return false;
}

bool online_fetch_file(const char *id, const OnlineEntry *entry, const char *cache,
                       char path[HUB_PATH_SIZE], OnlineTransfer *transfer)
{
    char meta[HUB_PATH_SIZE], url[ONLINE_URL_SIZE];
    if (!cache_paths(cache, entry, path, meta) || !online_payload_url(id, entry->key, url))
        return false;
    uint64_t offset = present_bytes(path, entry->size);
    if (offset == entry->size && online_verified_file(path, entry, transfer))
        return true;
    if (stop(transfer))
        return false;
    Payload p = {.entry = entry, .path = path, .meta = meta, .url = url, .transfer = transfer};
    if (offset && offset < entry->size && read_resume(meta, url, entry, p.previous_tag))
        p.offset = offset;
    if (!fetch_payload(&p)) {
        if (!p.offset || stop(transfer) || (p.status != 416 && !(p.status == 206 && p.rejected)))
            return false;
        p = (Payload){.entry = entry, .path = path, .meta = meta, .url = url, .transfer = transfer};
        if (!fetch_payload(&p))
            return false;
    }
    if (online_verified_file(path, entry, transfer))
        return true;
    if (!stop(transfer)) {
        unlink(path);
        unlink(meta);
        snprintf(transfer->error, sizeof(transfer->error), "SHA-256 mismatch: %.128s", entry->path);
    }
    return false;
}

bool online_fetch_verified_to(const char *id, const OnlineEntry *entry, const char *path,
                              OnlineTransfer *transfer)
{
    char url[ONLINE_URL_SIZE];
    if (!entry->size || entry->size > ONLINE_FILE_LIMIT ||
        !online_payload_url(id, entry->key, url) || stop(transfer))
        return false;
    Payload p = {.entry = entry, .path = path, .url = url, .transfer = transfer};
    if (!fetch_payload(&p))
        return false;
    if (online_verified_file(path, entry, transfer))
        return true;
    if (!stop(transfer))
        snprintf(transfer->error, sizeof(transfer->error), "SHA-256 mismatch: %.128s", entry->path);
    return false;
}
