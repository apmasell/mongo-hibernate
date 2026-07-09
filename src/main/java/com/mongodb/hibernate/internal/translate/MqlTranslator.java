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

package com.mongodb.hibernate.internal.translate;

import com.mongodb.hibernate.internal.translate.mongoast.AstValue;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

interface MqlTranslator<T, M extends AbstractMqlTranslator<?>, R> {
    TranslationResult<Object> UNINITIALIZED = new Failure<>("Internal error: uninitialized value");
    NamedShape<String> FIELD_NAME = new NamedShape<>(String.class, "field name");
    NamedShape<AstFilter> FILTER = new NamedShape<>(AstFilter.class, "match filter");
    NamedShape<AstValue> VALUE = new NamedShape<>(AstValue.class, "value");

    default MqlTranslator<List<? extends T>, M, List<R>> asList() {
        return new AccumulatingMapper<>(this) {
            @Override
            List<R> constructOutput() {
                return new ArrayList<>();
            }

            @Override
            void add(List<R> accumulator, R item) {
                accumulator.add(item);
            }
        };
    }

    abstract class AccumulatingMapper<T, M extends AbstractMqlTranslator<?>, R, A>
            implements MqlTranslator<List<? extends T>, M, A> {
        private final MqlTranslator<T, M, R> inner;

        AccumulatingMapper(MqlTranslator<T, M, R> inner) {
            this.inner = inner;
        }

        abstract A constructOutput();

        abstract void add(A accumulator, R item);

        @Override
        public final TranslationResult<A> translate(List<? extends T> input, M translator) {
            var results = constructOutput();
            var shapes = new TreeSet<String>();
            var mismatches = new ArrayList<ShapeMismatch>();
            for (var i = 0; i < input.size(); i++) {
                final var result = inner.translate(input.get(i), translator);
                if (result instanceof MqlTranslator.Success<R> success) {
                    add(results, success.value);
                    shapes.add(success.shape);
                } else if (result instanceof MqlTranslator.InvalidShape<R> shape) {
                    mismatches.add(shape.intoMultiple().withContext("index " + i));
                } else if (result instanceof MqlTranslator.Failure<R> failure) {
                    return new Failure<>(failure.error);
                }
            }
            if (mismatches.isEmpty()) {
                return new Success<>(results, shapes.stream().collect(Collectors.joining(" and ", "list of", "")));
            } else {
                return new InvalidShape<>(mismatches);
            }
        }
    }

    record NamedShape<T>(Class<T> type, String name) {}

    abstract sealed class TranslationResult<T> permits Success, Failure, InvalidShape {
        abstract <S> TranslationResult<S> cast(NamedShape<S> namedShape);

        abstract <S> TranslationResult<S> map(Function<? super T, S> mapper);

        abstract TranslationResult<Object> relax();
    }

    final class Success<T> extends TranslationResult<T> {
        final T value;
        final String shape;

        Success(T value, String shape) {
            this.value = value;
            this.shape = shape;
        }

        @Override
        <S> TranslationResult<S> cast(NamedShape<S> namedShape) {
            return namedShape.type().isInstance(value)
                    ? new Success<>(namedShape.type().cast(value), shape)
                    : new InvalidShape<>(new Mismatch(shape, this.shape));
        }

        @Override
        <S> TranslationResult<S> map(Function<? super T, S> mapper) {
            return new Success<>(mapper.apply(value), shape);
        }

        @Override
        TranslationResult<Object> relax() {
            return new Success<>(value, shape);
        }
    }

    final class Failure<T> extends TranslationResult<T> {
        final String error;

        Failure(String error) {
            this.error = error;
        }

        @Override
        <S> TranslationResult<S> cast(NamedShape<S> namedShape) {
            return new Failure<>(error);
        }

        @Override
        <S> TranslationResult<S> map(Function<? super T, S> mapper) {
            return new Failure<>(error);
        }

        @Override
        TranslationResult<Object> relax() {
            return new Failure<>(error);
        }
    }

    final class InvalidShape<T> extends TranslationResult<T> {
        final List<ShapeMismatch> mismatches;

        InvalidShape(ShapeMismatch mismatch) {
            mismatches = List.of(mismatch);
        }

        private InvalidShape(List<ShapeMismatch> mismatches) {
            this.mismatches = mismatches;
        }

        InvalidShape(Stream<ShapeMismatch> mismatches) {
            this.mismatches = mismatches.toList();
        }

        @Override
        <S> TranslationResult<S> cast(NamedShape<S> namedShape) {
            return new InvalidShape<>(mismatches);
        }

        @Override
        <S> TranslationResult<S> map(Function<? super T, S> mapper) {
            return new InvalidShape<>(mismatches);
        }

        @Override
        TranslationResult<Object> relax() {
            return new InvalidShape<>(mismatches);
        }

        public ShapeMismatch intoMultiple() {
            return new MultipleMismatches(mismatches);
        }
    }

    sealed class ShapeMismatch permits Mismatch, MultipleMismatches, WithContext {
        final ShapeMismatch withContext(String context) {
            return new WithContext(this, context);
        }
    }

    final class Mismatch extends ShapeMismatch {
        final String expected;
        final String found;

        public Mismatch(String expected, String found) {
            this.expected = expected;
            this.found = found;
        }
    }

    final class MultipleMismatches extends ShapeMismatch {
        final List<ShapeMismatch> mismatches;

        MultipleMismatches(List<ShapeMismatch> mismatches) {
            this.mismatches = mismatches;
        }
    }

    final class WithContext extends ShapeMismatch {
        final ShapeMismatch root;
        final String context;

        public WithContext(ShapeMismatch root, String context) {
            this.root = root;
            this.context = context;
        }
    }

    TranslationResult<R> translate(T input, M translator);

    default <S> MqlTranslator<S, M, R> from(Function<? super S, T> mapper) {
        final var inner = this;
        return new MqlTranslator<>() {
            @Override
            public TranslationResult<R> translate(S input, M translator) {
                return inner.translate(mapper.apply(input), translator);
            }
        };
    }

    default <S> MqlTranslator<T, M, S> then(Function<? super R, S> mapper) {
        final var inner = this;
        return new MqlTranslator<>() {
            @Override
            public TranslationResult<S> translate(T input, M translator) {
                return inner.translate(input, translator).map(mapper);
            }
        };
    }

    abstract class Merge2<T, A, B, M extends AbstractMqlTranslator<?>, R> implements MqlTranslator<T, M, R> {
        private final MqlTranslator<T, ? super M, A> left;
        private final String leftName;
        private final MqlTranslator<T, ? super M, B> right;
        private final String rightName;

        Merge2(
                MqlTranslator<T, ? super M, A> left,
                String leftName,
                MqlTranslator<T, ? super M, B> right,
                String rightName) {
            this.left = left;
            this.leftName = leftName;
            this.right = right;
            this.rightName = rightName;
        }

        protected abstract String infix(T input);

        protected abstract R merge(T input, A left, B right);

        protected abstract String shape(T input);

        @Override
        public final TranslationResult<R> translate(T input, M translator) {
            final var leftResult = left.translate(input, translator);
            final var rightResult = right.translate(input, translator);
            if (leftResult instanceof MqlTranslator.Success<A> leftSuccess
                    && rightResult instanceof MqlTranslator.Success<B> rightSuccess) {
                return new Success<>(merge(input, leftSuccess.value, rightSuccess.value), shape(input));
            }
            if (leftResult instanceof MqlTranslator.Failure<A> failure) {
                return new Failure<>(failure.error);
            }
            if (rightResult instanceof MqlTranslator.Failure<B> failure) {
                return new Failure<>(failure.error);
            }
            final var infix = infix(input);
            return new InvalidShape<>(Stream.concat(
                    leftResult instanceof MqlTranslator.InvalidShape<A> leftShape
                            ? Stream.of(leftShape.intoMultiple().withContext(leftName + " of " + infix))
                            : Stream.empty(),
                    rightResult instanceof MqlTranslator.InvalidShape<B> rightShape
                            ? Stream.of(rightShape.intoMultiple().withContext(rightName + " of " + infix))
                            : Stream.empty()));
        }
    }

    default <S extends R> MqlTranslator<T, M, S> into(NamedShape<S> namedShape) {
        final var inner = this;
        return new MqlTranslator<T, M, S>() {
            @Override
            public TranslationResult<S> translate(T input, M translator) {
                return inner.translate(input, translator).cast(namedShape);
            }
        };
    }
}
