#!/usr/bin/env bash
# Push the KeyForge Chat project to GitHub using a classic PAT.
# Token is read from GH_TOKEN env var ONLY — never written to disk, never
# embedded in a URL, never stored in git config. Uses http.extraheader so
# the credential lives only in this single git process.
#
# Usage:
#   GH_TOKEN='ghp_...' bash scripts/push_now.sh

set -euo pipefail

: "${GH_TOKEN:?Set GH_TOKEN to your GitHub personal access token}"

# Redact the token from any echoed output / errors.
redact() { sed "s|${GH_TOKEN}|***REDACTED***|g"; }

echo ">> Authenticating with GitHub..."
RESP=$(curl -sS -H "Authorization: token $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/user 2>&1 | redact)

USER=$(printf '%s' "$RESP" | python3 -c "import sys,json
try:
    print(json.load(sys.stdin).get('login',''))
except Exception:
    print('')" 2>/dev/null || echo "")

if [ -z "$USER" ]; then
  echo "!! Could not authenticate. Token may be invalid or expired."
  printf '%s\n' "$RESP" | head -20
  exit 1
fi
echo ">> Authenticated as: $USER"

# GitHub repo names cannot contain spaces — "Secret Agent" -> "Secret-Agent".
REPO="Secret-Agent"
echo ">> Target repo: $USER/$REPO"

echo ">> Checking if repo exists..."
status=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: token $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$USER/$REPO")

if [ "$status" = "404" ]; then
  echo ">> Repo does not exist — creating private repo $USER/$REPO..."
  curl -sS -X POST \
    -H "Authorization: token $GH_TOKEN" \
    -H "Accept: application/vnd.github+json" \
    -d "{\"name\":\"$REPO\",\"private\":true,\"description\":\"Native Android multi-key AI chat client with provider failover and on-device file generation\"}" \
    https://api.github.com/user/repos 2>&1 | redact | head -5
elif [ "$status" = "200" ]; then
  echo ">> Repo already exists."
else
  echo "!! Unexpected API status $status when checking repo. Aborting."
  exit 1
fi

cd /home/z/my-project

echo ">> Current branch: $(git branch --show-current)"
echo ">> Commits to push:"
git log --oneline | head -5

echo ">> Pushing main -> main (token via credential helper, not stored)..."
# GitHub's git HTTPS endpoint wants Basic auth with the token as the password.
# We feed it through a one-shot credential helper that reads $GH_TOKEN from the
# environment — the token is never written to a URL, never saved to git config,
# and never persisted to a credential file.
AUTH_B64=$(printf 'x-access-token:%s' "$GH_TOKEN" | base64 -w0)
if git -c http.extraheader="Authorization: Basic $AUTH_B64" \
       push "https://github.com/$USER/$REPO.git" main:main 2>&1 | redact; then
  echo
  echo "==============================="
  echo ">> PUSH SUCCEEDED."
  echo ">> Repo: https://github.com/$USER/$REPO"
  echo ">> *** REVOKE YOUR TOKEN NOW:  ***"
  echo ">> https://github.com/settings/tokens"
  echo "==============================="
else
  echo
  echo "!! Push failed. If the repo is non-empty (e.g. has a README),"
  echo "!! the push was rejected as non-fast-forward."
  exit 1
fi
