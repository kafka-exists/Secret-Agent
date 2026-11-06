#!/usr/bin/env bash
# Push KeyForge Chat to your GitHub repository.
#
# Run this on YOUR machine (not in any shared environment) so your token never
# leaves it. The token is read from an environment variable, never typed into
# the repo or committed.
#
# Usage:
#   export GH_TOKEN="ghp_your_personal_access_token_here"
#   export GH_OWNER="your-github-username"
#   export GH_REPO="keyforge-chat"        # will be created if it doesn't exist
#   bash scripts/push_to_github.sh
#
# Requirements: git, gh (GitHub CLI) optional for repo creation, or curl.

set -euo pipefail

: "${GH_TOKEN:?Set GH_TOKEN to a GitHub personal access token (repo scope)}"
: "${GH_OWNER:?Set GH_OWNER to your GitHub username}"
: "${GH_REPO:=keyforge-chat}"

cd "$(git rev-parse --show-toplevel)"

# 1. Create the repo if it doesn't exist (via the GitHub API, no third-party tools).
echo ">> Ensuring repository $GH_OWNER/$GH_REPO exists..."
status=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: token $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$GH_OWNER/$GH_REPO")

if [ "$status" = "404" ]; then
  echo ">> Creating repository $GH_OWNER/$GH_REPO (private)..."
  curl -fsSL -X POST \
    -H "Authorization: token $GH_TOKEN" \
    -H "Accept: application/vnd.github+json" \
    -d "{\"name\":\"$GH_REPO\",\"private\":true,\"description\":\"Native Android multi-key AI chat client\"}" \
    https://api.github.com/user/repos > /dev/null
elif [ "$status" = "200" ]; then
  echo ">> Repository already exists."
else
  echo "!! Unexpected API status $status checking repo. Aborting."
  exit 1
fi

# 2. Configure the remote using the token-embedded URL (token never written to disk).
echo ">> Setting remote 'origin'..."
REMOTE_URL="https://x-access-token:${GH_TOKEN}@github.com/${GH_OWNER}/${GH_REPO}.git"
# Remove any stale origin first.
git remote remove origin 2>/dev/null || true
git remote add origin "https://github.com/${GH_OWNER}/${GH_REPO}.git"

# 3. Push. We pass the credential via the URL only for this one push command,
#    then immediately reset origin to the clean URL so the token is not stored.
echo ">> Pushing main to origin..."
git push "${REMOTE_URL}" main:main

git remote set-url origin "https://github.com/${GH_OWNER}/${GH_REPO}.git"
echo ">> Done. Remote reset to clean URL (token not stored)."
echo ">> Verify: https://github.com/${GH_OWNER}/${GH_REPO}"
echo
echo ">> Next: add the signing secrets (see README.md) and push a tag:"
echo "      git tag v1.0.0 && git push origin v1.0.0"
