package com.ican.tvplay.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 生成用例。
 *
 * 覆盖冷启动关键路径：启动 MainActivity → 首页渲染。
 * 先从最小用例开始验证 baselineprofile 模块的基本工作流，
 * 跑通后再加滚动/导航等 CUJ 路径。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = baselineProfileRule.collect(
        packageName = "com.ican.tvplay",
        // 同时生成启动 profile，覆盖启动期执行的类 → 加速冷启动
        includeInStartupProfile = true,
    ) {
        // 启动 MainActivity，等待首页渲染到可交互
        startActivityAndWait()
    }
}
