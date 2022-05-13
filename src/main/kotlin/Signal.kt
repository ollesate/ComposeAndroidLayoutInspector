class Signal<T> {
    private val listeners = mutableListOf<(T) -> Unit>()

    operator fun invoke(value: T) {
        listeners.forEach { listener ->
            listener(value)
        }
    }

    operator fun invoke(listener: (T) -> Unit) {
        listeners += listener
    }
}