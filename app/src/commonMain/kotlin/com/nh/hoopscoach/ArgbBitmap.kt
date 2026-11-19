package com.nh.hoopscoach

/**
 * Platform-neutral ARGB_8888 pixel buffer used by sprite composition.
 * Replaces android.graphics.Bitmap in commonMain.
 */
class ArgbBitmap(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size == width * height) { "pixels size ${pixels.size} != ${width * height}" }
    }

    fun getPixel(x: Int, y: Int): Int = pixels[y * width + x]

    fun setPixel(x: Int, y: Int, color: Int) {
        pixels[y * width + x] = color
    }

    fun eraseColor(color: Int) {
        pixels.fill(color)
    }

    fun getPixels(out: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        var dst = offset
        for (row in y until y + h) {
            val src = row * width + x
            System.arraycopy(pixels, src, out, dst, w)
            dst += stride
        }
    }

    fun setPixels(src: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        var from = offset
        for (row in y until y + h) {
            val dst = row * width + x
            System.arraycopy(src, from, pixels, dst, w)
            from += stride
        }
    }

    companion object {
        fun create(width: Int, height: Int) = ArgbBitmap(width, height, IntArray(width * height))
        fun create(pixels: IntArray, width: Int, height: Int) = ArgbBitmap(width, height, pixels)
    }
}
