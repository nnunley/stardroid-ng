#!/bin/bash

# Download d3-celestial constellation data
curl -sL -o data/constellations.lines.json \
  https://raw.githubusercontent.com/ofrohn/d3-celestial/master/data/constellations.lines.json
curl -sL -o data/constellations.json \
  https://raw.githubusercontent.com/ofrohn/d3-celestial/master/data/constellations.json

# Generate JSON from raw catalogs (stars/messier still use old pipeline)
./gradlew :tools:run --args="--type stars --input data/stars.csv --output data/stars.json"
./gradlew :tools:run --args="--type messier --input data/messier.csv --output data/messier.json"
