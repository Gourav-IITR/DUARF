# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Gourav Mahunta

import datetime
import hashlib
import json
import os
import struct
import zlib
import numpy as np
from sklearn.datasets import load_svmlight_file
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, roc_auc_score

SEED = 42
np.random.seed(SEED)

def compute_file_sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def main():
    train_svm = "ml/data/train.libsvm" if os.path.exists("ml/data/train.libsvm") else "ml/data/train.svm"
    dev_svm = "ml/data/dev.libsvm" if os.path.exists("ml/data/dev.libsvm") else "ml/data/dev.svm"
    train_jsonl = "ml/data/train.jsonl"
    
    print("Loading LIBSVM datasets...")
    X_train, y_train = load_svmlight_file(train_svm, n_features=262144)
    X_dev, y_dev = load_svmlight_file(dev_svm, n_features=262144)

    # Convert +1/-1 to 1/0
    y_train = (y_train > 0).astype(int)
    y_dev = (y_dev > 0).astype(int)

    print(f"Train samples: {X_train.shape[0]} (Positive: {y_train.sum()}, Negative: {(1-y_train).sum()})")
    print(f"Dev samples:   {X_dev.shape[0]} (Positive: {y_dev.sum()}, Negative: {(1-y_dev).sum()})")

    # Hyperparameter tuning on dev set
    C_candidates = [0.1, 0.5, 1.0, 2.0]
    l1_candidates = [0.1, 0.3, 0.5]

    best_f1 = -1.0
    best_model = None
    best_params = {}

    print("\nTuning hyperparameters on dev set (SAGA solver, elasticnet penalty)...")
    for C in C_candidates:
        for l1 in l1_candidates:
            clf = LogisticRegression(
                penalty="elasticnet",
                solver="saga",
                C=C,
                l1_ratio=l1,
                class_weight="balanced",
                random_state=SEED,
                max_iter=150,
                tol=1e-3
            )
            clf.fit(X_train, y_train)
            dev_preds = clf.predict(X_dev)
            dev_f1 = f1_score(y_dev, dev_preds)
            print(f"  C={C:.2f}, l1_ratio={l1:.2f} -> Dev F1: {dev_f1:.4f}")
            if dev_f1 > best_f1:
                best_f1 = dev_f1
                best_model = clf
                best_params = {"C": C, "l1_ratio": l1}

    print(f"\nBest parameters: {best_params} with Dev F1: {best_f1:.4f}")

    # Decision function on dev split
    z_dev = best_model.decision_function(X_dev).reshape(-1, 1)

    # Fit Platt scaling calibration on dev split using target smoothing
    print("Fitting Platt scaling calibration on dev logits...")
    n_pos = float(np.sum(y_dev == 1))
    n_neg = float(np.sum(y_dev == 0))
    t_pos = (n_pos + 1.0) / (n_pos + 2.0)
    t_neg = 1.0 / (n_neg + 2.0)
    t = np.where(y_dev == 1, t_pos, t_neg)
    
    from scipy.optimize import minimize
    from scipy.special import expit
    
    def platt_loss(params):
        A, B = params
        logits = A * z_dev.ravel() + B
        log_p = -np.logaddexp(0, -logits)
        log_1mp = -np.logaddexp(0, logits)
        return -np.sum(t * log_p + (1.0 - t) * log_1mp)
        
    def platt_grad(params):
        A, B = params
        logits = A * z_dev.ravel() + B
        p = expit(logits)
        err = p - t
        return np.array([np.sum(err * z_dev.ravel()), np.sum(err)])

    res = minimize(platt_loss, [1.0, 0.0], jac=platt_grad, method="L-BFGS-B")
    calib_a = float(res.x[0])
    calib_b = float(res.x[1])
    print(f"Platt scaling params: A={calib_a:.4f}, B={calib_b:.4f}")

    # Compute calibrated dev metrics
    calibrated_probs = expit(calib_a * z_dev.ravel() + calib_b)
    calibrated_preds = (calibrated_probs >= 0.5).astype(int)

    dev_metrics = {
        "accuracy": float(accuracy_score(y_dev, calibrated_preds)),
        "precision": float(precision_score(y_dev, calibrated_preds, zero_division=0)),
        "recall": float(recall_score(y_dev, calibrated_preds)),
        "f1": float(f1_score(y_dev, calibrated_preds)),
        "roc_auc": float(roc_auc_score(y_dev, calibrated_probs))
    }

    # Compute per-language dev metrics
    dev_langs = []
    dev_jsonl = "ml/data/dev.jsonl"
    if os.path.exists(dev_jsonl):
        with open(dev_jsonl, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    dev_langs.append(json.loads(line).get("lang", "en"))

    per_lang_dev_metrics = {}
    if len(dev_langs) == len(y_dev):
        for l in sorted(set(dev_langs)):
            mask = np.array([lang == l for lang in dev_langs])
            if mask.sum() > 0 and len(np.unique(y_dev[mask])) > 1:
                per_lang_dev_metrics[l] = {
                    "count": int(mask.sum()),
                    "accuracy": float(accuracy_score(y_dev[mask], calibrated_preds[mask])),
                    "precision": float(precision_score(y_dev[mask], calibrated_preds[mask], zero_division=0)),
                    "recall": float(recall_score(y_dev[mask], calibrated_preds[mask], zero_division=0)),
                    "f1": float(f1_score(y_dev[mask], calibrated_preds[mask], zero_division=0))
                }
            elif mask.sum() > 0:
                per_lang_dev_metrics[l] = {
                    "count": int(mask.sum()),
                    "accuracy": float(accuracy_score(y_dev[mask], calibrated_preds[mask]))
                }

    print(f"Calibrated Dev Metrics: {dev_metrics}")
    print(f"Per-Language Dev Metrics: {per_lang_dev_metrics}")

    # Weight quantization to int8
    raw_weights = best_model.coef_[0]
    bias = float(best_model.intercept_[0])
    max_abs = float(np.max(np.abs(raw_weights)))
    scale = max_abs / 127.0 if max_abs > 0 else 1.0

    int8_weights = np.clip(np.round(raw_weights / scale), -127, 127).astype(np.int8)

    # Pack model.bin
    # Header format:
    # 4B magic ("PHRD")
    # 2B format_version (1)
    # 2B featurizer_version (1)
    # 1B log2_buckets (18)
    # 3B reserved (0, 0, 0)
    # 4B scale (float32)
    # 4B bias (float32)
    # 4B calib_a (float32)
    # 4B calib_b (float32)
    # Total header = 28 bytes
    header = struct.pack(
        "<4sHHB3sffff",
        b"PHRD",
        1,  # format version
        1,  # featurizer version
        18, # log2_buckets (2^18 = 262,144)
        b"\x00\x00\x00",
        scale,
        bias,
        calib_a,
        calib_b
    )
    assert len(header) == 28, f"Header size is {len(header)}, expected 28"

    weights_bytes = int8_weights.tobytes()
    assert len(weights_bytes) == 262144, f"Weights size is {len(weights_bytes)}, expected 262144"

    data_to_checksum = header + weights_bytes
    crc = zlib.crc32(data_to_checksum) & 0xFFFFFFFF
    crc_bytes = struct.pack("<I", crc)

    full_model_bytes = data_to_checksum + crc_bytes
    expected_total_size = 28 + 262144 + 4 # 262,176 bytes
    assert len(full_model_bytes) == expected_total_size, f"Total size is {len(full_model_bytes)}, expected {expected_total_size}"

    os.makedirs("packs/model", exist_ok=True)
    model_bin_path = "packs/model/model.bin"
    with open(model_bin_path, "wb") as f:
        f.write(full_model_bytes)
    print(f"Exported model binary to {model_bin_path} ({len(full_model_bytes)} bytes)")

    # Prepare model.json
    train_hash = compute_file_sha256(train_jsonl) if os.path.exists(train_jsonl) else ""
    model_metadata = {
        "format_version": 1,
        "featurizer_version": 1,
        "log2_buckets": 18,
        "generator_seed": SEED,
        "training_set_sha256": train_hash,
        "trained_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "best_hyperparameters": best_params,
        "platt_scaling": {
            "A": calib_a,
            "B": calib_b
        },
        "quantization": {
            "scale": scale,
            "bias": bias,
            "int8_range": [-127, 127]
        },
        "dev_metrics": dev_metrics,
        "per_language_dev_metrics": per_lang_dev_metrics,
        "operating_thresholds": {
            "low": {"caution": 0.55, "danger": 0.80},
            "balanced": {"caution": 0.45, "danger": 0.72},
            "high": {"caution": 0.35, "danger": 0.65}
        }
    }

    model_json_path = "packs/model/model.json"
    with open(model_json_path, "w", encoding="utf-8") as f:
        json.dump(model_metadata, f, indent=2)
    print(f"Exported model metadata to {model_json_path}")

if __name__ == "__main__":
    main()
