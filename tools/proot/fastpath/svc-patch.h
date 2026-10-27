static inline int svc_number(uint32_t insn) {
  if ((insn & 0xffe0001fU) == 0xd2800008U ||
      (insn & 0xffe0001fU) == 0x52800008U)
    return (insn >> 5) & 0xffff;
  return -1;
}

static inline int svc_argument_instruction(uint32_t insn) {
  if (insn == 0xd503201fU) return 1;
  if ((insn & 0xffc003e0U) == 0x290003e0U ||
      (insn & 0xffc003e0U) == 0xa90003e0U) return 1;
  if ((insn & 31) == 8) return 0;
  return (insn & 0xffe0ffe0U) == 0xaa0003e0U ||
         (insn & 0xffe0ffe0U) == 0x2a0003e0U ||
         (insn & 0xfffffc00U) == 0x93407c00U ||
         (insn & 0x7f800000U) == 0x52800000U ||
         (insn & 0x7f800000U) == 0x12800000U ||
         (insn & 0xffc003e0U) == 0xb94003e0U ||
         (insn & 0xffc003e0U) == 0xf94003e0U ||
         (insn & 0xffc003e0U) == 0xb98003e0U;
}

static inline int svc_site_number(const uint32_t *text, size_t at) {
  for (size_t back = 1; back <= 8 && back <= at; back++) {
    uint32_t insn = text[at - back];
    int nr = svc_number(insn);
    if (nr >= 0) return nr;
    if (!svc_argument_instruction(insn)) break;
  }
  return -1;
}

static inline int svc_branch(uintptr_t from, uintptr_t to, uint32_t *out) {
  int64_t delta = (int64_t)to - (int64_t)from;
  if ((delta & 3) || delta < -0x8000000LL || delta >= 0x8000000LL) return 0;
  *out = 0x14000000U | ((uint32_t)(delta / 4) & 0x03ffffffU);
  return 1;
}

#if defined(__aarch64__) && !defined(FP_SVC_ENCODINGS_ONLY)
#include <asm/hwcap.h>
#include <link.h>

static __thread int svc_busy __attribute__((tls_model("initial-exec")));
static unsigned svc_sites, svc_pages;
static unsigned long svc_fast, svc_slow;

static long svc_original(const long *r) {
  register long x0 __asm__("x0") = r[0];
  register long x1 __asm__("x1") = r[1];
  register long x2 __asm__("x2") = r[2];
  register long x3 __asm__("x3") = r[3];
  register long x4 __asm__("x4") = r[4];
  register long x5 __asm__("x5") = r[5];
  register long x8 __asm__("x8") = r[8];
  __asm__ volatile("svc #0" : "+r"(x0) : "r"(x1), "r"(x2), "r"(x3), "r"(x4), "r"(x5), "r"(x8) : "memory", "cc");
  return x0;
}

__attribute__((used, visibility("hidden")))
long fp_svc_dispatch(const long *r) {
  int saved_errno = errno;
  long result = FP_SLOW;
  if (!svc_busy) {
    svc_busy = 1;
    if (r[8] == SYS_openat)
      result = fp_open((int)r[0], (const char *)r[1], (int)r[2], (mode_t)r[3]);
    else if (r[8] == SYS_newfstatat && r[2])
      result = fp_stat((int)r[0], (const char *)r[1], (int)r[3], (struct stat *)r[2]);
    if (result != FP_SLOW) __atomic_fetch_add(&svc_fast, 1, __ATOMIC_RELAXED);
    svc_busy = 0;
  }
  if (result == FP_SLOW) {
    __atomic_fetch_add(&svc_slow, 1, __ATOMIC_RELAXED);
    result = svc_original(r);
  }
  errno = saved_errno;
  return result;
}

void fp_svc_entry(void) __attribute__((visibility("hidden")));
/* A replaced svc may have live values in every register, including NZCV and SIMD. */
__asm__(
  ".text\n.p2align 4\n.hidden fp_svc_entry\n.type fp_svc_entry,%function\nfp_svc_entry:\n"
  "bti j\nsub sp, sp, #800\n"
  "stp x0, x1, [sp, #0]\nstp x2, x3, [sp, #16]\n"
  "stp x4, x5, [sp, #32]\nstp x6, x7, [sp, #48]\n"
  "stp x8, x9, [sp, #64]\nstp x10, x11, [sp, #80]\n"
  "stp x12, x13, [sp, #96]\nstp x14, x15, [sp, #112]\n"
  "stp x18, x19, [sp, #128]\nstp x20, x21, [sp, #144]\n"
  "stp x22, x23, [sp, #160]\nstp x24, x25, [sp, #176]\n"
  "stp x26, x27, [sp, #192]\nstp x28, x29, [sp, #208]\n"
  "stp x30, x17, [sp, #224]\nmrs x0, nzcv\nstr x0, [sp, #240]\n"
  "mrs x0, fpcr\nstr x0, [sp, #768]\nmrs x0, fpsr\nstr x0, [sp, #776]\n"
  "stp q0, q1, [sp, #256]\nstp q2, q3, [sp, #288]\n"
  "stp q4, q5, [sp, #320]\nstp q6, q7, [sp, #352]\n"
  "stp q8, q9, [sp, #384]\nstp q10, q11, [sp, #416]\n"
  "stp q12, q13, [sp, #448]\nstp q14, q15, [sp, #480]\n"
  "stp q16, q17, [sp, #512]\nstp q18, q19, [sp, #544]\n"
  "stp q20, q21, [sp, #576]\nstp q22, q23, [sp, #608]\n"
  "stp q24, q25, [sp, #640]\nstp q26, q27, [sp, #672]\n"
  "stp q28, q29, [sp, #704]\nstp q30, q31, [sp, #736]\n"
  "mov x0, sp\nbl fp_svc_dispatch\nstr x0, [sp]\n"
  "ldp q0, q1, [sp, #256]\nldp q2, q3, [sp, #288]\n"
  "ldp q4, q5, [sp, #320]\nldp q6, q7, [sp, #352]\n"
  "ldp q8, q9, [sp, #384]\nldp q10, q11, [sp, #416]\n"
  "ldp q12, q13, [sp, #448]\nldp q14, q15, [sp, #480]\n"
  "ldp q16, q17, [sp, #512]\nldp q18, q19, [sp, #544]\n"
  "ldp q20, q21, [sp, #576]\nldp q22, q23, [sp, #608]\n"
  "ldp q24, q25, [sp, #640]\nldp q26, q27, [sp, #672]\n"
  "ldp q28, q29, [sp, #704]\nldp q30, q31, [sp, #736]\n"
  "ldr x0, [sp, #768]\nmsr fpcr, x0\nldr x0, [sp, #776]\nmsr fpsr, x0\n"
  "ldr x0, [sp, #240]\nmsr nzcv, x0\n"
  "ldp x0, x1, [sp, #0]\nldp x2, x3, [sp, #16]\n"
  "ldp x4, x5, [sp, #32]\nldp x6, x7, [sp, #48]\n"
  "ldp x8, x9, [sp, #64]\nldp x10, x11, [sp, #80]\n"
  "ldp x12, x13, [sp, #96]\nldp x14, x15, [sp, #112]\n"
  "ldp x18, x19, [sp, #128]\nldp x20, x21, [sp, #144]\n"
  "ldp x22, x23, [sp, #160]\nldp x24, x25, [sp, #176]\n"
  "ldp x26, x27, [sp, #192]\nldp x28, x29, [sp, #208]\n"
  "ldr x30, [sp, #224]\nldr x16, [sp, #232]\n"
  "add sp, sp, #800\nbr x16\n.size fp_svc_entry, .-fp_svc_entry\n"
);

static int svc_emit(uint32_t *code, uint32_t *site, uint32_t *branch) {
  uint32_t back;
  if (!svc_branch((uintptr_t)site, (uintptr_t)code, branch) ||
      !svc_branch((uintptr_t)(code + 5), (uintptr_t)(site + 1), &back)) return 0;
  code[0] = 0xa9bf47f0U; /* stp x16, x17, [sp, #-16]! */
  code[1] = 0x580000b0U; /* ldr x16, literal at +24 */
  code[2] = 0x10000051U; /* adr x17, +8 */
  code[3] = 0xd61f0200U; /* br x16 */
  code[4] = 0xa8c147f0U; /* ldp x16, x17, [sp], #16 */
  code[5] = back;
  uintptr_t entry = (uintptr_t)fp_svc_entry;
  memcpy(code + 6, &entry, sizeof entry);
  return 1;
}

struct svc_site { uint32_t *site; uint32_t branch; };
static struct svc_site svc_plan[256];
static size_t svc_planned;

static int svc_object(struct dl_phdr_info *info, size_t size, void *unused) {
  (void)size;
  (void)unused;
  const char *name = strrchr(info->dlpi_name, '/');
  name = name ? name + 1 : info->dlpi_name;
  if (strcmp(name, "libc.so.6") && strcmp(name, "ld-linux-aarch64.so.1")) return 0;
  for (int i = 0; i < info->dlpi_phnum; i++) {
    const ElfW(Phdr) *ph = &info->dlpi_phdr[i];
    if (ph->p_type != PT_LOAD || !(ph->p_flags & PF_X)) continue;
    uint32_t *text = (uint32_t *)(info->dlpi_addr + ph->p_vaddr);
    for (size_t j = 1; j < ph->p_memsz / 4 && svc_planned < 256; j++) {
      if (text[j] != 0xd4000001U) continue;
      int nr = svc_site_number(text, j);
      if (nr != SYS_openat && nr != SYS_newfstatat) continue;
      svc_plan[svc_planned++].site = &text[j];
    }
  }
  return 0;
}

static uint32_t *svc_relay_page(uintptr_t site, size_t page) {
  uintptr_t base = site & ~(uintptr_t)(page - 1);
  for (uintptr_t delta = page; delta < 0x7ff0000UL; delta += 0x10000UL) {
    for (int direction = -1; direction <= 1; direction += 2) {
      uintptr_t at = direction < 0 ? base - delta : base + delta;
      if (at < page) continue;
      void *p = mmap((void *)at, page, PROT_READ | PROT_WRITE,
                     MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED_NOREPLACE, -1, 0);
      if (p == (void *)at) return p;
      if (p != MAP_FAILED) munmap(p, page);
    }
  }
  return NULL;
}

static int svc_single_thread(void) {
  struct entry { uint64_t ino; int64_t off; unsigned short len; unsigned char type; char name[]; };
  char buf[4096] __attribute__((aligned(8)));
  long fd = sc(SYS_openat, AT_FDCWD, (long)"/proc/self/task", O_RDONLY | O_DIRECTORY | O_CLOEXEC, 0, 0);
  if (fd < 0) return 0;
  int count = 0;
  long n;
  while ((n = sc(SYS_getdents64, fd, (long)buf, sizeof buf, 0, 0)) > 0) {
    for (long at = 0; at < n;) {
      const struct entry *e = (const struct entry *)(buf + at);
      if (n - at < (long)offsetof(struct entry, name) + 1 ||
          e->len < offsetof(struct entry, name) + 1 || e->len > n - at) { count = -1; goto done; }
      if (e->name[0] >= '0' && e->name[0] <= '9' && ++count > 1) goto done;
      at += e->len;
    }
  }
  if (n < 0) count = -1;
done:
  sc(SYS_close, fd, 0, 0, 0, 0);
  return count == 1;
}

static void svc_stats(void) {
  char line[160];
  int n = snprintf(line, sizeof line, "fastpath-svc[%d]: %u sites, %u relay pages, %lu fast, %lu to proot\n",
                   getpid(), svc_sites, svc_pages, svc_fast, svc_slow);
  if (write(2, line, n) < 0) {}
}

__attribute__((constructor)) static void svc_patch_init(void) {
  const char *enabled = getenv("PROOT_FP_PATCH_SVC");
  if ((enabled && strcmp(enabled, "1")) || !fp_ready()) return;
  if (getauxval(AT_HWCAP) & HWCAP_SVE) return;
#ifdef HWCAP2_SME
  if (getauxval(AT_HWCAP2) & HWCAP2_SME) return;
#endif
  if (!svc_single_thread()) return;
  size_t page = (size_t)sysconf(_SC_PAGESIZE);
  if (page < 4096 || (page & (page - 1))) return;
  int mem = (int)sc(SYS_openat, AT_FDCWD, (long)"/proc/self/mem", O_RDWR | O_CLOEXEC, 0, 0);
  if (mem < 0) return;
  dl_iterate_phdr(svc_object, NULL);
  for (size_t first = 0; first < svc_planned;) {
    uint32_t *relay = svc_relay_page((uintptr_t)svc_plan[first].site, page);
    if (!relay) { first++; continue; }
    size_t end = first;
    for (; end < svc_planned && (end - first + 1) * 32 <= page; end++) {
      uint32_t *code = relay + (end - first) * 8;
      if (!svc_emit(code, svc_plan[end].site, &svc_plan[end].branch)) break;
    }
    __builtin___clear_cache((char *)relay, (char *)relay + page);
    if (end == first || mprotect(relay, page, PROT_READ | PROT_EXEC)) {
      munmap(relay, page);
      first++;
      continue;
    }
    unsigned patched = 0;
    for (size_t i = first; i < end; i++) {
      struct svc_site *s = &svc_plan[i];
      long written = sc(SYS_pwrite64, mem, (long)&s->branch, sizeof s->branch, (long)s->site, 0);
      if (written != sizeof s->branch) continue;
      __builtin___clear_cache((char *)s->site, (char *)(s->site + 1));
      patched++;
    }
    if (patched) { svc_sites += patched; svc_pages++; }
    else munmap(relay, page);
    first = end;
  }
  sc(SYS_close, mem, 0, 0, 0, 0);
  if (getenv("PROOT_FP_STATS")) atexit(svc_stats);
}
#endif
