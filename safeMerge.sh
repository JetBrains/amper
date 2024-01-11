#!/bin/bash
#
# Pushes specified revision via [Space Safe Merge](https://helpserver.labs.jb.gg/help/space/internal/branch-and-merge-restrictions.html#safe-merge).
#
# Usage:
#   ./safeMerge.sh [<target_branch>] [--dry-run]
#   ./safeMerge.sh [<revision>[:<target_branch>]] [--dry-run]
#  <revision> is HEAD by default
#  <target_branch> is currently checked out branch by default (can be only master, stable or release branch)
#  '--dry-run' option will start Safe Merge in a dry run mode

declare dry_run
declare revision
declare target_branch
declare remote
declare remote_url
declare user_name
declare build_type
declare safepush_type

function isCommitHash() {
  if [[ "$1" =~ ^[0-9a-f]{40}$ ]]; then
    return 0
  else
    return 1
  fi
}

function parseArguments() {
  dry_run=false
  revision=HEAD
  build_type=''
  safepush_type=''
  if [[ "$#" -gt 2 ]]; then
    echo >&2 "Expected two arguments at most, see 'Usage' section of safeMerge.sh script"
    exit 1
  fi
  target_branch="$(git rev-parse --abbrev-ref HEAD)"
  for arg in "$@"; do
    if [[ -z "${arg// /}" ]]; then
      echo >&2 "Unexpected blank argument '$arg', see 'Usage' section of safeMerge.sh script"
      exit 1
    elif [[ "$arg" = '--dry-run' ]]; then
      dry_run=true
    elif [[ "$arg" == *:* ]]; then
      revision="${arg%:*}"
      target_branch="${arg#*:}"
    elif isCommitHash "$arg"; then
      revision="$arg"
    else
      case "$arg" in
        compile)
          safepush_type=compile
          build_type=SafePushWithCompilation
          ;;
        all|test|tests)
          safepush_type=test
          build_type=SafePushWithTests
          ;;
        fleet)
          safepush_type=fleet
          build_type=SafePushFleet
          ;;
        *)
          target_branch="$arg"
          ;;
        esac
    fi
  done
  git check-ref-format --branch "$target_branch"
  remote="$(git config --get "branch.${target_branch}.remote" || true)"
  remote="${remote:-$(git config --get remote.pushDefault || true)}"
  remote="${remote:-origin}"
  remote_url="$(git remote get-url --push "$remote")"
  user_name="$(git config user.name || echo "${GIT_COMMITTER_NAME:-}")"
  user_name="$(userName "$user_name")"
  checkBuildType
}

# due to environment or repository configuration what should not affect parseArguments test
function checkEnvironment() {
  if [[ -z "$user_name" ]]; then
    echo >&2 "Missing user.name, please execute in terminal:"
    echo >&2 "> git config user.name <your_name>"
    echo >&2 "or set GIT_COMMITTER_NAME=<your_name> environment variable"
    exit 1
  fi
  if ! ssh -V; then
    echo >&2 "ssh is required to use space merge"
    exit 1
  fi
  if ! isSshRemote "$remote_url"; then
    echo >&2 "$remote_url: ssh:// is required to use space merge"
    exit 1
  fi
  checkBuildType
}

function checkBuildType() {
  if [[ -n "$safepush_type" ]]; then
    echo >&2 "'$safepush_type' cannot be used together with SPACE=true"
    exit 1
  fi
  if [[ -n "$build_type" ]]; then
    echo >&2 "'$build_type' cannot be used together with SPACE=true"
    exit 1
  fi
}

function countLocalRevisions() {
  count="$(git rev-list --count "$remote/$target_branch..$revision")"
  echo "$count"
}

function isSshRemote() {
  if [[ "$1" == 'ssh://'* ]]; then
    return 0
  else
    return 1
  fi
}

function userName() {
  echo "$1" | tr ' ' - | tr '.' - | tr '[:upper:]' '[:lower:]'
}

function spaceRepository() {
  local space_repository="${1#"ssh://"}"    # everything after ssh:// prefix
  space_repository="${space_repository#*/}" # everything after the first slash
  echo "$space_repository"
}

function safeMergeCenterUrl() {
  local project_key
  local repository="${1#*/}"    # everything after the first slash
  repository="${repository%.*}" # everything before the first dot
  if [[ "$1" == */* ]]; then
    project_key="${1%/*}"       # everything before the first slash
  else
    project_key=ij
  fi
  echo "https://jetbrains.team/p/$project_key/repositories/$repository/safe-merges"
}

# IDs of YouTrack issues, Exception Analyzer issues and Space reviews are parsed and joined as a push ID.
# If none are found, then random push ID is generated. This behaviour is intentionally different from IDE plugin version due to complexity of implementation in Shell.
function pushID() {
  local -r delimiter='+'
  local push_id
  push_id=$(echo "$@" |
    # IDs of YouTrack issues, Exception Analyzer issues and Space reviews are matched
    { grep --only-matching '[a-zA-Z-]\+-[0-9]\+' || true; } |
    tr '[:lower:]' '[:upper:]' | sort --unique |
    # everything joined with delimiter, trailing delimiter removed
    tr '\n' "$delimiter" | sed "s/$delimiter$/\n/")
  if [[ -z "${push_id// /}" ]]; then
    # '-sh' suffix is added to distinguish from IDE pushes for statistic
    push_id="$RANDOM-sh"
  fi
  echo "$push_id"
}

function spaceSshCommand() {
  local -r space_repository="$(spaceRepository "$remote_url")"
  # shellcheck disable=SC2029
  (set -x; ssh git.jetbrains.team space "$space_repository" "$@")
}

function safeMerge() {
  local -r commit_messages="$(git log --format=%B "$remote/$target_branch..$revision")"
  local -r push_id="$(pushID $commit_messages)"
  local -r branch="refs/safemerge/$user_name/$push_id/$target_branch"
  push "$branch"
  if ! spaceSshCommand mr-find "$branch"; then
    spaceSshCommand mr-create "$branch" "$target_branch"
  fi
  # will fail if safe merge isn't running yet
  spaceSshCommand mr-cancel "$branch" || true
  if [[ "$dry_run" = true ]]; then
    # may timeout, see CRL-T-25610
    spaceSshCommand mr-dry-run "$branch" || true
  else
    # may timeout, see CRL-T-25610
    spaceSshCommand mr-rebase "$branch" --delete-source-branch || true
  fi
  local -r space_repository="$(spaceRepository "$remote_url")"
  safeMergeCenterUrl="$(safeMergeCenterUrl "$space_repository")"
  echo "Track all your Safe-Pushes here $safeMergeCenterUrl"
}

function push() {
  local -r branch="$1"
  git check-ref-format --branch "$branch"
  git push --quiet "$remote" "+$revision:$branch"
}

function pull() {
  # unstashing cannot restore Changelists, please upvote https://youtrack.jetbrains.com/issue/IDEA-62426
  git status -uno --porcelain | grep "^\(A.\| M\|D.\| D\)\s" -q && {
    git stash
    trap 'git stash pop' EXIT
  }
  local -r ref_spec="refs/heads/$target_branch:refs/remotes/$remote/$target_branch"
  git pull --rebase "$remote" "$ref_spec" || echo >&2 "Failed to perform git pull"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  set -euo pipefail
  cd "$(dirname "$0")"
  if ! git --version; then
    echo >&2 "git is missing, push cannot be performed"
    exit 1
  fi
  parseArguments "$@"
  if [[ "$(countLocalRevisions)" = 0 ]]; then
    echo "Nothing to push"
    exit 0
  fi
  checkEnvironment
  safeMerge
fi
