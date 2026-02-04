#!/bin/bash

OUTPUT_DIR="../app/src/main/assets"
SCHEMA="../datamodel/src/main/fbs/source.fbs"

# Convert each catalog using flatc
for catalog in stars constellations messier; do
    flatc --binary -o "${OUTPUT_DIR}" "${SCHEMA}" "data/${catalog}.json"
    mv "${OUTPUT_DIR}/data/${catalog}.bin" "${OUTPUT_DIR}/${catalog}.bin"
done

echo "Binary files written to $OUTPUT_DIR"
