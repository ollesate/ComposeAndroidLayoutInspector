class Signal<T> {
    private val listeners = mutableListOf<(T) -> Unit>()

    operator fun invoke(value: T) {
        println("Invoke")
        listeners.forEach { listener ->
            listener(value)
        }
    }

    operator fun invoke(listener: (T) -> Unit) {
        println("add listener")
        listeners += listener
    }
}