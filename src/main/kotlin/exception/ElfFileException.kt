package exception

sealed class ElfFileException(reason: String) : RuntimeException(reason) {
    class IllegalFileException(criterion: String) :
        ElfFileException("Illegal file given, not $criterion")
}