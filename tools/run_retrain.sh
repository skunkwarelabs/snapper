#!/usr/bin/env bash
set -e
export LD_LIBRARY_PATH="/nix/store/cf1a53iqg6ncnygl698c4v0l8qam5a2q-gcc-14.3.0-lib/lib:/nix/store/f2q5ld1nipl8w1r2w8m6azhlm2varqgb-zlib-1.3.1/lib"
cd /home/mason/snapper/tools
echo "=== retrain: EfficientNetB0 + full fine-tune (reusing downloaded dataset) ==="
./venv/bin/python train_fish_model.py \
    --facts ../app/src/main/assets/fish_facts.json \
    --out . --per-species 250 --skip-download
echo "=== RETRAIN DONE — outputs: ==="
ls -la fish_model.tflite fish_labels.txt
