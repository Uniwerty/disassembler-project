package exception

sealed class DisassemblerException(reason: String) : RuntimeException(reason) {
    class UnexpectedInstructionException(instruction: Int) :
        DisassemblerException("Unexpected instruction found: ${instruction.toString(2)}")

    class UnexpectedRegisterException(register: Int) :
        DisassemblerException("Unexpected register found: $register")
}