package elf

import java.io.File

data class StringTable(private val source: File, private val tableOffset: Long) : Element(source) {
    var offset = 0

    init {
        skip(tableOffset)
    }

    fun readString(): String = StringBuilder().let {
        var cur = readByte()
        offset++
        while (cur != 0x0) {
            it.append(cur.toChar())
            cur = readByte()
            offset++
        }
        it.toString()
    }
}