#!/bin/bash
set -e -u -o pipefail

#
# Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
#

WD_HUB_URL=${WD_HUB_URL:-"http://circlet:circlet@10.21.0.104:4444/wd/hub"}
DEVICE_NAME=${DEVICE_NAME:-"iPhone 12"}
SESSION_URL="$WD_HUB_URL/session"
SESSION_NAME=${SESSION_NAME:-"iOS tests"}
SESSION_TIMEOUT=${SESSION_TIMEOUT:-"35m"}
SESSION_INFO_FILE=".device.session.json"

function get_session_id() {
    if [ -f "$SESSION_INFO_FILE" ]; then
        jq -r ".sessionId" "$SESSION_INFO_FILE"
    fi
}

function get_idb_companion_host_port() {
    if [ -f "$SESSION_INFO_FILE" ]; then
        IDB_COMPANION_HOST=$(jq -r '.host' "$SESSION_INFO_FILE")
        IDB_COMPANION_PORT=$(jq -r '.port' "$SESSION_INFO_FILE")
        echo "$IDB_COMPANION_HOST:$IDB_COMPANION_PORT"
    fi
}

function print_session_info() {
    SESSION_ID="$(get_session_id)"
    IDB_COMPANION="$(get_idb_companion_host_port)"

    [ -n "$SESSION_ID" ] && echo -e "DEVICE_SESSION_ID=$SESSION_ID\nIDB_COMPANION=$IDB_COMPANION"
}

function check_session() {
    SESSION_ID=$(get_session_id)
    if [ -n "$SESSION_ID" ]; then
        if curl -fsL -o /dev/null "$SESSION_URL/$SESSION_ID"; then
            print_session_info
        else
            echo "Session not found" 1>&2
        fi
    fi
}

function create_session() {
    if [ -n "$(check_session 2>/dev/null)" ]; then
        print_session_info
        return 1
    fi

    [ -f "$SESSION_INFO_FILE" ] && rm "$SESSION_INFO_FILE"

    SESSION_PAYLOAD="{\"deviceName\":\"$DEVICE_NAME\",\"desiredCapabilities\":{\"browserName\":\"$DEVICE_NAME\",\"name\":\"$SESSION_NAME\",\"newCommandTimeout\":0,\"sessionTimeout\":\"$SESSION_TIMEOUT\"}}"
    echo "Creating session with payload: $(echo "$SESSION_PAYLOAD" | jq)"

    curl -fsSL --retry 30 --retry-max-time 300 -H "Content-type: application/json" -d "$SESSION_PAYLOAD" --create-dirs -o "$SESSION_INFO_FILE" "$SESSION_URL"
    print_session_info
}

function delete_session() {
    FORCE="$1"
    if [ -n "$(check_session)" -o -n "$FORCE" ]; then
        SESSION_ID=$(get_session_id)

        echo "Deleting session: $SESSION_ID"
        curl -fsSL -X DELETE -o /dev/null "$SESSION_URL/$SESSION_ID"
        echo "Session deleted: $SESSION_ID"
        rm "$SESSION_INFO_FILE"
    fi
}

function usage() {
cat <<END

$(basename $0) [-s <session_info_file>] <action>

Available actions:
      create      Create new device session
      check       Check if current session is still alive
      delete      Delete active session
      purge       Delete active session (force mode: try deleting even if check session returns nothing)

Options:
      -s path     Use specified file path to store/read information about device session

END
}


while getopts ":hs:n:" opt; do
    case ${opt} in
        s)
            SESSION_INFO_FILE=$OPTARG
            ;;
        n)
            SESSION_NAME=$OPTARG
            ;;
        h)
            usage
            exit 0
            ;;
        \?)
            usage
            exit 1
            ;;
        :)
            echo "Invalid option: '$OPTARG' requires an argument" 1>&2
            exit 1
            ;;
    esac
done
shift $((OPTIND - 1))


case "$1" in
    create)
        create_session
        ;;
    check)
        check_session
        ;;
    check1)
            check_session1
            ;;
    delete)
        delete_session
        ;;
    purge)
        delete_session "force"
        ;;
    \?)
        echo "Unknown operation" 1>&2
        usage
        exit 1
esac

exit 0

