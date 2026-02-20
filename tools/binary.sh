#!/bin/bash

OUTPUT_DIR="../app/src/main/assets"
SCHEMA="../datamodel/src/main/fbs/source.fbs"

# Convert constellations using Gradle converter (GeoJSON -> FlatBuffer)
./gradlew :tools:run --args="--type constellations"

# Convert stars/messier using flatc (JSON -> FlatBuffer, still uses old pipeline)
for catalog in stars messier; do
    flatc --binary -o "${OUTPUT_DIR}" "${SCHEMA}" "data/${catalog}.json"
    mv "${OUTPUT_DIR}/data/${catalog}.bin" "${OUTPUT_DIR}/${catalog}.bin"
done

echo "Binary files written to $OUTPUT_DIR"
