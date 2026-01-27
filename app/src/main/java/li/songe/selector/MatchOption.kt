package li.songe.selector



data class MatchOption(
    val fastQuery: Boolean = false,
) {
    companion object {
        val default = MatchOption()
    }
}
