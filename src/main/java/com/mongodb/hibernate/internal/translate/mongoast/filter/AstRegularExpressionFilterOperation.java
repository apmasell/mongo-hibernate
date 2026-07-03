/*
 * Copyright 2025-present MongoDB, Inc.
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

package com.mongodb.hibernate.internal.translate.mongoast.filter;

import org.bson.BsonRegularExpression;
import org.bson.BsonWriter;

/**
 * See <a href="https://www.mongodb.com/docs/manual/reference/operator/query/regex/#mongodb-query-op.-regex">regular
 * expression query predicate</a>, <a href="https://www.mongodb.com/docs/manual/tutorial/query-documents/">Query
 * Documents</a>.
 *
 * @hidden
 */
public record AstRegularExpressionFilterOperation(String pattern, String options) implements AstFilterOperation {
    /**
     * Escapes a string in a way that is compatible with the server PCRE-implementation.
     *
     * <p>Rather than fencing the text in {@code \Q...\E} like {@link java.util.regex.Pattern#quote(String)}, this
     * backslash-escapes each metacharacter individually and appends to a string builder.
     *
     * @param text the text to convert to an exact-match equivalent regular expression
     * @param result a string builder that will hold the regular expression that matches the supplied text
     */
    public static void quoteMeta(CharSequence text, StringBuilder result) {
        text.codePoints().forEach(c -> {
            if (isMetacharacter(c)) {
                result.append('\\');
            }
            result.appendCodePoint(c);
        });
    }

    private static boolean isMetacharacter(int c) {
        // Escape every non-word ASCII character. This covers all PCRE metacharacters (including the backslash
        // itself) without needing to enumerate them; over-escaping an ASCII character that is not actually a
        // metacharacter is harmless in PCRE.
        return c < 0x80 && !Character.isLetterOrDigit(c) && c != '_';
    }

    @Override
    public void render(BsonWriter writer) {
        writer.writeStartDocument();
        {
            writer.writeName("$regex");
            writer.writeRegularExpression(new BsonRegularExpression(pattern, options));
        }
        writer.writeEndDocument();
    }
}
