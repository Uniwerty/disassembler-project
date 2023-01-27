package elf

import java.io.File

data class SymbolTable(
    private val source: File,
    val numberOfEntries: Int,
    private val symtabOffset: Long,
    private val strtabOffset: Long
) : Element(source) {
    private val table = ArrayList<SymbolTableEntry>()

    init {
        skip(symtabOffset)
        for (i in 0 until numberOfEntries) {
            readEntry()
        }
    }

    fun getEntry(entryIndex: Int): SymbolTableEntry = table[entryIndex]

    private fun readEntry() {
        val st_name = readWord()
        val stringTable = StringTable(source, strtabOffset + st_name)
        table.add(
            SymbolTableEntry(
                st_name = st_name,
                st_value = readWord(),
                st_size = readWord(),
                st_info = readByte(),
                st_other = readByte(),
                st_shndx = readHalfWord(),
                name = stringTable.readString()
            )
        )
    }
}