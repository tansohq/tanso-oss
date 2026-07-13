#!/bin/bash

if [ -z "$1" ]; then
  echo "Usage: ./encode.sh [env]  # example: ./encode.sh staging"
  exit 1
fi

INPUT_FILE="application-${1}.yaml"
OUTPUT_FILE="${1}.b64"
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")

if [ ! -f "$INPUT_FILE" ]; then
  echo "File $INPUT_FILE does not exist."
  exit 1
fi

base64 --input "$INPUT_FILE" --output "$OUTPUT_FILE"
echo "✅ Encoded $INPUT_FILE → $OUTPUT_FILE"

echo "🚙 Moving file"
mkdir ~/projects/scripts/output/${TIMESTAMP}/
mv $OUTPUT_FILE ~/projects/scripts/output/${TIMESTAMP}/
