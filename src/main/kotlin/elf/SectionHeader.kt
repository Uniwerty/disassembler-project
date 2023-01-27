package elf

import java.io.File

data class SectionHeader(private val source: File, private val offset: Long) : Element(source) {
    init {
        skip(offset)
    }

    val sh_name = readWord()
    val sh_type = readWord()
    val sh_flags = readWord()
    val sh_addr = readWord()
    val sh_offset = readWord()
    val sh_size = readWord()
    val sh_link = readWord()
    val sh_info = readWord()
    val sh_addralign = readWord()
    val sh_entsize = readWord()
}