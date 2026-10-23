/* Parent process for the helper harnesses: it starts the helper so that the helper's parent is a
 * process signed with the application identifier, and forwards termination to it. */
#include <errno.h>
#include <signal.h>
#include <spawn.h>
#include <sys/wait.h>
#include <unistd.h>

extern char **environ;
static pid_t child;

static void forward(int number) {
  if (child > 0) {
    kill(child, number);
  }
}

int main(int argc, char **argv) {
  if (argc != 2) {
    return 64;
  }
  signal(SIGTERM, forward);
  signal(SIGINT, forward);
  char *arguments[] = {argv[1], NULL};
  if (posix_spawn(&child, argv[1], NULL, NULL, arguments, environ) != 0) {
    return 65;
  }
  int status = 0;
  while (waitpid(child, &status, 0) < 0) {
    if (errno != EINTR) {
      return 66;
    }
  }
  if (WIFEXITED(status)) {
    return WEXITSTATUS(status);
  }
  return 128 + WTERMSIG(status);
}
