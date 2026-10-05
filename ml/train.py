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
    train_svm = "ml/data/train.svm"
    dev_svm = "ml/data/dev.svm"
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

    # Fit Platt scaling calibration on dev split
    print("Fitting Platt scaling calibration on dev logits...")
    calibrator = LogisticRegression(solver="lbfgs", random_state=SEED)
    calibrator.fit(z_dev, y_dev)
    calib_a = float(calibrator.coef_[0][0])
    calib_b = float(calibrator.intercept_[0])
    print(f"Platt scaling params: A={calib_a:.4f}, B={calib_b:.4f}")

    # Compute calibrated dev metrics
    calibrated_probs = 1.0 / (1.0 + np.exp(-(calib_a * z_dev.ravel() + calib_b)))
    calibrated_preds = (calibrated_probs >= 0.5).astype(int)

    dev_metrics = {
        "accuracy": float(accuracy_score(y_dev, calibrated_preds)),
        "precision": float(precision_score(y_dev, calibrated_preds, zero_division=0)),
        "recall": float(recall_score(y_dev, calibrated_preds)),
        "f1": float(f1_score(y_dev, calibrated_preds)),
        "roc_auc": float(roc_auc_score(y_dev, calibrated_probs))
    }
    print(f"Calibrated Dev Metrics: {dev_metrics}")

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
