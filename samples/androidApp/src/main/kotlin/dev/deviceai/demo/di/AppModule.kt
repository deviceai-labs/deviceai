package dev.deviceai.demo.di

import dev.deviceai.demo.AudioRecorder
import dev.deviceai.demo.LlmViewModel
import dev.deviceai.demo.SpeechViewModel
import org.koin.dsl.module

val appModule = module {
    single { AudioRecorder() }
    single { SpeechViewModel(get()) }
    single { LlmViewModel() }
}
