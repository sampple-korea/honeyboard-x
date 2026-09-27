#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p tools/out/classes
javac --release 17 -encoding UTF-8 -d tools/out/classes src/main/java/dev/sampple/rambler/s3/*.java tools/S3Harness.java tools/S3ResponseTest.java
java -cp tools/out/classes dev.sampple.rambler.s3.S3ResponseTest
language="${1:-en-US}"
case "$language" in
  en-US) phrase="Hello world. This is a test of speech recognition."; tl=en ;;
  ko-KR) phrase="안녕하세요. 삼성 키보드 음성 인식 테스트입니다. 오늘 날씨는 맑습니다."; tl=ko ;;
  *) echo "supply a supported test language: en-US or ko-KR" >&2; exit 2 ;;
esac
phrase="${S3_PHRASE:-$phrase}"
if [[ -n "${S3_AUDIO:-}" ]]; then
    input="$S3_AUDIO"
elif [[ "${S3_TTS:-google}" == google ]]; then
    curl --fail --silent --show-error --get 'https://translate.google.com/translate_tts' \
        --data-urlencode 'ie=UTF-8' --data-urlencode 'client=tw-ob' \
        --data-urlencode "tl=$tl" --data-urlencode "q=$phrase" -o tools/out/speech.mp3
    input=tools/out/speech.mp3
else
    ffmpeg -v error -y -f lavfi -i "flite=text='$phrase'" tools/out/speech.wav
    input=tools/out/speech.wav
fi
ffmpeg -v error -y -i "$input" -ar 16000 -ac 1 tools/out/speech16.wav
opusenc --quiet --speech --bitrate 24 --framesize 20 --discard-comments --padding 0 \
    tools/out/speech16.wav tools/out/speech.opus
java -Ds3.trigger="${S3_TRIGGER:-}" -Ds3.dump="${S3_DUMP:-false}" -Ds3.livePartials="${S3_LIVE_PARTIALS:-true}" \
    -Ds3.additional="${S3_ADDITIONAL:-}" -Ds3.historyFile="${S3_HISTORY_FILE:-}" \
    -cp tools/out/classes S3Harness "$language" tools/out/speech.opus
