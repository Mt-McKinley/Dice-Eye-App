"""
ONNX Model Opset Converter
Converts your ONNX model from Opset 22 to Opset 21 (compatible with ONNX Runtime 1.20.1)
"""

import onnx
from onnx import version_converter

def convert_model():
    print("=" * 60)
    print("  ONNX Model Opset Converter")
    print("=" * 60)

    input_model = "app/src/main/assets/my_model.onnx"
    output_model = "app/src/main/assets/my_model_opset21.onnx"

    print(f"\n[1/4] Loading model from: {input_model}")
    try:
        model = onnx.load(input_model)
        print(f"✓ Model loaded successfully")
        print(f"  Current Opset version: {model.opset_import[0].version}")
        print(f"  Model IR version: {model.ir_version}")
    except Exception as e:
        print(f"✗ Error loading model: {e}")
        return False

    print(f"\n[2/4] Converting to Opset 21...")
    try:
        # Convert to opset 21
        converted_model = version_converter.convert_version(model, 21)
        print(f"✓ Conversion successful")
        print(f"  New Opset version: {converted_model.opset_import[0].version}")
    except Exception as e:
        print(f"✗ Error converting model: {e}")
        print("\nNote: If conversion fails, you may need to re-export your model")
        print("from the original training framework (PyTorch, TensorFlow, etc.)")
        print("with opset_version=21 specified during export.")
        return False

    print(f"\n[3/4] Validating converted model...")
    try:
        onnx.checker.check_model(converted_model)
        print(f"✓ Model validation passed")
    except Exception as e:
        print(f"⚠ Warning during validation: {e}")
        print("The model may still work, continuing...")

    print(f"\n[4/4] Saving converted model to: {output_model}")
    try:
        onnx.save(converted_model, output_model)
        import os
        size_mb = os.path.getsize(output_model) / (1024 * 1024)
        print(f"✓ Model saved successfully ({size_mb:.2f} MB)")
    except Exception as e:
        print(f"✗ Error saving model: {e}")
        return False

    print("\n" + "=" * 60)
    print("  CONVERSION COMPLETE!")
    print("=" * 60)
    print(f"\n✓ Your converted model is ready at:")
    print(f"  {output_model}")
    print(f"\nNext steps:")
    print(f"1. Rename the old model (backup):")
    print(f"   ren app\\src\\main\\assets\\my_model.onnx my_model_opset22_backup.onnx")
    print(f"2. Rename the new model:")
    print(f"   ren app\\src\\main\\assets\\my_model_opset21.onnx my_model.onnx")
    print(f"3. Rebuild and run your app")
    print()

    return True

if __name__ == "__main__":
    try:
        import onnx
        print(f"Using ONNX version: {onnx.__version__}")
    except ImportError:
        print("✗ ONNX package not found!")
        print("\nPlease install it with:")
        print("  pip install onnx")
        exit(1)

    success = convert_model()

    if not success:
        print("\n" + "=" * 60)
        print("  ALTERNATIVE: Re-export from source")
        print("=" * 60)
        print("\nIf conversion failed, re-export your model from PyTorch/TensorFlow")
        print("with the correct opset version:")
        print("\nFor PyTorch:")
        print("  torch.onnx.export(model, dummy_input, 'my_model.onnx',")
        print("                    opset_version=21,")
        print("                    input_names=['images'],")
        print("                    output_names=['output'])")
        print("\nFor TensorFlow (via tf2onnx):")
        print("  python -m tf2onnx.convert --saved-model model_path")
        print("                            --output my_model.onnx")
        print("                            --opset 21")
        print()

