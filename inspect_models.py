#!/usr/bin/env python3
"""
Model inspection script to compare TFLite models
"""
import sys
import numpy as np

try:
    import tensorflow as tf
except ImportError:
    print("ERROR: TensorFlow not installed. Install with: pip install tensorflow")
    sys.exit(1)

def inspect_tflite_model(model_path):
    """Inspect a TFLite model and print detailed information"""
    print(f"\n{'='*70}")
    print(f"Inspecting: {model_path}")
    print(f"{'='*70}")
    
    try:
        # Load the model
        interpreter = tf.lite.Interpreter(model_path=model_path)
        interpreter.allocate_tensors()
        
        # Get input details
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()
        
        print(f"\n📥 INPUT TENSORS ({len(input_details)}):")
        for i, input_detail in enumerate(input_details):
            print(f"  Input[{i}]:")
            print(f"    Name: {input_detail['name']}")
            print(f"    Shape: {input_detail['shape']}")
            print(f"    Type: {input_detail['dtype']}")
            if 'quantization' in input_detail and input_detail['quantization'][0] != 0:
                scale, zero_point = input_detail['quantization']
                print(f"    Quantization: scale={scale}, zero_point={zero_point}")
            else:
                print(f"    Quantization: None (float model)")
        
        print(f"\n📤 OUTPUT TENSORS ({len(output_details)}):")
        for i, output_detail in enumerate(output_details):
            print(f"  Output[{i}]:")
            print(f"    Name: {output_detail['name']}")
            print(f"    Shape: {output_detail['shape']}")
            print(f"    Type: {output_detail['dtype']}")
            if 'quantization' in output_detail and output_detail['quantization'][0] != 0:
                scale, zero_point = output_detail['quantization']
                print(f"    Quantization: scale={scale}, zero_point={zero_point}")
            else:
                print(f"    Quantization: None (float model)")
        
        # Analyze output format
        print(f"\n🔍 MODEL FORMAT ANALYSIS:")
        if len(output_details) == 1:
            shape = output_details[0]['shape']
            print(f"  Single output tensor detected")
            if len(shape) == 3:
                batch, dim1, dim2 = shape
                if dim2 > dim1 and dim2 > 100:
                    print(f"  ✓ YOLO11 TRANSPOSED format: [batch={batch}, classes={dim1}, predictions={dim2}]")
                    num_classes = dim1 - 4  # Subtract bbox coordinates
                    print(f"  ✓ Detected {num_classes} classes (first 4 channels are bbox coordinates)")
                elif dim1 > dim2:
                    print(f"  ✓ YOLO11 STANDARD format: [batch={batch}, predictions={dim1}, features={dim2}]")
                    num_classes = dim2 - 4
                    print(f"  ✓ Detected {num_classes} classes (last {num_classes} features are class scores)")
                else:
                    print(f"  ⚠️ Unusual format: shape={shape}")
            elif len(shape) == 2:
                batch, num_classes = shape
                print(f"  ✓ SIMPLE CLASSIFICATION format: [batch={batch}, classes={num_classes}]")
        else:
            print(f"  Multi-tensor output (legacy format): {len(output_details)} tensors")
        
        # Test inference with dummy data
        print(f"\n🧪 TEST INFERENCE:")
        input_shape = input_details[0]['shape']
        input_dtype = input_details[0]['dtype']
        
        if input_dtype == np.uint8:
            dummy_input = np.random.randint(0, 256, size=input_shape, dtype=np.uint8)
        elif input_dtype == np.float32:
            dummy_input = np.random.random(size=input_shape).astype(np.float32)
        else:
            print(f"  ⚠️ Unsupported input dtype: {input_dtype}")
            return
        
        interpreter.set_tensor(input_details[0]['index'], dummy_input)
        
        try:
            interpreter.invoke()
            print(f"  ✓ Inference successful with dummy input")
            
            # Print output value ranges
            for i, output_detail in enumerate(output_details):
                output_data = interpreter.get_tensor(output_detail['index'])
                print(f"  Output[{i}] value range: [{output_data.min():.4f}, {output_data.max():.4f}]")
                print(f"  Output[{i}] mean: {output_data.mean():.4f}, std: {output_data.std():.4f}")
                
        except Exception as e:
            print(f"  ✗ Inference failed: {e}")
        
        print(f"\n{'='*70}\n")
        return True
        
    except Exception as e:
        print(f"\n❌ ERROR loading model: {e}")
        import traceback
        traceback.print_exc()
        return False

def compare_models(model1_path, model2_path):
    """Compare two models side by side"""
    print("\n" + "="*70)
    print("MODEL COMPARISON SUMMARY")
    print("="*70)
    
    try:
        interp1 = tf.lite.Interpreter(model_path=model1_path)
        interp1.allocate_tensors()
        input1 = interp1.get_input_details()[0]
        output1 = interp1.get_output_details()
        
        interp2 = tf.lite.Interpreter(model_path=model2_path)
        interp2.allocate_tensors()
        input2 = interp2.get_input_details()[0]
        output2 = interp2.get_output_details()
        
        print("\n📊 COMPARISON:")
        print(f"\nModel 1 (Current): {model1_path}")
        print(f"  Input: {input1['shape']} {input1['dtype']}")
        print(f"  Outputs: {len(output1)} tensors")
        for i, out in enumerate(output1):
            print(f"    Output[{i}]: {out['shape']} {out['dtype']}")
        
        print(f"\nModel 2 (New): {model2_path}")
        print(f"  Input: {input2['shape']} {input2['dtype']}")
        print(f"  Outputs: {len(output2)} tensors")
        for i, out in enumerate(output2):
            print(f"    Output[{i}]: {out['shape']} {out['dtype']}")
        
        print("\n🔍 DIFFERENCES:")
        if not np.array_equal(input1['shape'], input2['shape']):
            print(f"  ⚠️ Input shapes differ: {input1['shape']} vs {input2['shape']}")
        else:
            print(f"  ✓ Input shapes match: {input1['shape']}")
        
        if input1['dtype'] != input2['dtype']:
            print(f"  ⚠️ Input types differ: {input1['dtype']} vs {input2['dtype']}")
        else:
            print(f"  ✓ Input types match: {input1['dtype']}")
        
        if len(output1) != len(output2):
            print(f"  ⚠️ Number of outputs differ: {len(output1)} vs {len(output2)}")
        else:
            print(f"  ✓ Number of outputs match: {len(output1)}")
        
        for i in range(min(len(output1), len(output2))):
            if not np.array_equal(output1[i]['shape'], output2[i]['shape']):
                print(f"  ⚠️ Output[{i}] shapes differ: {output1[i]['shape']} vs {output2[i]['shape']}")
            else:
                print(f"  ✓ Output[{i}] shapes match: {output1[i]['shape']}")
            
            if output1[i]['dtype'] != output2[i]['dtype']:
                print(f"  ⚠️ Output[{i}] types differ: {output1[i]['dtype']} vs {output2[i]['dtype']}")
        
        print("\n" + "="*70)
        
    except Exception as e:
        print(f"❌ Comparison failed: {e}")

if __name__ == "__main__":
    current_model = r"C:\Users\disne\projects\Dice-Eye-App\app\src\main\assets\die_classifier.tflite"
    new_model = r"C:\Users\disne\Downloads\d6TrainingCont\my_model\my_model_float32.tflite"
    
    print("🔬 TFLite Model Inspector")
    print("="*70)
    
    # Inspect current model
    success1 = inspect_tflite_model(current_model)
    
    # Inspect new model
    success2 = inspect_tflite_model(new_model)
    
    # Compare if both loaded successfully
    if success1 and success2:
        compare_models(current_model, new_model)
    else:
        print("\n⚠️ Could not compare models - one or both failed to load")
