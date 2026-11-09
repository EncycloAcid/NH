package com.nh.hoopscoach.ui

import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear

/**
 * Back-compat entry point for portrait rendering. New portraits are the user's layered
 * body / jersey / hairstyle sprites — see [SpriteCompose].
 */
object CelRender {
    const val N = SpriteCompose.N

    fun render(f: Face, primary: Int, secondary: Int, number: Int, gear: Gear = Gear(), jerseyKey: String? = null): IntArray =
        SpriteCompose.render(f, primary, secondary, number, gear, jerseyKey)
}
