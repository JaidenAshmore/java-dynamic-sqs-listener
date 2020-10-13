package com.jashmore.sqs.util;

import java.lang.reflect.Method;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

/**
 * Interceptor used to test a method being proxied via cglib.
 *
 * @param <T> the type of the original object to proxy
 */
public class ProxyMethodInterceptor<T> implements MethodInterceptor {

    private final T original;

    private ProxyMethodInterceptor(final T original) {
        this.original = original;
    }

    @Override
    public Object intercept(Object object, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
        System.out.println("BEFORE");
        method.invoke(original, args);
        System.out.println("AFTER");
        return null;
    }

    /**
     * Wrap the provided object so that it has before and after log messages.
     *
     * @param original the original object to wrap
     * @param clazz    the class of the object to wrap
     * @param <S>      the type parameter for the class
     * @return the wrapped object
     */
    @SuppressWarnings("unchecked")
    public static <S> S wrapObject(final S original, final Class<S> clazz) {
        return (S) Enhancer.create(clazz, new ProxyMethodInterceptor<>(original));
    }
}
