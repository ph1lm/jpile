package com.opower.persistence.jpile.loader;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.mysql.jdbc.Statement;
import com.opower.persistence.jpile.infile.InfileDataBuffer;
import com.opower.persistence.jpile.reflection.PersistenceAnnotationInspector;
import com.opower.persistence.jpile.sample.Customer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the builder for Single Infile Object Loader
 *
 * @author amir.raminfar
 */
@RunWith(MockitoJUnitRunner.class)
public class SingleInfileObjectLoaderBuilderTest {
    @Mock
    Connection connection;

    @Mock
    Statement statement;

    SingleInfileObjectLoader<Customer> objectLoader;

    private static final Long AUTO_GENERATED_ID = 123L;

    @Before
    public void setUp() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.first()).thenReturn(true);
        when(resultSet.getLong(1)).thenReturn(AUTO_GENERATED_ID);

        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        when(connection.createStatement()).thenReturn(statement);

        objectLoader = new SingleInfileObjectLoaderBuilder<>(Customer.class)
                .withDefaultTableName()
                .withJdbcConnection(connection)
                .usingAnnotationInspector(new PersistenceAnnotationInspector())
                .withBuffer(new InfileDataBuffer())
                .build();
    }

    @Test
    public void testBuildingCustomer() throws Exception {
        assertEquals(ImmutableSet.of("id", "last_seen_on", "type"), objectLoader.getMappings().keySet());
        assertEquals(ImmutableMap.<Method, SingleInfileObjectLoader<Object>>of(), objectLoader.getEmbeds());
        assertEquals(ImmutableList.of(), objectLoader.getWarnings());
        assertTrue(objectLoader.isAutoGenerateId());
        assertEquals(objectLoader.autoGeneratedId, AUTO_GENERATED_ID.longValue());
    }

    @Test
    public void testAddingCustomer() throws Exception {
        Customer customer = new Customer();
        objectLoader.add(customer);
        assertNotNull(customer.getId());
        assertEquals("124\t\\N\t\\N",
                CharStreams.toString(new InputStreamReader(objectLoader.getInfileDataBuffer().asInputStream())
        ));
    }

    @Test
    public void testFlush() throws Exception {
        Customer customer = new Customer();
        objectLoader.add(customer);
        objectLoader.flush();

        verify(connection, times(2)).createStatement();
        verify(statement).execute(anyString());
    }

    /**
     * An enum used for testing.
     */
    private enum TestType {
        A;

        /**
         * @return something that is different from {@link #name()} to verify that this method is not used when getting
         * the value
         */
        @Override
        public String toString() {
            return "Something that is NOT the same as name()";
        }
    }

    /**
     * Verify that when the method is not annotated with {@link Enumerated}, the {@link Enum#ordinal()} is used.
     */
    @Test
    public void testGetEnumValueToAppendWithNoAnnotation() throws Exception {
        TestType enumObject = TestType.A;

        /** Test class */
        class TestClass {
            public void getEnum() {}
        }

        assertEquals("Enum value to append", enumObject.ordinal(),
                objectLoader.getEnumValueToAppend(TestClass.class.getMethod("getEnum"), enumObject));
    }

    /**
     * Verify that when the method is annotated with {@link Enumerated} (but no type is specified), the
     * {@link Enum#ordinal()} is used.
     */
    @Test
    public void testGetEnumValueToAppendWithDefaultOrdinal() throws Exception {
        TestType enumObject = TestType.A;

        /** Test class */
        class TestClass {
            @Enumerated
            public void getEnum() {}
        }

        assertEquals("Enum value to append", enumObject.ordinal(),
                objectLoader.getEnumValueToAppend(TestClass.class.getMethod("getEnum"), enumObject));
    }

    /**
     * Verify that when the method is annotated with {@link Enumerated} and {@link EnumType#ORDINAL} is specified, the
     * {@link Enum#ordinal()} is used.
     */
    @Test
    public void testGetEnumValueToAppendWithExplicitOrdinal() throws Exception {
        TestType enumObject = TestType.A;

        /** Test class */
        class TestClass {
            @Enumerated(EnumType.ORDINAL)
            public void getEnum() {}
        }

        assertEquals("Enum value to append", enumObject.ordinal(),
                objectLoader.getEnumValueToAppend(TestClass.class.getMethod("getEnum"), enumObject));
    }

    /**
     * Verify that when the method is annotated with {@link Enumerated} and {@link EnumType#STRING} is specified, the
     * {@link Enum#name()} is used.
     */
    @Test
    public void testGetEnumValueToAppendWithString() throws Exception {
        TestType enumObject = TestType.A;

        /** Test class */
        class TestClass {
            @Enumerated(EnumType.STRING)
            public void getEnum() {}
        }

        assertEquals("Enum value to append", enumObject.name(),
                objectLoader.getEnumValueToAppend(TestClass.class.getMethod("getEnum"), enumObject));
    }
}
