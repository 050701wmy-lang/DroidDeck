#include "../fastpath/fastpath.c"
#include <assert.h>
#include <sys/wait.h>

#if defined(__aarch64__)
struct snapshot { long x[32]; unsigned char q[32][16]; unsigned long nzcv, fpcr, fpsr; };
extern uint32_t svc_check_site;
extern void svc_register_probe(struct snapshot *, const long *);

__asm__(
  ".text\n.p2align 4\n.type svc_register_probe,%function\nsvc_register_probe:\n"
  "sub sp, sp, #1072\n"
  "stp x19, x20, [sp]\nstp x21, x22, [sp, #16]\nstp x23, x24, [sp, #32]\n"
  "stp x25, x26, [sp, #48]\nstp x27, x28, [sp, #64]\nstp x29, x30, [sp, #80]\n"
  "stp q8, q9, [sp, #96]\nstp q10, q11, [sp, #128]\n"
  "stp q12, q13, [sp, #160]\nstp q14, q15, [sp, #192]\n"
  "stp x0, x1, [sp, #224]\n"
  ".irp n,1,2,3,4,5,6,7,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30\n"
  "mov x\\n, #\\n\n.endr\n"
  ".irp n,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31\n"
  "movi v\\n\\().16b, #0x5a\n.endr\n"
  "mov x0, #0xa0000000\nmsr nzcv, x0\n"
  "ldr x0, [sp, #232]\nldr x8, [x0, #48]\n"
  "ldp x4, x5, [x0, #32]\nldp x2, x3, [x0, #16]\nldp x0, x1, [x0]\n"
  ".global svc_check_site\nsvc_check_site:\nsvc #0\n"
  ".irp n,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30\n"
  "str x\\n, [sp, #(256 + \\n * 8)]\n.endr\n"
  ".irp n,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31\n"
  "str q\\n, [sp, #(512 + \\n * 16)]\n.endr\n"
  "mrs x0, nzcv\nstr x0, [sp, #1024]\nmrs x0, fpcr\nstr x0, [sp, #1032]\n"
  "mrs x0, fpsr\nstr x0, [sp, #1040]\n"
  "ldr x0, [sp, #224]\nadd x1, sp, #256\nmov x2, #792\nbl memcpy\n"
  "ldp q8, q9, [sp, #96]\nldp q10, q11, [sp, #128]\n"
  "ldp q12, q13, [sp, #160]\nldp q14, q15, [sp, #192]\n"
  "ldp x19, x20, [sp]\nldp x21, x22, [sp, #16]\nldp x23, x24, [sp, #32]\n"
  "ldp x25, x26, [sp, #48]\nldp x27, x28, [sp, #64]\nldp x29, x30, [sp, #80]\n"
  "add sp, sp, #1072\nret\n.size svc_register_probe, .-svc_register_probe\n"
);

static void check(const long *args, long want) {
  struct snapshot out = {0};
  unsigned long fpcr, fpsr;
  __asm__ volatile("mrs %0, fpcr\nmrs %1, fpsr" : "=r"(fpcr), "=r"(fpsr));
  errno = 123;
  svc_register_probe(&out, args);
  assert(errno == 123);
  assert(out.x[0] == want);
  for (int i = 1; i <= 30; i++) {
    long expected = i <= 5 ? args[i] : i == 8 ? args[6] : i;
    if (out.x[i] != expected) { fprintf(stderr, "x%d: %lx != %lx\n", i, out.x[i], expected); abort(); }
  }
  for (int i = 0; i < 32; i++)
    for (int j = 0; j < 16; j++) assert(out.q[i][j] == 0x5a);
  assert(out.nzcv == 0xa0000000UL);
  assert(out.fpcr == fpcr && out.fpsr == fpsr);
}

static void *thread_check(void *unused) {
  (void)unused;
  struct stat st;
  const long args[] = {AT_FDCWD, (long)"/usr/lib/libc.so.6", (long)&st, 0, 0, 0, SYS_newfstatat};
  for (int i = 0; i < 100; i++) check(args, 0);
  return NULL;
}

static pthread_barrier_t barrier;
static void *thread_probe(void *unused) {
  pthread_barrier_wait(&barrier);
  thread_check(unused);
  pthread_barrier_wait(&barrier);
  return NULL;
}

int main(void) {
  assert(fp_ready());
  assert(svc_single_thread());
  size_t page = sysconf(_SC_PAGESIZE);
  uint32_t *relay = svc_relay_page((uintptr_t)&svc_check_site, page), branch;
  assert(relay && svc_emit(relay, &svc_check_site, &branch));
  __builtin___clear_cache((char *)relay, (char *)relay + 32);
  assert(mprotect(relay, page, PROT_READ | PROT_EXEC) == 0);
  int mem = open("/proc/self/mem", O_RDWR | O_CLOEXEC);
  assert(mem >= 0 && pwrite(mem, &branch, 4, (off_t)(uintptr_t)&svc_check_site) == 4);
  close(mem);
  __builtin___clear_cache((char *)&svc_check_site, (char *)(&svc_check_site + 1));
  long args[] = {0, 1, 2, 3, 4, 5, SYS_getpid};
  check(args, getpid());
  struct stat st;
  args[0] = AT_FDCWD; args[1] = (long)"/usr/lib/libc.so.6"; args[2] = (long)&st;
  args[3] = 0; args[6] = SYS_newfstatat;
  check(args, 0);
  assert(st.st_size > 0);
  args[1] = (long)"/usr/lib/proot-svc-check-does-not-exist";
  check(args, -ENOENT);
  args[1] = 0;
  check(args, -EFAULT);
  args[1] = (long)"/usr/lib/libc.so.6"; args[2] = 0;
  check(args, -EFAULT);
  args[1] = (long)"/proc/self/exe"; args[2] = (long)&st;
  check(args, 0);
  args[3] = 0x40000000;
  check(args, -EINVAL);
  pthread_t threads[4];
  assert(pthread_barrier_init(&barrier, NULL, 5) == 0);
  for (int i = 0; i < 4; i++) assert(pthread_create(&threads[i], NULL, thread_probe, NULL) == 0);
  pthread_barrier_wait(&barrier);
  assert(!svc_single_thread());
  pthread_barrier_wait(&barrier);
  for (int i = 0; i < 4; i++) assert(pthread_join(threads[i], NULL) == 0);
  assert(pthread_barrier_destroy(&barrier) == 0);
  pid_t pid = fork();
  assert(pid >= 0);
  if (!pid) { thread_check(NULL); _exit(0); }
  int status;
  assert(waitpid(pid, &status, 0) == pid && WIFEXITED(status) && WEXITSTATUS(status) == 0);
  puts("PASS: raw results, errno, x1-x30, q0-q31, NZCV, FPCR/FPSR, 4 threads, fork");
  return 0;
}
#else
int main(void) { return 77; }
#endif
