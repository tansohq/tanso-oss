#!/bin/bash
if [ -z "$1" ]; then
  echo "Usage: ./decode.sh [env]  # example: ./decode.sh staging"
  exit 1
fi

OUTPUT_FILE="application-${1}.yaml"
INPUT_FILE="${1}.b64"
TIMESTAMP=$(date +"%Y%m%d-%H%M")

if [ ! -f "$INPUT_FILE" ]; then
  echo "File $INPUT_FILE does not exist."
  exit 1
fi

base64 --decode --input "$INPUT_FILE" --output "$OUTPUT_FILE"
echo "✅ Decoded $INPUT_FILE → $OUTPUT_FILE"



