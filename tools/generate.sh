#!/bin/bash

# Generate JSON from raw catalogs
./gradlew :tools:run --args="--type stars --input data/stars.csv --output data/stars.json"
./gradlew :tools:run --args="--type constellations --input data/constellations.txt --output data/constellations.json"
./gradlew :tools:run --args="--type messier --input data/messier.csv --output data/messier.json"
