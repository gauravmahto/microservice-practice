#!/usr/bin/env bash
# Compress tracked source code (excluding files ignored by .gitignore) into a tar.gz archive.
# By default uses git archive so only tracked (non-ignored) files are included.
# Optionally include currently untracked (but not ignored) files with --include-untracked.
#
# Usage:
#   scripts/compress-source.sh [--output <file>] [--ref <git-ref>] [--include-untracked]
#
# Examples:
#   scripts/compress-source.sh                                 # build/distributions/practice-source-<shortsha>.tar.gz
#   scripts/compress-source.sh --ref main                      # archive of main branch tip
#   scripts/compress-source.sh --include-untracked             # also bundle new, unstaged files
#   scripts/compress-source.sh --output /tmp/src.tar.gz
#
# Exit codes:
#   0 success
#   1 generic failure (e.g., not a git repo)
set -euo pipefail

PROJECT_NAME="practice"
REF=""
OUT=""
INCLUDE_UNTRACKED=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output|-o)
      OUT="$2"; shift 2;;
    --ref|-r)
      REF="$2"; shift 2;;
    --include-untracked)
      INCLUDE_UNTRACKED=true; shift;;
    --help|-h)
      grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0;;
    *)
      echo "Unknown argument: $1" >&2; exit 1;;
  esac
done

if ! command -v git >/dev/null 2>&1; then
  echo "git not found in PATH" >&2
  exit 1
fi

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Not inside a git repository" >&2
  exit 1
fi

if [[ -z "$REF" ]]; then
  # Prefer tag description; fallback to short SHA
  if REF_FULL=$(git describe --tags --dirty --always 2>/dev/null); then
    REF="${REF_FULL}"
  else
    REF=$(git rev-parse --short HEAD)
  fi
fi

SHORT_SHA=$(git rev-parse --short "$REF" 2>/dev/null || echo "$REF")

if [[ -z "$OUT" ]]; then
  mkdir -p build/distributions
  OUT="build/distributions/${PROJECT_NAME}-source-${SHORT_SHA}.tar.gz"
fi

echo "Creating source archive: $OUT (ref=$REF, include_untracked=$INCLUDE_UNTRACKED)"

if [[ "$INCLUDE_UNTRACKED" == false ]]; then
  # Pure git archive (only tracked content) automatically excludes ignored files.
  git archive --format=tar.gz -o "$OUT" "$REF"
else
  TMPDIR=$(mktemp -d)
  cleanup() { rm -rf "$TMPDIR"; }
  trap cleanup EXIT
  # Extract tracked files into temp
  git archive "$REF" | tar -x -C "$TMPDIR"
  # Add untracked (but not ignored) files
  while IFS= read -r f; do
    # Skip empty line
    [[ -z "$f" ]] && continue
    mkdir -p "$TMPDIR/$(dirname "$f")"
    cp -p "$f" "$TMPDIR/$f"
  done < <(git ls-files -o --exclude-standard)
  # Create archive from temp directory contents
  tar -C "$TMPDIR" -czf "$OUT" .
fi

echo "Archive size: $(du -h "$OUT" | cut -f1)"
echo "Done."
