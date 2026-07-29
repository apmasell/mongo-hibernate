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

import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.EXPRESSION;

import com.mongodb.hibernate.internal.translate.AbstractMqlTranslator;
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstVariableExpression;
import java.util.List;
import java.util.Objects;
import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.query.sqm.function.AbstractSqmSelfRenderingFunctionDescriptor;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.produce.function.StandardFunctionArgumentTypeResolvers;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.type.BasicTypeReference;
import org.hibernate.type.spi.TypeConfiguration;

/** Defines a HQL function that maps to a Mongo expression that takes no arguments */
public final class MongoExpressionNoArgumentFunction extends AbstractSqmSelfRenderingFunctionDescriptor
        implements ExpressionFunction {

    private final AstExpression expression;

    /**
     * Create a function definition for a server variable
     *
     * @param typeConfiguration the type information of the Hibernate context
     * @param returnType the type that this function will return
     * @param variableName the name of the server variable (excluding the <code>$$</code>)
     * @return the function definition
     * @see <a href="https://www.mongodb.com/docs/manual/reference/aggregation-variables/">Aggregation Variables</a>
     */
    public static MongoExpressionNoArgumentFunction forServerVariable(
            String hqlName,
            TypeConfiguration typeConfiguration,
            BasicTypeReference<?> returnType,
            String variableName) {
        return new MongoExpressionNoArgumentFunction(
                hqlName, typeConfiguration, returnType, new AstVariableExpression(variableName));
    }
    /**
     * Create a new function definition
     *
     * @param hqlName the name for the function in HQL
     * @param typeConfiguration the type information of the Hibernate context
     * @param returnType the type that this function will return
     * @param expression the expression to use when this function is called
     */
    public MongoExpressionNoArgumentFunction(
            String hqlName,
            TypeConfiguration typeConfiguration,
            BasicTypeReference<?> returnType,
            AstExpression expression) {
        super(
                hqlName,
                StandardArgumentsValidators.NO_ARGS,
                StandardFunctionReturnTypeResolvers.invariant(Objects.requireNonNull(
                        typeConfiguration.getBasicTypeRegistry().resolve(returnType))),
                StandardFunctionArgumentTypeResolvers.NULL);
        this.expression = expression;
    }

    @Override
    public void render(
            SqlAppender sqlAppender,
            List<? extends SqlAstNode> arguments,
            ReturnableType<?> returnType,
            SqlAstTranslator<?> walker) {
        var translator = AbstractMqlTranslator.cast(walker);
        translator.yield(EXPRESSION, expression);
    }
}
