/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.query;

import org.apache.lucene.sandbox.search.CombinedFieldQuery;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.elasticsearch.core.Strings;
import org.elasticsearch.test.AbstractQueryTestCase;

import java.io.IOException;
import java.util.Arrays;

import static org.elasticsearch.index.query.QueryBuilders.combinedFieldsQuery;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;

public class CombinedFieldsQueryBuilderTests extends AbstractQueryTestCase<CombinedFieldsQueryBuilder> {
    private static final int NUMBER_OF_TESTQUERIES = 20;
    private static final String MISSING_WILDCARD_FIELD_NAME = "missing_*";
    private static final String MISSING_FIELD_NAME = "missing";

    @Override
    protected CombinedFieldsQueryBuilder doCreateTestQueryBuilder() {
        Object value = getRandomQueryText();
        String field = randomFrom(TEXT_FIELD_NAME, TEXT_ALIAS_FIELD_NAME, MISSING_FIELD_NAME, MISSING_WILDCARD_FIELD_NAME);
        CombinedFieldsQueryBuilder query = combinedFieldsQuery(value, field);

        if (randomBoolean()) {
            query.field(field);
        } else {
            query.field(field, 1.0f + randomFloat());
        }

        if (randomBoolean()) {
            query.operator(randomFrom(Operator.values()));
        }
        if (randomBoolean()) {
            query.minimumShouldMatch(randomMinimumShouldMatch());
        }
        if (randomBoolean()) {
            query.zeroTermsQuery(randomFrom(ZeroTermsQueryOption.NONE, ZeroTermsQueryOption.ALL, ZeroTermsQueryOption.OMIT));
        }
        if (randomBoolean()) {
            query.autoGenerateSynonymsPhraseQuery(randomBoolean());
        }
        return query;
    }

    /**
     * This check is very light, instead the parsing is tested in detail in {@link CombinedFieldsQueryParsingTests}.
     */
    @Override
    protected void doAssertLuceneQuery(CombinedFieldsQueryBuilder queryBuilder, Query query, SearchExecutionContext context) {
        assertThat(
            query,
            anyOf(
                Arrays.asList(
                    instanceOf(BooleanQuery.class),
                    instanceOf(TermQuery.class),
                    instanceOf(MatchAllDocsQuery.class),
                    instanceOf(MatchNoDocsQuery.class),
                    instanceOf(CombinedFieldQuery.class)
                )
            )
        );
    }

    public void testValuesFromXContent() throws IOException {
        String json = """
            {
              "combined_fields" : {
                "query" : "quick brown fox",
                "fields" : [ "abstract^1.0", "body^1.0", "title^1.0" ],
                "operator" : "AND",
                "zero_terms_query" : "ALL",
                "auto_generate_synonyms_phrase_query" : false,
                "boost" : 2.0
              }
            }""";

        CombinedFieldsQueryBuilder parsed = (CombinedFieldsQueryBuilder) parseQuery(json);
        checkGeneratedJson(json, parsed);

        assertEquals(json, "quick brown fox", parsed.value());
        assertEquals(json, 3, parsed.fields().size());
        assertEquals(json, Operator.AND, parsed.operator());
        assertEquals(json, 2.0, parsed.boost, 1e-6);
    }

    /**
     * We parse `minimum_should_match` to a String but other queries supporting this parameter also accept integer values and null
     */
    public void testMinumumShouldMatchFromXContent() throws IOException {
        Object[] testValues = new Object[] { 2, "\"2\"", "\"2%\"", null };
        Object[] expectedValues = new Object[] { "2", "2", "2%", null };
        int i = 0;
        for (Object value : testValues) {
            String json = Strings.format("""
                {
                  "combined_fields" : {
                    "query" : "quick brown fox",
                    "minimum_should_match" : %s
                  }
                }""", value);

            CombinedFieldsQueryBuilder parsed = (CombinedFieldsQueryBuilder) parseQuery(json);

            assertEquals(json, "quick brown fox", parsed.value());
            assertEquals(json, expectedValues[i], parsed.minimumShouldMatch());
            i++;
        }
    }

    @Override
    public void testToQuery() throws IOException {
        for (int runs = 0; runs < NUMBER_OF_TESTQUERIES; runs++) {
            SearchExecutionContext context = createSearchExecutionContext();
            assert context.isCacheable();
            context.setAllowUnmappedFields(true);
            CombinedFieldsQueryBuilder firstQuery = createTestQueryBuilder();
            CombinedFieldsQueryBuilder controlQuery = copyQuery(firstQuery);
            /* we use a private rewrite context here since we want the most realistic way of asserting that we are cacheable or not.
             * We do it this way in SearchService where
             * we first rewrite the query with a private context, then reset the context and then build the actual lucene query*/
            QueryBuilder rewritten = rewriteQuery(firstQuery, createQueryRewriteContext(), new SearchExecutionContext(context));
            Query firstLuceneQuery = rewritten.toQuery(context);
            if (firstQuery.zeroTermsQuery() != ZeroTermsQueryOption.OMIT) {
                assertNotNull("toQuery should not return null", firstLuceneQuery);
            }
            assertLuceneQuery(firstQuery, firstLuceneQuery, context);
            // remove after assertLuceneQuery since the assertLuceneQuery impl might access the context as well
            assertEquals(
                "query is not equal to its copy after calling toQuery, firstQuery: " + firstQuery + ", secondQuery: " + controlQuery,
                firstQuery,
                controlQuery
            );
            assertEquals(
                "equals is not symmetric after calling toQuery, firstQuery: " + firstQuery + ", secondQuery: " + controlQuery,
                controlQuery,
                firstQuery
            );
            assertThat(
                "query copy's hashcode is different from original hashcode after calling toQuery, firstQuery: "
                    + firstQuery
                    + ", secondQuery: "
                    + controlQuery,
                controlQuery.hashCode(),
                equalTo(firstQuery.hashCode())
            );

            CombinedFieldsQueryBuilder secondQuery = copyQuery(firstQuery);
            // query _name never should affect the result of toQuery, we randomly set it to make sure
            if (randomBoolean()) {
                secondQuery.queryName(
                    secondQuery.queryName() == null
                        ? randomAlphaOfLengthBetween(1, 30)
                        : secondQuery.queryName() + randomAlphaOfLengthBetween(1, 10)
                );
            }
            context = new SearchExecutionContext(context);
            Query secondLuceneQuery = rewriteQuery(secondQuery, createQueryRewriteContext(), new SearchExecutionContext(context)).toQuery(
                context
            );
            if (secondQuery.zeroTermsQuery() != ZeroTermsQueryOption.OMIT) {
                assertNotNull("toQuery should not return null", firstLuceneQuery);
            }
            assertLuceneQuery(secondQuery, secondLuceneQuery, context);

            if (builderGeneratesCacheableQueries()) {
                assertEquals(
                    "two equivalent query builders lead to different lucene queries hashcode",
                    secondLuceneQuery.hashCode(),
                    firstLuceneQuery.hashCode()
                );
                assertEquals(
                    "two equivalent query builders lead to different lucene queries",
                    rewrite(secondLuceneQuery),
                    rewrite(firstLuceneQuery)
                );
            }

            if (supportsBoost() && firstLuceneQuery instanceof MatchNoDocsQuery == false) {
                secondQuery.boost(firstQuery.boost() + 1f + randomFloat());
                Query thirdLuceneQuery = rewriteQuery(secondQuery, createQueryRewriteContext(), new SearchExecutionContext(context))
                    .toQuery(context);
                assertNotEquals(
                    "modifying the boost doesn't affect the corresponding lucene query",
                    rewrite(firstLuceneQuery),
                    rewrite(thirdLuceneQuery)
                );
            }
        }
    }
}
