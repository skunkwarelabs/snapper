# Snapper on-device fish classifier

`train_fish_model.py` builds the TFLite model that replaces Gemini for fish ID.

## Why it's not run here
The dev box (NixOS) has no TensorFlow/pip/GPU, and there's no off-the-shelf TFLite model
for North American freshwater gamefish. So we train a small one from open iNaturalist photos.

## Run it on Google Colab (free GPU, ~30–60 min)
1. New Colab notebook → Runtime → Change runtime type → **GPU**.
2. Upload `train_fish_model.py` and `app/src/main/assets/fish_facts.json`.
3. Run:
   ```
   !pip install -q tensorflow requests
   !python train_fish_model.py --facts fish_facts.json --out . --per-species 300 --epochs 12
   ```
   - `--per-species` images downloaded per fish (300 is a good start; more = better/slower).
   - First run downloads the dataset (slow); re-runs reuse `fish_dataset/`. Add
     `--skip-download` once you have it.
4. Download the two output files and drop them into the app:
   ```
   app/src/main/assets/fish_model.tflite
   app/src/main/assets/fish_labels.txt
   ```
5. Rebuild. `util/FishClassifier.kt` loads them automatically; auto-ID switches on.

## Notes
- Images are pulled CC0/CC-BY/CC-BY-NC/CC-BY-SA only (fine for training).
- Model input: 224×224×3 float32, pixels 0–255. Output: softmax over `fish_labels.txt` order.
- Until the model is bundled, the app uses the manual species picker (fully offline).
- Length/weight come from `fish_facts.json` typical ranges, not the photo.
