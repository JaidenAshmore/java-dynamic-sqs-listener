package com.jashmore.sqs.util;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

public class ProxyMethodInterceptor<T> implements MethodInterceptor {
    private final T original;

    public ProxyMethodInterceptor(final T original) {
        this.original = original;
    }

    public Object intercept(Object o, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
        System.out.println("BEFORE");
        method.invoke(original, args);
        System.out.println("AFTER");
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <S> S wrapObject(final S original, final Class<S> clazz) {
        return (S) Enhancer.create(clazz, new ProxyMethodInterceptor<>(original));
    }
}
