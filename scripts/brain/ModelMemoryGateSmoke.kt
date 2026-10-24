import com.medmate.personal.brain.*

fun main() {
    val size = 1_000L * ModelMemoryGate.MIB
    val minimum = ModelMemoryGate.estimatedRequiredBytes(size)
    check(minimum > size)
    check(ModelMemoryGate.canAttemptLoad(size, minimum + 300L * ModelMemoryGate.MIB, false))
    check(!ModelMemoryGate.canAttemptLoad(size, minimum, false))
    check(!ModelMemoryGate.canAttemptLoad(size, minimum + 400L * ModelMemoryGate.MIB, true))
    check(!ModelMemoryGate.canAttemptLoad(size, 0, false))
    check(runCatching { ModelMemoryGate.estimatedRequiredBytes(-1) }.isFailure)
    println("PASS ModelMemoryGate: 6 pre-flight memory checks")
}
