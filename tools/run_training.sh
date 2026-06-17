#!/usr/bin/env bash
set -e
# 64-bit gcc libstdc++/libgomp + zlib so pip's TF wheels run under nix-ld.
export LD_LIBRARY_PATH="/nix/store/cf1a53iqg6ncnygl698c4v0l8qam5a2q-gcc-14.3.0-lib/lib:/nix/store/f2q5ld1nipl8w1r2w8m6azhlm2varqgb-zlib-1.3.1/lib"
cd /home/mason/snapper/tools

[ -d venv ] || python3 -m venv venv

echo "=== [1/3] installing tensorflow-cpu (resumes if partial) ==="
./venv/bin/pip install --upgrade pip
./venv/bin/pip install tensorflow-cpu pillow requests

echo "=== verifying TF import ==="
./venv/bin/python -c "import tensorflow as tf; print('TF', tf.__version__)"

echo "=== [2/3] download + clean + train + export ==="
./venv/bin/python train_fish_model.py \
    --facts ../app/src/main/assets/fish_facts.json \
    --out . --per-species 250 --epochs 12

echo "=== [3/3] DONE — outputs: ==="
ls -la fish_model.tflite fish_labels.txt
