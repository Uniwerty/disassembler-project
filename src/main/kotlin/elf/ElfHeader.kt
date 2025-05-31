package elf

import exception.ElfFileException
import java.io.File

data class ElfHeader(private val source: File) : Element(source) {
    internal val e_ident = IntArray(16)

    init {
        for (i in 0 until 16) {
            e_ident[i] = readByte()
        }
    }

    val e_type = readHalfWord()
    val e_machine = readHalfWord()
    val e_version = readWord()
    val e_entry = readWord()
    val e_phoff = readWord()
    val e_shoff = readWord()
    val e_flags = readWord()
    val e_ehsize = readHalfWord()
    val e_phentsize = readHalfWord()
    val e_phnum = readHalfWord()
    val e_shentsize = readHalfWord()
    val e_shnum = readHalfWord()
    val e_shstrndx = readHalfWord()

    fun check() = when {
        e_ident[0] != 0x7f || e_ident[1] != 0x45 || e_ident[2] != 0x4c || e_ident[3] != 0x46 ->
            throw ElfFileException.IllegalFileException("ELF")
        e_ident[4] != 0x01 -> throw ElfFileException.IllegalFileException("32-bit")
        e_ident[5] != 0x01 -> throw ElfFileException.IllegalFileException("little-endian")
        e_machine != 0xF3 -> throw ElfFileException.IllegalFileException("RISC-V")
        else -> {}
    }
}