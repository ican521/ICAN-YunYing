package com.ican.tvplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ican.tvplay.ui.AppRoot

/**
 * 唯一 Activity，承载全部 Compose 页面。
 * 同时注册 LAUNCHER 与 LEANBACK_LAUNCHER，手机 / 平板 / Android TV 共用同一入口。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppRoot()
        }
    }
}
