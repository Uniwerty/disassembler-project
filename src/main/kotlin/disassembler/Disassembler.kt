package disassembler

import elf.ElfFile
import exception.DisassemblerException
import java.io.*

class Disassembler(val filename: String) {
    companion object {
        private val REGISTERS = arrayOf(
            "zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
            "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
            "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
            "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
        )
        private val SYMBOL_TABLE_HEADER = String.format(
            "%s %-15s %7s %-8s %-8s %-8s %6s %s\n",
            "Symbol", "Value", "Size", "Type", "Bind", "Vis", "Index", "Name"
        )
    }

    private val file = ElfFile(filename)
    private val labels = markLabels()
    private val jumps = markJumps()

    fun disassembleTo(outputFilename: String) {
        val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(outputFilename)))
        writer.use {
            it.write(".text")
            it.write(System.lineSeparator())
            it.write(writeTextSection())
            it.write(System.lineSeparator())
            it.write(".symtab")
            it.write(System.lineSeparator())
            it.write(writeSymbolTable())
        }
    }

    private fun markLabels(): HashMap<Int, String> {
        val labels = HashMap<Int, String>()
        for (i in 0 until file.symbolTable.numberOfEntries) {
            val entry = file.symbolTable.getEntry(i)
            if (entry.st_type == 2) {
                labels[entry.st_value] = entry.name
            }
        }
        return labels
    }

    private fun markJumps(): HashMap<Int, String> {
        val jumps = HashMap<Int, String>()
        var labelNumber = 0
        var address = file.textHeader.sh_addr
        var byteNumber = 0
        val reader = BufferedInputStream(FileInputStream(filename))
        reader.skip(file.textHeader.sh_offset.toLong())
        while (byteNumber < file.textHeader.sh_size) {
            val code = reader.read32bit()
            if (isJumpInstruction(code)) {
                val jumpAddress = address + getJumpOffset(code)
                if (!labels.containsKey(jumpAddress)) {
                    labels[jumpAddress] = String.format("LOC_%05x", labelNumber)
                }
                jumps[address] = labels[jumpAddress] ?: ""
                labelNumber++
            }
            address += 4
            byteNumber += 4
        }
        return jumps
    }

    private fun isJumpInstruction(instruction: Int): Boolean {
        val opcode = instruction shl 25 ushr 25
        if (opcode == 0b1101111 || opcode == 0b1100011) {
            // JAL BEQ BNE BLT BGE BLTU BGEU
            return true
        }
        return false
    }

    private fun getJumpOffset(instruction: Int): Int {
        val opcode = instruction shl 25 ushr 25
        return when (opcode) {
            0b1101111 -> // JAL
                (instruction shl 1 ushr 22) * (1 shl 1) +
                        (instruction shl 11 ushr 31) * (1 shl 11) +
                        (instruction shl 12 ushr 24) * (1 shl 12) +
                        (instruction ushr 31) * (1 shl 20)
            0b1100011 -> // BEQ BNE BLT BGE BLTU BGEU
                (instruction shl 20 ushr 28) * (1 shl 1) +
                        (instruction shl 1 ushr 26) * (1 shl 5) +
                        (instruction shl 24 ushr 31) * (1 shl 11) +
                        (instruction ushr 31) * (1 shl 12)
            else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
        }
    }

    private fun writeTextSection(): String = StringBuilder().let {
        var address = file.textHeader.sh_addr
        var byteNumber = 0
        val reader = BufferedInputStream(FileInputStream(filename))
        reader.skip(file.textHeader.sh_offset.toLong())
        while (byteNumber < file.textHeader.sh_size) {
            val code = reader.read32bit()
            val instruction = getInstruction(code, address)
            it.append(writeInstruction(address, labels[address] ?: "", instruction))
                .append(System.lineSeparator())
            address += 4
            byteNumber += 4
        }
        it.toString()
    }

    private fun writeInstruction(address: Int, label: String, instruction: String): String =
        String.format("%08x %10s %s", address, label, instruction)

    private fun writeSymbolTable(): String = StringBuilder().let {
        val table = file.symbolTable
        it.append(SYMBOL_TABLE_HEADER)
        for (i in 0 until table.numberOfEntries) {
            val entry = table.getEntry(i)
            val type = when (entry.st_type) {
                0 -> "NOTYPE"
                1 -> "OBJECT"
                2 -> "FUNC"
                3 -> "SECTION"
                4 -> "FILE"
                13 -> "LOPROC"
                15 -> "HIPROC"
                else -> entry.st_type
            }
            val bind = when (entry.st_bind) {
                0 -> "LOCAL"
                1 -> "GLOBAL"
                2 -> "WEAK"
                13 -> "LOPROC"
                15 -> "HIPROC"
                else -> entry.st_bind
            }
            val vis = when (entry.st_vis) {
                0 -> "DEFAULT"
                1 -> "INTERNAL"
                2 -> "HIDDEN"
                3 -> "PROTECTED"
                4 -> "EXPORTED"
                5 -> "SINGLETON"
                6 -> "ELIMINATE"
                else -> entry.st_vis
            }
            val index = when (entry.st_shndx) {
                0 -> "UNDEF"
                0xff00 -> "LORESERVE"
                0xff1f -> "HIPROC"
                0xfff1 -> "ABS"
                0xfff2 -> "COMMON"
                0xffff -> "HIRESERVE"
                else -> entry.st_shndx
            }
            it.append(
                String.format(
                    "[%4d] 0x%-15X %5d %-8s %-8s %-8s %6s %s\n",
                    i, entry.st_value, entry.st_size, type, bind, vis, index, entry.name
                )
            )
        }
        it.toString()
    }

    private fun getInstruction(instruction: Int, address: Int): String {
        val opcode = instruction shl 25 ushr 25
        when (opcode) {
            0b0110111 -> { // LUI
                val rd = instruction shl 20 ushr 27
                val imm = instruction ushr 12
                return String.format("%s %s, %s", "lui", getRegister(rd), imm)
            }
            0b0010111 -> { // AUIPC
                val rd = instruction shl 20 ushr 27
                val imm = instruction ushr 12
                return String.format("%s %s, %s", "auipc", getRegister(rd), imm)
            }
            0b1101111 -> { // JAL
                val rd = instruction shl 20 ushr 27
                val imm =
                    (instruction shl 1 ushr 22) +
                            (instruction shl 11 ushr 31) * (1 shl 10) +
                            (instruction shl 12 ushr 24) * (1 shl 11) +
                            (instruction ushr 31) * (1 shl 19)
                return String.format("%s %s, %s, %s", "jal", getRegister(rd), imm, jumps[address])
            }
            0b1100111 -> { // JALR
                val rd = instruction shl 20 ushr 27
                val rs1 = instruction shl 12 ushr 27
                val imm = instruction ushr 20
                return String.format("%s %s, %s", "jalr", getRegister(rd), getRegister(rs1), imm)
            }
            0b1100011 -> { // BEQ BNE BLT BGE BLTU BGEU
                val rs1 = instruction shl 12 ushr 27
                val rs2 = instruction shl 7 ushr 27
                val imm =
                    (instruction shl 20 ushr 28) +
                            (instruction shl 1 ushr 26) * (1 shl 4) +
                            (instruction shl 24 ushr 31) * (1 shl 10) +
                            (instruction ushr 31) * (1 shl 11)
                val func3 = instruction shl 17 ushr 29
                val name = when (func3) {
                    0b000 -> "beq"
                    0b001 -> "bne"
                    0b100 -> "blt"
                    0b101 -> "bge"
                    0b110 -> "bltu"
                    0b111 -> "bgeu"
                    else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                }
                return String.format(
                    "%s %s, %s, %s, %s", name, getRegister(rs1), getRegister(rs2), imm, jumps[address]
                )
            }
            0b0000011 -> { // LB LH LW LBU LHU
                val rd = instruction shl 20 ushr 27
                val rs1 = instruction shl 12 ushr 27
                val imm = instruction ushr 20
                val func3 = instruction shl 17 ushr 29
                val name = when (func3) {
                    0b000 -> "lb"
                    0b001 -> "lh"
                    0b010 -> "lw"
                    0b100 -> "lbu"
                    0b101 -> "lhu"
                    else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                }
                return String.format("%s %s, %s(%s)", name, getRegister(rd), imm, getRegister(rs1))
            }
            0b0100011 -> { // SB SH SW
                val rs1 = instruction shl 12 ushr 27
                val rs2 = instruction shl 7 ushr 27
                val imm = (instruction shl 20 ushr 27) +
                        (instruction ushr 25) * (1 shl 5)
                val func3 = instruction shl 17 ushr 29
                val name = when (func3) {
                    0b000 -> "sb"
                    0b001 -> "sh"
                    0b010 -> "sw"
                    else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                }
                return String.format("%s %s, %s(%s)", name, getRegister(rs2), imm, getRegister(rs1))
            }
            0b0010011 -> { // ADDI SLTI SLTIU XORI ORI ANDI SLLI SRLI SRAI
                val rd = instruction shl 20 ushr 27
                val rs1 = instruction shl 12 ushr 27
                val func3 = instruction shl 17 ushr 29
                val imm = instruction ushr 20
                val shamt = instruction shl 7 ushr 27
                val func7 = instruction ushr 25
                val name = when (func3) {
                    0b000 -> "addi"
                    0b010 -> "slti"
                    0b011 -> "sltiu"
                    0b100 -> "xori"
                    0b110 -> "ori"
                    0b111 -> "andi"
                    0b001 -> when (func7) {
                        0b0000000 -> "slli"
                        else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                    }
                    0b101 -> when (func7) {
                        0b0000000 -> "srli"
                        0b0100000 -> "srai"
                        else -> DisassemblerException.UnexpectedInstructionException(instruction)
                    }
                    else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                }
                val value =
                    if (func3 == 0b001 && func7 == 0b0000000 ||
                        func3 == 0b101 && (func7 == 0b0000000 || func7 == 0b0100000)
                    ) shamt
                    else imm
                return String.format("%s %s, %s, %s", name, getRegister(rd), getRegister(rs1), value)
            }
            0b0110011 -> {
                // ADD SUB SLL SLT SLTU XOR SRL SRA OR AND
                // MUL MULH MULHSU MULHU DIV DIVU REM REMU
                val rd = instruction shl 20 ushr 27
                val rs1 = instruction shl 12 ushr 27
                val rs2 = instruction shl 7 ushr 27
                val func3 = instruction shl 17 ushr 29
                val func7 = instruction ushr 25
                val name = when (func3) {
                    0b000 -> when (func7) {
                        0b0000000 -> "add"
                        0b0100000 -> "sub"
                        0b0000001 -> "mul"
                        else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                    }
                    0b001 -> when (func7) {
                        0b0000000 -> "sll"
                        0b0000001 -> "mulh"
                        else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                    }
                    0b010 -> when (func7) {
                        0b0000000 -> "slt"
                        0b0000001 -> "mulhsu"
                        else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                    }
                    0b011 -> when (func7) {
                        0b0000000 -> "sltu"
                        0b0000001 -> "mulhu"
                        else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                    }
                    0b100 -> when (func7) {
                        0b0000000 -> "xor"
                        0b0000001 -> "div"
                        else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                    }
                    0b101 -> when (func7) {
                        0b0000000 -> "srl"
                        0b0100000 -> "sra"
                        0b0000001 -> "divu"
                        else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                    }
                    0b110 -> when (func7) {
                        0b0000000 -> "or"
                        0b0000001 -> "rem"
                        else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                    }
                    0b111 -> when (func7) {
                        0b0000000 -> "and"
                        0b0000001 -> "remu"
                        else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                    }
                    else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                }
                return String.format("%s %s, %s, %s", name, getRegister(rd), getRegister(rs1), getRegister(rs2))
            }
            0b1110011 -> { // ECALL EBREAK CSRRW CSRRS CSRRC CSRRWI CSRRSI CSRRCI
                val func3 = instruction shl 17 ushr 29
                val rd = instruction shl 20 ushr 27
                val rs1 = instruction shl 12 ushr 27
                val func12 = instruction ushr 20
                val uimm = instruction.toLong() shl 12 ushr 27
                val csr = func12
                if (func3 == 0b000) {
                    if (rd == 0b00000 && rs1 == 0b00000) {
                        if (func12 == 0b000000000000) {
                            return "ecall"
                        } else if (func12 == 0b000000000001) {
                            return "ebreak"
                        }
                    }
                } else {
                    val name = when (func3) {
                        0b001 -> "csrrw"
                        0b010 -> "csrrs"
                        0b011 -> "csrrc"
                        0b101 -> "csrrwi"
                        0b110 -> "csrrsi"
                        0b111 -> "csrrci"
                        else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                    }
                    val value = when (func3) {
                        0b001, 0b010, 0b011 -> getRegister(rs1)
                        0b101, 0b110, 0b111 -> uimm
                        else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
                    }
                    return String.format("%s %s, %s, %s", name, getRegister(rd), csr, value)
                }
            }
            else -> throw DisassemblerException.UnexpectedInstructionException(instruction)
        }
        throw DisassemblerException.UnexpectedInstructionException(instruction)
    }

    private fun getRegister(register: Int): String {
        if (register < 0 || register > REGISTERS.size) {
            throw DisassemblerException.UnexpectedRegisterException(register)
        }
        return REGISTERS[register]
    }

    private fun BufferedInputStream.read32bit(): Int {
        return read() + read() * 0x100 + read() * 0x10000 + read() * 0x1000000
    }
}