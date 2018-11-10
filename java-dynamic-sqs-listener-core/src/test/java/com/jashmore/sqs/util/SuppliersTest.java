package com.jashmore.sqs.util;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.function.Supplier;

public class SuppliersTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Supplier<Integer> mockSupplier;

    @Test(expected = IllegalArgumentException.class)
    public void illegalArgumentExceptionThrownWhenTimeoutIsNegative() {
        // act
        Suppliers.memoize(-1000, () -> 1);
    }

    @Test(expected = NullPointerException.class)
    public void nullPointerExceptionThrownWhenSupplierIsNull() {
        // act
        Suppliers.memoize(1, null);
    }

    @Test
    public void valueIsCachedOverSubsequentCalls() {
        // arrange
        when(mockSupplier.get()).thenReturn(1);

        // act
        final Supplier<Integer> cachedSupplier = Suppliers.memoize(10000, mockSupplier);
        cachedSupplier.get();
        cachedSupplier.get();
        cachedSupplier.get();

        // assert
        verify(mockSupplier, times(1)).get();
    }

    @Test
    public void cachedValueIsResetAfterTimeout() throws InterruptedException {
        // arrange
        when(mockSupplier.get()).thenReturn(1);

        // act
        final Supplier<Integer> cachedSupplier = Suppliers.memoize(500, mockSupplier);
        cachedSupplier.get();
        cachedSupplier.get();
        Thread.sleep(501);
        cachedSupplier.get();

        // assert
        verify(mockSupplier, times(2)).get();
    }
}
