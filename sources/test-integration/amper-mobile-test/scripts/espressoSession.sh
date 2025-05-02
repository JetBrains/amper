#!/bin/bash

set -e -u -o pipefail

WD_HUB_URL=${WD_HUB_URL:-"http://circlet:circlet@10.21.0.104:4444/wd/hub"}
SESSION_URL="$WD_HUB_URL/session"
SESSION_NAME="Android espresso tests"
ANDROID_VERSION=${ANDROID_VERSION:-"8.1"}
CAPABILITIES='{"desiredCapabilities": {"version": "__ANDROID_VERSION__", "platformName": "Android", "deviceName": "android", "name": "__SESSION_NAME__", "newCommandTimeout": 0, "sessionTimeout": "15m", "enableVNC": true, "portBindings": { "5037": "50001-50010" }}}'
SESSION_INFO_FILE=".device.session.json"

function get_session_id() {
    if [ -f "$SESSION_INFO_FILE" ]; then
        jq -r ".sessionId" "$SESSION_INFO_FILE"
    fi
}

function print_session_info() {
    SESSION_ID="$(get_session_id)"
    ADB_COMPANION_HOST_PORT=$(get_adb_host)

    [ -n "$SESSION_ID" ] && echo -e "DEVICE_SESSION_ID=$SESSION_ID\nADB_COMPANION=$ADB_COMPANION_HOST_PORT"
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

    CAPABILITIES=$(echo "$CAPABILITIES" | sed "s|__SESSION_NAME__|$SESSION_NAME|")
    CAPABILITIES=$(echo "$CAPABILITIES" | sed "s|__ANDROID_VERSION__|$ANDROID_VERSION|")
    echo "Creating session with capabilities: $CAPABILITIES"

    curl -fsSL --retry 30 --retry-max-time 300 -H "Content-type: application/json" -d "$CAPABILITIES" --create-dirs -o "$SESSION_INFO_FILE" "$SESSION_URL"
    print_session_info
}

function get_adb_host() {
    if [ -f "$SESSION_INFO_FILE" ]; then
        ADB_COMPANION=$(jq -r '.value.portBindings."5037"' "$SESSION_INFO_FILE")
        echo "$ADB_COMPANION"
    fi
}

function delete_session() {
    if [ -n "$(check_session)" ]; then
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
      port        Get adb host and mapped port
      delete      Delete active session

Options:
      -s path     Use specified file path to store/read information about device session

END
}


while getopts ":hs:n:v:" opt; do
    case ${opt} in
        s)
            SESSION_INFO_FILE=$OPTARG
            ;;
        n)
            SESSION_NAME=$OPTARG
            ;;
	    v)
            ANDROID_VERSION=$OPTARG
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
    port)
        get_adb_host
        ;;
    delete)
        delete_session
        ;;
    \?)
        echo "Unknown operation" 1>&2
        usage
        exit 1
esac

exit 0

