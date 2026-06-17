#!/usr/bin/env python3
"""
Train an on-device fish-species classifier for Snapper and export it to TFLite.

Pipeline:
  1. Read the species + scientific names from app/src/main/assets/fish_facts.json.
  2. Download CC-licensed photos per species from the iNaturalist API.
  3. Transfer-learn an EfficientNetV2-B3 classifier (frozen base + head, then full fine-tune).
  4. Export fish_model.tflite (float16) + fish_labels.txt.

Why this recipe (read before "improving" it): the bundled model scores ~96% on held-out
iNaturalist photos but misidentifies real phone shots — fish held by the lip, partly out
of frame, with a hand/dock/glare in view. That's distribution shift, not a weak model. The
fixes below all target it: (a) a bigger backbone at higher resolution for fine-grained
species (e.g. the bass family); (b) aggressive augmentation that *manufactures* phone-photo
conditions at train time — hard zoom-in (partial fish), translation (fish pushed to the
edge), rotation, brightness/contrast swings, and noise; (c) more images per species. The
Android side (util/FishClassifier.kt, SIZE) MUST match IMG_SIZE below.

WHERE TO RUN: a machine/Colab with a GPU and TensorFlow. This repo's NixOS box has no
TF/pip/GPU, so run this on Google Colab (free GPU) or any box with `pip install tensorflow`.

  # Colab:  upload this file + fish_facts.json, then:
  !pip install -q tensorflow requests pillow
  !python train_fish_model.py --facts fish_facts.json --out . --per-species 600

Drop the resulting fish_model.tflite + fish_labels.txt into app/src/main/assets/ and rebuild.
The Android side (util/FishClassifier.kt) loads them automatically.
"""
import argparse, json, os, time, hashlib, io, random
import urllib.request, urllib.parse

INAT = "https://api.inaturalist.org/v1/observations"
UA = {"User-Agent": "Snapper-model-trainer/1.0 (fishing app; contact marwan.ansari@gmail.com)"}

# Generic dataset names ("Crappie","Bullhead") -> a representative species to search iNat for.
SEARCH_OVERRIDE = {
    "Crappie": "Pomoxis nigromaculatus",
    "Bullhead": "Ameiurus melas",
    "Hybrid Striped Bass": "Morone saxatilis",  # looks like striped bass; train on that
}

# Backbone registry. The Android side reads the input size from the model, so you can switch
# backbone/size freely here without touching FishClassifier.kt. Defaults target a phone with
# a generous (200 MB) app budget and accuracy as the priority — EfficientNetV2-S at 384px is
# the sweet spot (~42 MB float16, native 384 res, strong fine-grained accuracy, trains on a
# free Colab T4). Bump to "v2m" (~108 MB) for the largest model that still fits the budget;
# drop to "b0" (~8 MB, 224px) only if you must train on a CPU box (the bigger ones OOM there).
BACKBONES = {
    "v2s": ("EfficientNetV2S", 384),
    "v2m": ("EfficientNetV2M", 384),
    "v2b3": ("EfficientNetV2B3", 300),
    "b0": ("EfficientNetB0", 224),
}
DEFAULT_BACKBONE = "v2s"


def get_json(url):
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=40))


def taxon_id(scientific):
    """Resolve a scientific name to an iNaturalist taxon id (most specific match)."""
    q = urllib.parse.urlencode({"q": scientific, "rank": "species,genus", "per_page": 1})
    res = get_json(f"https://api.inaturalist.org/v1/taxa?{q}")
    results = res.get("results", [])
    return results[0]["id"] if results else None


def download_species(name, scientific, out_dir, target):
    """Download up to `target` research-grade, licensed photos for a species into out_dir."""
    search = SEARCH_OVERRIDE.get(name, scientific)
    tid = taxon_id(search)
    if not tid:
        print(f"  !! no iNat taxon for {name} ({search})"); return 0
    saved, page = 0, 1
    while saved < target and page <= 20:
        params = urllib.parse.urlencode({
            "taxon_id": tid, "photos": "true", "quality_grade": "research",
            "photo_license": "cc0,cc-by,cc-by-nc,cc-by-sa",  # redistributable for training
            "per_page": 100, "page": page, "order_by": "votes",
        })
        try:
            obs = get_json(f"{INAT}?{params}").get("results", [])
        except Exception as e:
            print(f"  page {page} error: {e}"); break
        if not obs:
            break
        for o in obs:
            for p in o.get("photos", []):
                url = p.get("url", "").replace("/square.", "/medium.")
                if not url:
                    continue
                try:
                    data = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30).read()
                except Exception:
                    continue
                h = hashlib.md5(data).hexdigest()[:12]
                with open(os.path.join(out_dir, f"{h}.jpg"), "wb") as f:
                    f.write(data)
                saved += 1
                if saved >= target:
                    break
            if saved >= target:
                break
        page += 1
        time.sleep(1.0)  # be polite to the API
    print(f"  {name}: {saved} images")
    return saved


def gather(facts, raw_dir, per_species):
    os.makedirs(raw_dir, exist_ok=True)
    labels = sorted(facts.keys())
    for name in labels:
        d = os.path.join(raw_dir, name.replace(" ", "_"))
        os.makedirs(d, exist_ok=True)
        have = len([f for f in os.listdir(d) if f.endswith(".jpg")])
        if have >= per_species:
            print(f"  {name}: already {have}, skip"); continue
        download_species(name, facts[name]["scientific"], d, per_species)
    return labels


def clean(raw_dir, labels):
    """Re-save every image as clean RGB JPEG; delete anything that won't decode.
    TF's image pipeline crashes on truncated/CMYK/PNG-as-jpg files, so normalize first."""
    from PIL import Image, ImageFile
    ImageFile.LOAD_TRUNCATED_IMAGES = False
    for name in labels:
        d = os.path.join(raw_dir, name.replace(" ", "_"))
        if not os.path.isdir(d):
            continue
        kept = 0
        for fn in list(os.listdir(d)):
            p = os.path.join(d, fn)
            try:
                with Image.open(p) as im:
                    im = im.convert("RGB")
                    if min(im.size) < 48:  # drop tiny/thumbnail junk
                        raise ValueError("too small")
                    im.save(p, "JPEG", quality=90)
                kept += 1
            except Exception:
                try: os.remove(p)
                except OSError: pass
        print(f"  {name}: {kept} usable images")
        if kept < 20:
            print(f"  !! WARNING: {name} has only {kept} images — accuracy will suffer")


def train(raw_dir, out_dir, labels, epochs, backbone="v2s", batch=12):
    import tensorflow as tf
    app_name, size = BACKBONES[backbone]
    # batch 12 fits a Colab T4 (16 GB) for v2s@384; drop to 8 for v2m, raise for small models.
    train_ds = tf.keras.utils.image_dataset_from_directory(
        raw_dir, validation_split=0.15, subset="training", seed=42, shuffle=True,
        image_size=(size, size), batch_size=batch, label_mode="categorical",
        class_names=[l.replace(" ", "_") for l in labels])
    val_ds = tf.keras.utils.image_dataset_from_directory(
        raw_dir, validation_split=0.15, subset="validation", seed=42, shuffle=False,
        image_size=(size, size), batch_size=batch, label_mode="categorical",
        class_names=[l.replace(" ", "_") for l in labels])
    # image_dataset_from_directory already shuffles file order each epoch. Do NOT add a big
    # .shuffle() on the BATCHED dataset — that buffers thousands of decoded images and OOMs.
    # Bounded prefetch only.
    train_ds = train_ds.prefetch(2)
    val_ds = val_ds.prefetch(2)

    # Augmentation tuned to mimic real phone shots, NOT clean iNat photos:
    #  - RandomZoom mostly zooms IN (negative factors) → the model sees partial fish
    #    (a head held to the camera) instead of always the whole body.
    #  - RandomTranslation shoves the fish toward the frame edge → off-center / cropped.
    #  - rotation + brightness/contrast cover hand-held angles and sun glare.
    # Heavier than a "clean dataset" recipe on purpose.
    aug = tf.keras.Sequential([
        tf.keras.layers.RandomFlip("horizontal"),
        tf.keras.layers.RandomRotation(0.20),
        tf.keras.layers.RandomZoom(height_factor=(-0.35, 0.10)),  # negative = zoom in
        tf.keras.layers.RandomTranslation(0.20, 0.20),
        tf.keras.layers.RandomContrast(0.25),
        tf.keras.layers.RandomBrightness(0.25, value_range=(0, 255)),
    ])
    # Big EfficientNetV2 at high resolution — the capacity that separates fine-grained species
    # (e.g. spotted vs largemouth vs smallmouth bass). Normalization is built in, so feed
    # raw [0,255] — matches util/FishClassifier.kt.
    print(f"Backbone: {app_name} @ {size}px")
    base = getattr(tf.keras.applications, app_name)(
        input_shape=(size, size, 3), include_top=False, weights="imagenet")
    base.trainable = False
    inputs = tf.keras.Input((size, size, 3))
    x = aug(inputs)
    x = base(x, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.3)(x)
    outputs = tf.keras.layers.Dense(len(labels), activation="softmax")(x)
    model = tf.keras.Model(inputs, outputs)

    loss = tf.keras.losses.CategoricalCrossentropy(label_smoothing=0.1)
    ckpt_path = os.path.join(out_dir, "fish_best.keras")
    cbs = [
        tf.keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=5,
                                         restore_best_weights=True),
        tf.keras.callbacks.ReduceLROnPlateau(monitor="val_accuracy", factor=0.3,
                                             patience=2, min_lr=1e-6),
        # Persist best epoch to disk so a kill/OOM doesn't lose progress.
        tf.keras.callbacks.ModelCheckpoint(ckpt_path, monitor="val_accuracy",
                                           save_best_only=True),
    ]

    # Phase 1: warm up the new head on a frozen base.
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-3), loss=loss, metrics=["accuracy"])
    model.fit(train_ds, validation_data=val_ds, epochs=6, callbacks=cbs)

    # Phase 2: unfreeze the WHOLE base and fine-tune — this is what fixes underfitting.
    base.trainable = True
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-4), loss=loss, metrics=["accuracy"])
    hist = model.fit(train_ds, validation_data=val_ds, epochs=25, callbacks=cbs)
    best = max(hist.history.get("val_accuracy", [0.0]))
    print(f"\n=== BEST val_accuracy: {best:.4f} ===")

    # Export TFLite (float16 — small, accurate, no calibration set needed).
    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    conv.target_spec.supported_types = [tf.float16]
    tflite = conv.convert()
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "fish_model.tflite"), "wb") as f:
        f.write(tflite)
    with open(os.path.join(out_dir, "fish_labels.txt"), "w") as f:
        f.write("\n".join(labels))
    print(f"\nWrote {out_dir}/fish_model.tflite ({len(tflite)//1024} KB) + fish_labels.txt")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--facts", default="../app/src/main/assets/fish_facts.json")
    ap.add_argument("--out", default=".")
    ap.add_argument("--raw", default="fish_dataset")
    ap.add_argument("--per-species", type=int, default=600)
    ap.add_argument("--epochs", type=int, default=12)
    ap.add_argument("--backbone", choices=list(BACKBONES), default=DEFAULT_BACKBONE,
                    help="v2s (default, ~42MB) / v2m (~108MB, most accurate) / v2b3 / b0 (CPU-trainable)")
    ap.add_argument("--batch", type=int, default=12, help="lower to 8 for v2m, raise for b0")
    ap.add_argument("--skip-download", action="store_true")
    args = ap.parse_args()

    facts = json.load(open(args.facts))["facts"]
    labels = sorted(facts.keys())
    if not args.skip_download:
        gather(facts, args.raw, args.per_species)
    clean(args.raw, labels)
    # Only train on species that actually have images (avoids empty-class errors).
    labels = [l for l in labels
              if os.path.isdir(os.path.join(args.raw, l.replace(" ", "_")))
              and any(f.endswith(".jpg") for f in os.listdir(os.path.join(args.raw, l.replace(" ", "_"))))]
    print(f"Training on {len(labels)} species with images.")
    train(args.raw, args.out, labels, args.epochs, backbone=args.backbone, batch=args.batch)


if __name__ == "__main__":
    main()
