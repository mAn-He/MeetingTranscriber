package com.meetingtranscriber.ml

object QwenTokenizerUtils {
    init {
        System.loadLibrary("qwen_tokenizer_jni")
    }

    // Qwen Tokenizer JNI
    external fun encode(text: String): LongArray
    external fun decode(tokens: LongArray): String
}
