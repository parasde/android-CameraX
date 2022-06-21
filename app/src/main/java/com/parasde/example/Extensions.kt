package com.parasde.example

import android.content.Context
import android.os.Build
import android.view.Window
import androidx.core.view.WindowCompat
import java.util.concurrent.Executor

fun Context.mainExecutor(): Executor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    mainExecutor
} else {
    MainExecutor()
}

fun Window.fitSystemWindows() {
    WindowCompat.setDecorFitsSystemWindows(this, false)
}