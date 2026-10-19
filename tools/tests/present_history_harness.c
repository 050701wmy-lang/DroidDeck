#include "../../app/src/main/cpp/waylandcomp/src/present_history.h"
#include <assert.h>
#include <math.h>
#include <stdio.h>

int main(void) {
    struct present_history h = {.since_ns = 1000000000LL};
    float stats[2];
    for (int i = 1; i <= 120; i++) {
        int64_t time = 1000000000LL + (int64_t)i * 1000000000LL / 120;
        present_history_record(&h, time);
        present_history_record(&h, time);
    }
    present_history_snapshot(&h, 2000000000LL, stats);
    assert(fabsf(stats[0] - 120) < .01f);
    assert(fabsf(stats[1] - 8.333333f) < .01f);
    present_history_snapshot(&h, 4000000000LL, stats);
    assert(stats[0] == 0 && stats[1] == 0);

    h = (struct present_history){.since_ns = 5000000000LL};
    present_history_record(&h, 4000000000LL);
    present_history_record(&h, 5500000000LL);
    present_history_record(&h, 5200000000LL);
    present_history_snapshot(&h, 5500000000LL, stats);
    assert(stats[0] == 4 && stats[1] == 300);
    present_history_snapshot(&h, 5250000000LL, stats);
    assert(stats[0] == 4 && stats[1] == 0);

    h = (struct present_history){.since_ns = 1000000000LL};
    for (int i = 1; i <= 5000; i++)
        present_history_record(&h, 1000000000LL + (int64_t)i * 10000000LL);
    present_history_snapshot(&h, 51000000000LL, stats);
    assert(stats[0] == 100 && stats[1] == 10);
    puts("PASS: presentation history deduplicates, expires, resets and wraps");
    return 0;
}
