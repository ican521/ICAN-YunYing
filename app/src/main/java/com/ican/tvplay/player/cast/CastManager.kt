package com.ican.tvplay.player.cast

/**
 * DLNA 投屏：遗留项（stub）。
 *
 * 参考项目 D:\fongmi-tv-mycustom 的投屏实现在 leanback 源集（dlna/DLNARendererService 等，
 * 基于 org.jupnp 全家桶 + mediarouter），定位为「TV 端接收投屏」；
 * 本项目为手机/平板移动端，jupnp 依赖重且与 Compose UI 无直接对接点，
 * 本次移植暂不实现，保留此入口接口，后续如需「投屏到电视」再引入 jupnp 实现。
 */
object CastManager {

    /** 投屏能力是否可用（当前恒为 false，UI 据此隐藏入口） */
    val isAvailable: Boolean = false

    /** 占位：开始扫描局域网投屏设备 */
    fun startDiscovery() {
        // TODO: 引入 org.jupnp 后实现设备扫描
    }

    /** 占位：投放当前播放地址到指定设备 */
    fun cast(url: String, deviceId: String) {
        // TODO: 引入 org.jupnp 后实现 AVTransport 投放
    }

    /** 占位：停止扫描并释放资源 */
    fun release() {
    }
}
