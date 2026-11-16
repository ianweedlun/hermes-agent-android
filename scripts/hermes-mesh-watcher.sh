#!/usr/bin/env bash
# hermes-mesh-watcher.sh - Autonomous mesh watcher between MacBook Air and Pixel 10 Pro Fold

set -u

REPO="ianweedlun/hermes-agent-android"
AI_MEMORY_URL="http://127.0.0.1:49374/mcp"
AUTH_TOKEN_FILE="$HOME/.hermes/ai-memory.json"
GH_TOKEN_FILE="$HOME/src/vibes/agent-gh-token"
SEEN_MESSAGES_FILE="$HOME/.hermes/seen_mesh_messages.txt"
LOCK_FILE="/tmp/hermes-mesh-agent.lock"
LOG_FILE="$HOME/.hermes/logs/mesh-worker.log"
POLL_INTERVAL=15

mkdir -p "$HOME/.hermes/logs"
touch "$SEEN_MESSAGES_FILE"

# Export GH_TOKEN if available
if [[ -z "${GH_TOKEN:-}" ]] && [[ -f "$GH_TOKEN_FILE" ]]; then
    export GH_TOKEN="$(cat "$GH_TOKEN_FILE")"
fi

# Extract auth token from ai-memory.json if present
AI_MEMORY_TOKEN=""
if [[ -f "$AUTH_TOKEN_FILE" ]]; then
    AI_MEMORY_TOKEN="$(jq -r '.auth_token // empty' "$AUTH_TOKEN_FILE" 2>/dev/null || true)"
fi
if [[ -z "$AI_MEMORY_TOKEN" ]]; then
    AI_MEMORY_TOKEN="fc62e8555e1a33b02cfdda408e212d18f4c2e3eafef1ff40ae5c4bedb3cd395e"
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Hermes Mesh Watcher started."
echo "Monitoring:"
echo " - GitHub Repo PRs ($REPO)"
echo " - GitHub CI runs ($REPO)"
echo " - ai-memory inbox ($AI_MEMORY_URL)"

notify() {
    local msg="$1"
    echo -e "\a[$(date '+%Y-%m-%d %H:%M:%S')] [ALERT] $msg"
    osascript -e "display notification \"$msg\" with title \"Hermes Mesh\"" 2>/dev/null || true
}

PREV_PRS=""
PREV_RUNS=""
PREV_MSG_COUNT=0
FIRST_RUN=1

while true; do
    # 1. Check PRs
    CURRENT_PRS="$(gh pr list --repo "$REPO" 2>/dev/null || true)"
    if [[ $FIRST_RUN -eq 0 && "$CURRENT_PRS" != "$PREV_PRS" ]]; then
        notify "GitHub PR state changed on $REPO"
    fi
    PREV_PRS="$CURRENT_PRS"

    # 2. Check CI Runs
    CURRENT_RUNS="$(gh run list --repo "$REPO" --limit 3 2>/dev/null || true)"
    if [[ $FIRST_RUN -eq 0 && "$CURRENT_RUNS" != "$PREV_RUNS" ]]; then
        notify "GitHub CI run delta detected on $REPO"
    fi
    PREV_RUNS="$CURRENT_RUNS"

    # 3. Check ai-memory inbox
    INBOX_RESP="$(curl -s -m 5 \
        -H "Accept: application/json, text/event-stream" \
        -H "Authorization: Bearer $AI_MEMORY_TOKEN" \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"memory_message_list","arguments":{"workspace":"default","project":"workspace"}}}' \
        "$AI_MEMORY_URL" 2>/dev/null || true)"

    MESSAGES_JSON="$(echo "$INBOX_RESP" | jq -r '.result.content[0].text | fromjson | .messages' 2>/dev/null || echo "[]")"
    
    if [[ "$MESSAGES_JSON" != "[]" && "$MESSAGES_JSON" != "null" ]]; then
        # Look for messages not sent by hermes (i.e. incoming from Pixel or others)
        INCOMING="$(echo "$MESSAGES_JSON" | jq -c '.[] | select(.from_agent != "hermes")' 2>/dev/null || true)"
        
        while IFS= read -r msg; do
            [[ -z "$msg" ]] && continue
            MSG_ID="$(echo "$msg" | jq -r '.id')"
            MSG_SUBJ="$(echo "$msg" | jq -r '.subject')"

            if ! grep -q "$MSG_ID" "$SEEN_MESSAGES_FILE" 2>/dev/null; then
                echo "$MSG_ID" >> "$SEEN_MESSAGES_FILE"
                notify "New message from Pixel: $MSG_SUBJ"

                # Check lock to prevent overlapping runs
                if [[ -f "$LOCK_FILE" ]] && kill -0 "$(cat "$LOCK_FILE" 2>/dev/null)" 2>/dev/null; then
                    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Agent busy, skipping auto-turn trigger for $MSG_ID (already processing)"
                else
                    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Spawning autonomous Hermes agent turn for message $MSG_ID..."
                    (
                        echo $$ > "$LOCK_FILE"
                        hermes chat -q "Autonomous Mesh Worker: An incoming message from Pixel 10 Pro Fold was detected in ai-memory inbox (id: $MSG_ID, subject: '$MSG_SUBJ'). Claim it using mcp__ai_memory__memory_message_pop, evaluate the task, execute the necessary changes/builds/tests in /Users/ian/Dev/hermes-agent-android, push branches/PRs as needed, and reply back to the Pixel via mcp__ai_memory__memory_message_send." >> "$LOG_FILE" 2>&1
                        rm -f "$LOCK_FILE"
                    ) &
                fi
            fi
        done <<< "$INCOMING"
    fi

    FIRST_RUN=0
    sleep "$POLL_INTERVAL"
done
