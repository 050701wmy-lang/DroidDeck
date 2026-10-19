#ifndef PRESENT_HISTORY_H
#define PRESENT_HISTORY_H
#include <stdint.h>
#include <stdlib.h>

#define PRESENT_HISTORY_CAP 1024
struct present_history {
    int64_t times[PRESENT_HISTORY_CAP];
    int64_t since_ns;
    unsigned count, next;
};

static inline int present_time_compare(const void *a, const void *b) {
    int64_t x = *(const int64_t *)a, y = *(const int64_t *)b;
    return (x > y) - (x < y);
}

static inline void present_history_record(struct present_history *h, int64_t time_ns) {
    if (time_ns <= 0 || time_ns < h->since_ns) return;
    for (unsigned i = 0; i < h->count; i++)
        if (h->times[i] == time_ns) return;
    h->times[h->next] = time_ns;
    h->next = (h->next + 1) % PRESENT_HISTORY_CAP;
    if (h->count < PRESENT_HISTORY_CAP) h->count++;
}

static inline void present_history_snapshot(const struct present_history *h, int64_t now_ns, float out[2]) {
    int64_t times[PRESENT_HISTORY_CAP], intervals[PRESENT_HISTORY_CAP];
    int64_t start = now_ns - 1000000000LL;
    if (start < h->since_ns) start = h->since_ns;
    unsigned n = 0;
    for (unsigned i = 0; i < h->count; i++)
        if (h->times[i] > start && h->times[i] <= now_ns) times[n++] = h->times[i];
    out[0] = now_ns > start ? (float)((double)n * 1e9 / (double)(now_ns - start)) : 0;
    out[1] = 0;
    if (n < 2) return;
    qsort(times, n, sizeof(*times), present_time_compare);
    for (unsigned i = 1; i < n; i++) intervals[i - 1] = times[i] - times[i - 1];
    qsort(intervals, n - 1, sizeof(*intervals), present_time_compare);
    unsigned rank = ((n - 1) * 95 + 99) / 100;
    out[1] = (float)((double)intervals[rank - 1] / 1e6);
}
#endif
