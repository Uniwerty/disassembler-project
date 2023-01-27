package elf

import java.io.File

class ElfFile(filename: String) {
    companion object {
        private const val SECTION_HEADER_SIZE = 40
        private const val TEXT_RECORD_SIZE = 6
        private const val STRTAB_RECORD_SIZE = 8
        private const val SYMTAB_HEADER_TYPE = 0x2
    }

    val source = File(filename)
    val elfHeader: ElfHeader
    val shstrtabHeader: SectionHeader
    val textHeader: SectionHeader
    val strtabHeader: SectionHeader
    val symtabHeader: SectionHeader
    val symbolTable: SymbolTable

    init {
        elfHeader = readElfHeader()
        shstrtabHeader = readShstrtabHeader()
        textHeader = readTextHeader()
        strtabHeader = readStrtabHeader()
        symtabHeader = readSymtabHeader()
        symbolTable = readSymbolTable()
    }

    // Reading ELF header and checking file correctness
    private fun readElfHeader(): ElfHeader {
        val elfHeader = ElfHeader(source)
        elfHeader.check()
        return elfHeader
    }

    // Reading .shstrtab header
    private fun readShstrtabHeader(): SectionHeader =
        SectionHeader(
            source,
            elfHeader.e_shoff.toLong() + elfHeader.e_shentsize.toLong() * elfHeader.e_shstrndx.toLong()
        )

    private fun readTextHeader(): SectionHeader {
        // Finding .text record in .shstrtab
        val textRecordOffset = findSectionRecord(".text", TEXT_RECORD_SIZE)
        // Finding and reading .text header
        return readSectionHeader { it.sh_name == textRecordOffset }
    }

    private fun readStrtabHeader(): SectionHeader {
        // Finding .strtab record in .shstrtab
        val strtabRecordOffset = findSectionRecord(".strtab", STRTAB_RECORD_SIZE)
        // Finding and reading .strtab header
        return readSectionHeader { it.sh_name == strtabRecordOffset }
    }

    // Finding and reading .symtab header
    private fun readSymtabHeader(): SectionHeader =
        readSectionHeader { it.sh_type == SYMTAB_HEADER_TYPE }

    // Finding a specific record in .shstrtab
    private fun findSectionRecord(sectionName: String, recordSize: Int): Int {
        val shstrTable = StringTable(source, shstrtabHeader.sh_offset.toLong())
        var line = shstrTable.readString()
        while (line != sectionName) {
            line = shstrTable.readString()
        }
        return shstrTable.offset - recordSize
    }

    // Finding and reading a specific header
    private fun readSectionHeader(condition: (SectionHeader) -> Boolean): SectionHeader {
        var offset = elfHeader.e_shoff.toLong()
        var header = SectionHeader(source, offset)
        while (!condition(header)) {
            offset += SECTION_HEADER_SIZE
            header = SectionHeader(source, offset)
        }
        return header
    }

    // Reading .symtab section
    private fun readSymbolTable(): SymbolTable =
        SymbolTable(
            source,
            symtabHeader.sh_size / symtabHeader.sh_entsize,
            symtabHeader.sh_offset.toLong(),
            strtabHeader.sh_offset.toLong()
        )
}