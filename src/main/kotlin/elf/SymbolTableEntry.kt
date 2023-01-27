package elf

data class SymbolTableEntry(
    val st_name: Int,
    val st_value: Int,
    val st_size: Int,
    val st_info: Int,
    val st_other: Int,
    val st_shndx: Int,
    val name: String
) {
    val st_type: Int = st_info and 0xf
    val st_vis: Int = st_other and 0x3
    val st_bind: Int = st_info ushr 4
}