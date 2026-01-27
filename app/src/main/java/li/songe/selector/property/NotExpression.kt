package li.songe.selector.property

import li.songe.selector.QueryContext
import li.songe.selector.Transform


data class NotExpression(
    val expression: Expression
) : Expression() {

    override fun <T> match(
        context: QueryContext<T>,
        transform: Transform<T>,
    ): Boolean {
        return !expression.match(context, transform)
    }

    override fun getBinaryExpressionList() = expression.getBinaryExpressionList()

    override fun stringify(): String {
        return "!(${expression.stringify()})"
    }
}
