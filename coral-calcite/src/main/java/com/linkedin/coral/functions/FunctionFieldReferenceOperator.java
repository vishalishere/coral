package com.linkedin.coral.functions;

import com.google.common.base.Preconditions;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlBinaryOperator;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlCharStringLiteral;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlWriter;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.util.SqlBasicVisitor;
import org.apache.calcite.sql.util.SqlVisitor;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorScope;


/**
 * Operator to reference fields of structs returned by SQL functions.
 * This supports following SQL:
 * {@code
 *   SELECT f(col_1, col_2).field_a FROM myTable
 * }
 * where {@code f} is a function that returns a ROW type containing {@code field_a}.
 *
 * TODO: Fix calcite and fold this into Calcite DOT operator
 *
 */
public class FunctionFieldReferenceOperator extends SqlBinaryOperator {
  public static final FunctionFieldReferenceOperator DOT = new FunctionFieldReferenceOperator();

  public FunctionFieldReferenceOperator() {
    super(".", SqlKind.DOT,
        80,
        true,
        null,
        null, OperandTypes.ANY_ANY);
  }

  @Override
  public SqlCall createCall(SqlLiteral functionQualifier, SqlParserPos pos, SqlNode... operands) {
    Preconditions.checkState(operands.length == 2);
    SqlCharStringLiteral fieldName = SqlLiteral.createCharString(fieldNameStripQuotes(operands[1]), SqlParserPos.ZERO);
    return super.createCall(functionQualifier, pos, operands[0], fieldName);
  }

  @Override
  public <R> void acceptCall(
      SqlVisitor<R> visitor,
      SqlCall call,
      boolean onlyExpressions,
      SqlBasicVisitor.ArgHandler<R> argHandler) {
    argHandler.visitChild(visitor, call, 0, call.operand(0));
  }

  @Override
  public void unparse(SqlWriter writer, SqlCall call, int leftPrec, int rightPrec) {
    call.operand(0).unparse(writer, getLeftPrec(), getRightPrec());
    writer.literal(".");
    writer.setNeedWhitespace(false);
      // strip quotes from fieldName
    String fieldName = fieldNameStripQuotes(call.operand(1));
    writer.identifier(fieldName);
  }

  @Override
  public RelDataType deriveType(SqlValidator validator, SqlValidatorScope scope, SqlCall call) {
    SqlNode firstOperand = call.operand(0);
    if (firstOperand instanceof SqlBasicCall) {
      RelDataType funcType = validator.deriveType(scope, firstOperand);
      if (funcType.isStruct()) {
        return funcType.getField(fieldNameStripQuotes(call.operand(1)), false, false).getType();
      }
    }
    return super.deriveType(validator, scope, call);
  }

  @Override
  public void validateCall(SqlCall call, SqlValidator validator, SqlValidatorScope scope,
      SqlValidatorScope operandScope) {
    call.operand(0).validateExpr(validator, operandScope);
  }

  public static String fieldNameStripQuotes(SqlNode node) {
    return Utils.stripQuotes(fieldName(node));
  }

  public static String fieldName(SqlNode node) {
    switch (node.getKind()) {
      case IDENTIFIER:
        return ((SqlIdentifier) node).getSimple();
      case LITERAL:
        return ((SqlLiteral) node).toValue();
      default:
        throw new IllegalStateException(
            String.format("Unknown operand type %s to reference a field, operand: %s", node.getKind(), node));
    }
  }
}