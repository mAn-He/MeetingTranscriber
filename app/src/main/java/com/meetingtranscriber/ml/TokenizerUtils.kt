package com.meetingtranscriber.ml

object TokenizerUtils {
    init {
        System.loadLibrary("sentencepiece_jni")
        System.loadLibrary("whisper_tokenizer_jni")
    }

    // Whisper BPE JNI
    external fun whisperEncode(text: String): LongArray
    external fun whisperDecode(tokens: LongArray): String

    // NLLB SentencePiece JNI
    external fun spEncode(text: String): LongArray
    external fun spDecode(tokens: LongArray): String
}
