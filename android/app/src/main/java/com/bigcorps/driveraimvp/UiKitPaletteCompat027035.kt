package com.srrotas.app

/**
 * RC3.5 compatibility alias.
 * UiKit names the secondary surface `surfaceAlt`, while SrUi023 exposes
 * the same semantic color as `surfaceMuted`.
 */
val UiKit.Palette.surfaceMuted: Int
    get() = surfaceAlt
