import disassembler.Disassembler
import exception.DisassemblerException
import exception.ElfFileException

fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("Invalid arguments given. Please write input and output filenames!")
    } else {
        try {
            val disassembler = Disassembler(args[0])
            disassembler.disassembleTo(args[1])
        } catch (exception: DisassemblerException) {
            System.err.println("Disassembler exception occurred:")
            System.err.println(exception.message)
        } catch (exception: ElfFileException) {
            System.err.println("ELF file exception occurred:")
            System.err.println(exception.message)
        } catch (exception: Exception) {
            System.err.println("Something went wrong:")
            System.err.println(exception.message)
        }
    }
}