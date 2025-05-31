package elf

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

sealed class Element(source: File) {
    private val reader = BufferedInputStream(FileInputStream(source))

    protected fun skip(number: Long) = reader.skip(number)
    protected fun readByte(): Int = reader.read()
    protected fun readHalfWord(): Int = reader.read() + reader.read() * 0x100
    protected fun readWord(): Int =
        reader.read() + reader.read() * 0x100 + reader.read() * 0x10000 + reader.read() * 0x1000000
}