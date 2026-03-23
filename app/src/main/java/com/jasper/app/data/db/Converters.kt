package com.jasper.app.data.db

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Converters {

    @TypeConverter
    @JvmStatic
    fun floatArrayToByteArray(value: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(value.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        buffer.asFloatBuffer().put(value)
        return buffer.array()
    }

    @TypeConverter
    @JvmStatic
    fun byteArrayToFloatArray(value: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(value).order(ByteOrder.nativeOrder())
        return FloatArray(value.size / Float.SIZE_BYTES) { buffer.float }
    }
}
