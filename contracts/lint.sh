#!/usr/bin/env bash

set -e

project_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." &> /dev/null && pwd)

pushd "$project_dir"

docker run --rm -v "$PWD":/spec redocly/cli lint

popd
