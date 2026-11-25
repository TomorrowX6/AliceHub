#include "hub.h"

#include <pthread.h>
#include <string.h>

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static HubProgress tasks[16];

bool hub_progress_publish(const HubProgress *task)
{
    if (!*task->id || !memchr(task->id, 0, sizeof(task->id)))
        return false;
    pthread_mutex_lock(&lock);
    HubProgress *slot = NULL;
    for (size_t i = 0; i < sizeof(tasks) / sizeof(*tasks); ++i) {
        if (!strcmp(tasks[i].id, task->id)) {
            slot = &tasks[i];
            break;
        }
        if (!slot && !*tasks[i].id)
            slot = &tasks[i];
    }
    if (slot) {
        *slot = *task;
        slot->title[sizeof(slot->title) - 1] = 0;
        slot->detail[sizeof(slot->detail) - 1] = 0;
        if (slot->permille > 1000)
            slot->permille = 1000;
    }
    pthread_mutex_unlock(&lock);
    return slot != NULL;
}

size_t hub_progress_snapshot(HubProgress *output, size_t capacity)
{
    size_t n = 0;
    pthread_mutex_lock(&lock);
    for (size_t i = 0; i < sizeof(tasks) / sizeof(*tasks) && n < capacity; ++i)
        if (*tasks[i].id)
            output[n++] = tasks[i];
    pthread_mutex_unlock(&lock);
    return n;
}

void hub_progress_remove(const char *id)
{
    pthread_mutex_lock(&lock);
    for (size_t i = 0; i < sizeof(tasks) / sizeof(*tasks); ++i)
        if (!strcmp(tasks[i].id, id))
            tasks[i] = (HubProgress){0};
    pthread_mutex_unlock(&lock);
}
