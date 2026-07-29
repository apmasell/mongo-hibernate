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

package com.mongodb.hibernate.query.function;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.MongoExtension;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SessionFactory(exportSchema = false)
@DomainModel(annotatedClasses = {DateFunctionIntegrationTests.Item.class})
@ExtendWith(MongoExtension.class)
public class DateFunctionIntegrationTests extends AbstractQueryIntegrationTests {
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String COLLECTION_NAME = "items";
    private static final Item ITEM = new Item(1, Instant.ofEpochMilli(123456789), Instant.ofEpochMilli(654321987));

    @SuppressWarnings("unchecked")
    private <T> void assertQueryResult(String hql, T expected, String expectedMql) {
        assertSelectionQuery(hql, (Class<T>) expected.getClass(), "{}", List.of(expected), Set.of(COLLECTION_NAME));
    }

    private <T> void assertQueryResult(String hql, Class<T> resultClass, Consumer<T> check, String expectedMql) {
        assertSelectionQuery(hql, resultClass, "{}", items -> items.forEach(check), Set.of(COLLECTION_NAME));
    }

    @BeforeEach
    void beforeEach() {
        getSessionFactoryScope().inTransaction(session -> {
            session.persist(ITEM);
        });
    }

    @Test
    void testCurrentDate() {
        assertQueryResult(
                "select current_date from Item",
                new Date(Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli()),
                "{\"a\":1}");
    }

    @Test
    void testCurrentTimestamp() {
        assertQueryResult(
                "select current_timestamp from Item",
                Timestamp.class,
                now -> Assertions.assertEquals(
                        1,
                        Duration.between(now.toInstant(), Instant.now())
                                .abs()
                                .compareTo(Duration.of(1, ChronoUnit.SECONDS))),
                "{\"a\":1}");
    }

    @Test
    void testCurrentTime() {
        var localTime = Instant.now().atZone(UTC).toLocalTime();
        var target = ZonedDateTime.of(LocalDate.EPOCH, localTime, UTC).toInstant();
        assertQueryResult(
                "select current_time from Item",
                Time.class,
                result -> Assertions.assertEquals(
                        1,
                        Duration.between(target, result.toInstant())
                                .abs()
                                .compareTo(Duration.of(1, ChronoUnit.SECONDS))),
                "{\"a\":1}");
    }

    @Test
    void testTimestampDiffSecond() {
        assertQueryResult(
                "select timestampdiff(second, before, after) from Item",
                Duration.between(ITEM.before, ITEM.after).get(ChronoUnit.SECONDS),
                "{\"a\":1}");
    }

    @Test
    void testTimestampDiffMinute() {
        assertQueryResult(
                "select timestampdiff(minute, before, after) from Item",
                ChronoUnit.MINUTES.between(ITEM.before.atZone(UTC), ITEM.after.atZone(UTC)),
                "{\"a\":1}");
    }

    @Test
    void testTimestampDiffHour() {
        assertQueryResult(
                "select timestampdiff(hour, before, after) from Item",
                ChronoUnit.HOURS.between(ITEM.before.atZone(UTC), ITEM.after.atZone(UTC)),
                "{\"a\":1}");
    }

    @Test
    void testTimestampDiffDay() {
        assertQueryResult(
                "select timestampdiff(day, before, after) from Item",
                ChronoUnit.DAYS.between(ITEM.before.atZone(UTC), ITEM.after.atZone(UTC)),
                "{\"a\":1}");
    }

    @Test
    void testTimestampDiffWeek() {
        assertQueryResult(
                "select timestampdiff(week, before, after) from Item",
                ChronoUnit.WEEKS.between(ITEM.before.atZone(UTC), ITEM.after.atZone(UTC)),
                "{\"a\":1}");
    }

    @Test
    void testTimestampDiffMonth() {
        assertQueryResult(
                "select timestampdiff(month, before, after) from Item",
                ChronoUnit.MONTHS.between(ITEM.before.atZone(UTC), ITEM.after.atZone(UTC)),
                "{\"a\":1}");
    }

    @Test
    void testTimestampDiffYear() {
        assertQueryResult(
                "select timestampdiff(quarter, before, after) from Item",
                ChronoUnit.YEARS.between(ITEM.before.atZone(UTC), ITEM.after.atZone(UTC)),
                "{\"a\":1}");
    }

    @Test
    void testFormat() {
        assertQueryResult(
                "select format(before, '%Y-%m-%dT%H:%M:%S.%L') from Item",
                ITEM.before.atZone(UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), "{\"a\":1}");
    }

    @Test
    void testTimestampAddMinutes() {
        assertQueryResult("select before + 10 minute from Item", ITEM.before.plus(10, ChronoUnit.MINUTES), "{\"a\":1}");
    }

    @Test
    void testExtractSecond() {
        assertQueryResult(
                "select extract(second from before) from Item",
                (float) ITEM.before.atZone(UTC).getSecond(),
                "{\"a\":1}");
    }

    @Test
    void testExtractMinute() {
        assertQueryResult(
                "select extract(minute from before) from Item",
                ITEM.before.atZone(UTC).getMinute(),
                "{\"a\":1}");
    }

    @Test
    void testExtractHour() {
        assertQueryResult(
                "select extract(hour from before) from Item",
                ITEM.before.atZone(UTC).getHour(),
                "{\"a\":1}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"day", "day of month"})
    void testExtractDay(String unit) {
        assertQueryResult(
                "select extract(%s from before) from Item".formatted(unit),
                ITEM.before.atZone(UTC).getDayOfMonth(),
                "{\"a\":1}");
    }

    @Test
    void testExtractMonth() {
        assertQueryResult(
                "select extract(month from before) from Item",
                ITEM.before.atZone(UTC).getMonthValue(),
                "{\"a\":1}");
    }

    @Test
    void testExtractYear() {
        assertQueryResult(
                "select extract(year from before) from Item",
                ITEM.before.atZone(UTC).getYear(),
                "{\"a\":1}");
    }

    @Test
    void testExtractQuarter() {
        assertQueryResult(
                "select extract(quarter from before) from Item",
                ITEM.before.atZone(UTC).get(IsoFields.QUARTER_OF_YEAR),
                "{\"a\":1}");
    }

    @Test
    void testExtractWeek() {
        assertQueryResult(
                "select extract(week of year from before) from Item",
                ITEM.before.atZone(UTC).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                "{\"a\":1}");
    }

    @Test
    void testExtractWeekOfMonth() {
        assertQueryResult(
                "select extract(week of month from before) from Item",
                ITEM.before.atZone(UTC).get(ChronoField.ALIGNED_WEEK_OF_MONTH),
                "{\"a\":1}");
    }

    @Test
    void testExtractDayOfWeek() {
        assertQueryResult(
                "select extract(day of week from before) from Item",
                ITEM.before.atZone(UTC).get(ChronoField.DAY_OF_WEEK),
                "{\"a\":1}");
    }

    @Test
    void testExtractDayOfYear() {
        assertQueryResult(
                "select extract(day of year from before) from Item",
                ITEM.before.atZone(UTC).getDayOfYear(),
                "{\"a\":1}");
    }

    @Test
    void testExtractEpoch() {
        assertQueryResult("select extract(epoch from before) from Item", ITEM.before.getEpochSecond(), "{\"a\":1}");
    }

    @Test
    void testExtractNanosecond() {
        assertQueryResult(
                "select extract(nanosecond from before) from Item",
                (long) ITEM.before.atZone(UTC).getNano(),
                "{\"a\":1}");
    }

    @Test
    void testExtractTime() {
        assertQueryResult(
                "select extract(time from before) from Item",
                ITEM.before.atZone(UTC).toLocalTime(),
                "{\"a\":1}");
    }

    @Nested
    class Unsupported implements MongoServiceRegistryProducer {

        @Test
        void testExtractOffset() {
            assertSelectQueryFailure(
                    "select extract(offset from before) from Item",
                    ZoneOffset.class,
                    FeatureNotSupportedException.class,
                    "Time unit offset not supported");
        }

        @Test
        void testExtractTimeZoneHour() {
            assertSelectQueryFailure(
                    "select extract(timezone_hour from before) from Item",
                    Integer.class,
                    FeatureNotSupportedException.class,
                    "Time unit timezone_hour not supported");
        }

        @Test
        void testExtractTimeZoneMinute() {
            assertSelectQueryFailure(
                    "select extract(timezone_minute from before) from Item",
                    Integer.class,
                    FeatureNotSupportedException.class,
                    "Time unit timezone_minute not supported");
        }
    }

    @Entity(name = "Item")
    @Table(name = COLLECTION_NAME)
    static class Item {
        @Id
        int id;

        Instant before;
        Instant after;

        Item() {}

        Item(int id, Instant before, Instant after) {
            this.id = id;
            this.before = before;
            this.after = after;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            return id == item.id && Objects.equals(before, item.before) && Objects.equals(after, item.after);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, before, after);
        }

        @Override
        public String toString() {
            return "Item{" + "id=" + id + ", s='" + before + '\'' + ", u='" + after + '\'' + '}';
        }
    }
}
