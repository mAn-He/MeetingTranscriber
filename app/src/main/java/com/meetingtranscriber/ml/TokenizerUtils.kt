package com.meetingtranscriber.ml

import android.util.Log

object TokenizerUtils {
    private const val TAG = "TokenizerUtils"
    private var jniLibrariesLoaded = false

    init {
        try {
            System.loadLibrary("sentencepiece_jni")
            System.loadLibrary("whisper_tokenizer_jni")
            jniLibrariesLoaded = true
            Log.i(TAG, "JNI libraries loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "JNI libraries not found, using fallback implementation: ${e.message}")
            jniLibrariesLoaded = false
        }
    }

    // Whisper BPE JNI (with fallback)
    fun whisperEncode(text: String): LongArray {
        return if (jniLibrariesLoaded) {
            try {
                whisperEncodeNative(text)
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "JNI whisperEncode failed, using fallback")
                whisperEncodeFallback(text)
            }
        } else {
            whisperEncodeFallback(text)
        }
    }

    fun whisperDecode(tokens: LongArray): String {
        return if (jniLibrariesLoaded) {
            try {
                whisperDecodeNative(tokens)
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "JNI whisperDecode failed, using fallback")
                whisperDecodeFallback(tokens)
            }
        } else {
            whisperDecodeFallback(tokens)
        }
    }

    // NLLB SentencePiece JNI (with fallback)
    fun spEncode(text: String): LongArray {
        return if (jniLibrariesLoaded) {
            try {
                spEncodeNative(text)
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "JNI spEncode failed, using fallback")
                spEncodeFallback(text)
            }
        } else {
            spEncodeFallback(text)
        }
    }

    fun spDecode(tokens: LongArray): String {
        return if (jniLibrariesLoaded) {
            try {
                spDecodeNative(tokens)
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "JNI spDecode failed, using fallback")
                spDecodeFallback(tokens)
            }
        } else {
            spDecodeFallback(tokens)
        }
    }

    // Native JNI methods (실제 구현이 있을 때만 동작)
    private external fun whisperEncodeNative(text: String): LongArray
    private external fun whisperDecodeNative(tokens: LongArray): String
    private external fun spEncodeNative(text: String): LongArray
    private external fun spDecodeNative(tokens: LongArray): String

    // Fallback implementations (JNI 없을 때 사용)
    private fun whisperEncodeFallback(text: String): LongArray {
        Log.d(TAG, "Using fallback Whisper tokenizer for: $text")
        // 간단한 토큰화 (단어 단위)
        return text.split(" ").mapIndexed { index, word ->
            (word.hashCode().toLong() and 0xFFFF) + index
        }.toLongArray()
    }

    private fun whisperDecodeFallback(tokens: LongArray): String {
        Log.d(TAG, "Using fallback Whisper detokenizer for ${tokens.size} tokens")
        return "🎤 음성 인식 결과 (Fallback): ${tokens.joinToString(" ") { "토큰$it" }}"
    }

    private fun spEncodeFallback(text: String): LongArray {
        Log.d(TAG, "Using fallback SentencePiece tokenizer for: $text")
        return text.split(" ").map { word ->
            (word.hashCode().toLong() and 0xFFFF) + 1000
        }.toLongArray()
    }

    private fun spDecodeFallback(tokens: LongArray): String {
        Log.d(TAG, "Using fallback SentencePiece detokenizer for ${tokens.size} tokens")
        return "🈲 번역 결과 (Fallback): ${tokens.joinToString(" ") { "번역$it" }}"
    }
}