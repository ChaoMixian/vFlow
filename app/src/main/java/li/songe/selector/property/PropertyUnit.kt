package li.songe.selector.property

import li.songe.selector.Stringify


data class PropertyUnit(
    val expression: Expression
) : Stringify {
    override fun stringify(): String {
        return "[${expression.stringify()}]"
    }
}
