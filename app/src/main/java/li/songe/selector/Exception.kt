package li.songe.selector

import li.songe.selector.property.BinaryExpression
import li.songe.selector.property.ValueExpression


sealed class GkdException(override val message: String) : Exception(message) {
    // for kotlin js
    @Suppress("unused")
    val outMessage: String
        get() = message
}


data class SyntaxException(
    override val message: String,
    val expectedValue: String,
    val index: Int
) : GkdException(message)


sealed class TypeException(override val message: String) : GkdException(message)


data class UnknownIdentifierException(
    val value: ValueExpression.Identifier,
) : TypeException("Unknown Identifier: ${value.stringify()}")


data class UnknownMemberException(
    val value: ValueExpression.MemberExpression,
) : TypeException("Unknown Member: ${value.stringify()}")


data class UnknownIdentifierMethodException(
    val value: ValueExpression.Identifier,
) : TypeException("Unknown Identifier Method: ${value.stringify()}")


data class UnknownIdentifierMethodParamsException(
    val value: ValueExpression.CallExpression,
) : TypeException("Unknown Identifier Method Params: ${value.stringify()}")


data class UnknownMemberMethodException(
    val value: ValueExpression.MemberExpression,
) : TypeException("Unknown Member Method: ${value.stringify()}")


data class UnknownMemberMethodParamsException(
    val value: ValueExpression.CallExpression,
) : TypeException("Unknown Member Method Params: ${value.stringify()}")


data class MismatchParamTypeException(
    val call: ValueExpression.CallExpression,
    val argument: ValueExpression,
    val type: PrimitiveType
) : TypeException("Mismatch Param Type: ${argument.stringify()} should be ${type.key}")


data class MismatchExpressionTypeException(
    val exception: BinaryExpression,
    val leftType: PrimitiveType,
    val rightType: PrimitiveType,
) : TypeException("Mismatch Expression Type: ${exception.stringify()}")


data class MismatchOperatorTypeException(
    val exception: BinaryExpression,
) : TypeException("Mismatch Operator Type: ${exception.stringify()}")
