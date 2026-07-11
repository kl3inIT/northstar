import 'package:image_picker/image_picker.dart';
import 'package:northstar/domain/models/capture_models.dart';

abstract interface class ReceiptSourcePicker {
  Future<ReceiptUpload?> pick(ReceiptSource source);
}

class ReceiptPicker implements ReceiptSourcePicker {
  ReceiptPicker({ImagePicker? picker}) : _picker = picker ?? ImagePicker();

  final ImagePicker _picker;

  @override
  Future<ReceiptUpload?> pick(ReceiptSource source) async {
    final image = await _picker.pickImage(
      source: source == ReceiptSource.camera
          ? ImageSource.camera
          : ImageSource.gallery,
      maxWidth: 2400,
      imageQuality: 92,
      requestFullMetadata: false,
    );
    if (image == null) {
      return null;
    }
    final bytes = await image.readAsBytes();
    if (bytes.isEmpty) {
      throw const ReceiptPickerException('The selected image is empty.');
    }
    if (bytes.length > 15 * 1024 * 1024) {
      throw const ReceiptPickerException(
        'Choose a receipt image smaller than 15 MB.',
      );
    }
    return ReceiptUpload(
      bytes: bytes,
      filename: image.name,
      mimeType: image.mimeType ?? _mimeTypeFor(image.name),
    );
  }

  String _mimeTypeFor(String name) {
    final lower = name.toLowerCase();
    if (lower.endsWith('.png')) {
      return 'image/png';
    }
    if (lower.endsWith('.heic') || lower.endsWith('.heif')) {
      return 'image/heic';
    }
    return 'image/jpeg';
  }
}

class ReceiptPickerException implements Exception {
  const ReceiptPickerException(this.message);

  final String message;

  @override
  String toString() => message;
}
