/*
 * Copyright 2026-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.hibernate.internal.dialect.function;

import static com.mongodb.hibernate.internal.dialect.function.FunctionParameterDefinition.divideAndSomethingAsInt;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.EXPRESSION;
import static org.hibernate.query.sqm.produce.function.FunctionParameterType.TEMPORAL;
import static org.hibernate.query.sqm.produce.function.FunctionParameterType.TEMPORAL_UNIT;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.translate.AbstractMqlTranslator;
import com.mongodb.hibernate.internal.translate.mongoast.AstArithmeticExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstBinaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstNamedOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstUnaryOperatorExpression;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.query.sqm.function.AbstractSqmSelfRenderingFunctionDescriptor;
import org.hibernate.query.sqm.produce.function.ArgumentTypesValidator;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.produce.function.StandardFunctionArgumentTypeResolvers;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.expression.ExtractUnit;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Defines an operator for a function as used by {@link MongoExpressionPositionalFunction},
 * {@link MongoExpressionNamedFunction}, and {@link MongoExpressionUnaryFunction} that can either provide a fixed
 * operator name or generate an appropriate operator based on a parameter
 */
public final class MongoExtractFunction extends AbstractSqmSelfRenderingFunctionDescriptor
        implements ExpressionFunction {
    private static final AstExpression MILLIS_PER_DAY =
            new AstLiteralExpression(new AstLiteral(new BsonInt32(24 * 60 * 60 * 1000)));

    public static AstExpression truncateToTime(AstExpression date) {
        return new AstUnaryOperatorExpression(
                "$toDate",
                new AstBinaryOperatorExpression(
                        AstArithmeticExpressionOperator.MOD,
                        new AstUnaryOperatorExpression("$toLong", date),
                        MILLIS_PER_DAY));
    }

    public MongoExtractFunction(TypeConfiguration typeConfiguration) {
        super(
                "extract",
                new ArgumentTypesValidator(StandardArgumentsValidators.exactly(2), TEMPORAL_UNIT, TEMPORAL),
                StandardFunctionReturnTypeResolvers.useArgType(1),
                StandardFunctionArgumentTypeResolvers.invariant(typeConfiguration, TEMPORAL_UNIT, TEMPORAL));
    }

    @Override
    public void render(
            SqlAppender sqlAppender,
            List<? extends SqlAstNode> sqlAstArguments,
            ReturnableType<?> returnType,
            SqlAstTranslator<?> walker) {
        var translator = AbstractMqlTranslator.cast(walker);

        var input = translator.acceptAndYield(sqlAstArguments.get(1), EXPRESSION);
        var unit = ((ExtractUnit) sqlAstArguments.get(0)).getUnit();
        translator.yield(
                EXPRESSION,
                switch (unit) {
                    case DATE ->
                        new AstNamedOperatorExpression(
                                "$dateTrunc",
                                new TreeMap<>(Map.of(
                                        "date",
                                        input,
                                        "unit",
                                        new AstLiteralExpression(new AstLiteral(new BsonString("day"))))));
                    case DAY, DAY_OF_MONTH -> new AstUnaryOperatorExpression("$dayOfMonth", input);
                    case DAY_OF_WEEK -> new AstUnaryOperatorExpression("$dayOfWeek", input);
                    case DAY_OF_YEAR -> new AstUnaryOperatorExpression("$dayOfYear", input);
                    case EPOCH -> new AstUnaryOperatorExpression("$tsSecond", input);
                    case HOUR -> new AstUnaryOperatorExpression("$hour", input);
                    case MINUTE -> new AstUnaryOperatorExpression("$minute", input);
                    case MONTH -> new AstUnaryOperatorExpression("$month", input);
                    case NANOSECOND ->
                        new AstUnaryOperatorExpression(
                                "$toLong",
                                new AstBinaryOperatorExpression(
                                        AstArithmeticExpressionOperator.MULTIPLY,
                                        new AstUnaryOperatorExpression("$millisecond", input),
                                        new AstLiteralExpression(new AstLiteral(new BsonInt32(1000)))));
                    case NATIVE -> new AstUnaryOperatorExpression("$toDate", input);
                    case QUARTER ->
                        divideAndSomethingAsInt(new AstUnaryOperatorExpression("$month", input), 3, "$ceil");
                    case SECOND ->
                        new AstUnaryOperatorExpression("$toDouble", new AstUnaryOperatorExpression("$second", input));
                    case TIME -> truncateToTime(input);
                    case WEEK, WEEK_OF_YEAR -> new AstUnaryOperatorExpression("$week", input);
                    case WEEK_OF_MONTH ->
                        divideAndSomethingAsInt(new AstUnaryOperatorExpression("$dayOfMonth", input), 7, "$ceil");
                    case YEAR -> new AstUnaryOperatorExpression("$year", input);
                    case OFFSET, TIMEZONE_HOUR, TIMEZONE_MINUTE ->
                        throw new FeatureNotSupportedException("Time unit %s not supported".formatted(unit));
                });
        ;
    }
}
