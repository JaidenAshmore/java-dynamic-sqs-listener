package com.jashmore.sqs.util.annotation;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.argument.DefaultMethodParameter;
import com.jashmore.sqs.argument.MethodParameter;
import com.jashmore.sqs.util.ProxyMethodInterceptor;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Optional;

@SuppressWarnings("checkstyle:TypeName")
public class AnnotationUtils_ParameterAnnotationsTest {
    @Test
    public void parameterAnnotationsCanBeFoundOnBaseClasses() {
        // arrange
        final MyClass object = new MyClass();

        // act
        final Optional<ParameterAnnotation> annotation = AnnotationUtils.findParameterAnnotation(
                getMethodParameterWithAnnotation(object), ParameterAnnotation.class);

        // assert
        assertThat(annotation).isPresent();
    }

    @Test
    public void parameterAnnotationNotExistingOnBaseClassReturnsEmptyOptional() {
        // arrange
        final MyClass object = new MyClass();

        // act
        final Optional<ParameterAnnotation> annotation = AnnotationUtils.findParameterAnnotation(
                getMethodParameterWithoutAnnotation(object), ParameterAnnotation.class);

        // assert
        assertThat(annotation).isEmpty();
    }

    @Test
    public void parameterAnnotationsForExtendedClassMethodsCanBeFound() {
        // arrange
        final ExtendedMyClass object = new ExtendedMyClass();

        // act
        final Optional<ParameterAnnotation> annotation = AnnotationUtils.findParameterAnnotation(
                getMethodParameterWithAnnotation(object), ParameterAnnotation.class);

        // assert
        assertThat(annotation).isPresent();
    }

    @Test
    public void parameterAnnotationsForExtendedClassMethodWithoutParameterAnnotationReturnsEmptyOptional() {
        // arrange
        final ExtendedMyClass object = new ExtendedMyClass();

        // act
        final Optional<ParameterAnnotation> annotation = AnnotationUtils.findParameterAnnotation(
                getMethodParameterWithoutAnnotation(object), ParameterAnnotation.class);

        // assert
        assertThat(annotation).isEmpty();
    }

    @Test
    public void parameterAnnotationsForExtendedClassWithOverriddenMethodCanBeFound() {
        // arrange
        final ExtendedMyClassWithOverride object = new ExtendedMyClassWithOverride();

        // act
        final Optional<ParameterAnnotation> annotation = AnnotationUtils.findParameterAnnotation(
                getMethodParameterWithAnnotation(object), ParameterAnnotation.class);

        // assert
        assertThat(annotation).isPresent();
    }

    @Test
    public void parameterAnnotationsForExtendedClassWithOverriddenMethodWithoutAnnotationReturnsEmptyOptional() {
        // arrange
        final ExtendedMyClassWithOverride object = new ExtendedMyClassWithOverride();

        // act
        final Optional<ParameterAnnotation> annotation = AnnotationUtils.findParameterAnnotation(
                getMethodParameterWithoutAnnotation(object), ParameterAnnotation.class);

        // assert
        assertThat(annotation).isEmpty();
    }

    @Test
    public void classExtendedByCgLibCanFindParameterAnnotationOnBaseClass() {
        // arrange
        final MyClass object = new MyClass();
        final MyClass proxyObject = ProxyMethodInterceptor.wrapObject(object, MyClass.class);

        // act
        final Optional<ParameterAnnotation> annotation = AnnotationUtils.findParameterAnnotation(
                getMethodParameterWithAnnotation(proxyObject), ParameterAnnotation.class);

        // assert
        assertThat(annotation).isPresent();
    }

    @Test
    public void classExtendedByCgLibForBaseClassWithNoParameterAnnotationReturnsEmptyOptional() {
        // arrange
        final MyClass object = new MyClass();
        final MyClass proxyObject = ProxyMethodInterceptor.wrapObject(object, MyClass.class);

        // act
        final Optional<ParameterAnnotation> annotation = AnnotationUtils.findParameterAnnotation(
                getMethodParameterWithoutAnnotation(proxyObject), ParameterAnnotation.class);

        // assert
        assertThat(annotation).isEmpty();
    }

    @Test
    public void multipleLevelsOfProxyingCanStillFindOriginalClassesParameterAnnotations() {
        // arrange
        final MyClass object = new MyClass();
        final MyClass proxyObject = ProxyMethodInterceptor.wrapObject(object, MyClass.class);
        final MyClass secondLevelProxyObject = ProxyMethodInterceptor.wrapObject(proxyObject, MyClass.class);

        // act
        final Optional<ParameterAnnotation> annotation = AnnotationUtils.findParameterAnnotation(
                getMethodParameterWithAnnotation(secondLevelProxyObject), ParameterAnnotation.class);

        // assert
        assertThat(annotation).isPresent();
    }


    @Test
    public void multipleLevelsOfProxyingForMethodWithNoParameterAnnotationReturnsEmptyOptional() {
        // arrange
        final MyClass object = new MyClass();
        final MyClass proxyObject = ProxyMethodInterceptor.wrapObject(object, MyClass.class);
        final MyClass secondLevelProxyObject = ProxyMethodInterceptor.wrapObject(proxyObject, MyClass.class);

        // act
        final Optional<ParameterAnnotation> annotation = AnnotationUtils.findParameterAnnotation(
                getMethodParameterWithoutAnnotation(secondLevelProxyObject), ParameterAnnotation.class);

        // assert
        assertThat(annotation).isEmpty();
    }

    @Test
    public void multipleLevelsOfProxyingAndClassExtensionCanStillFindOriginalClassesParameterAnnotations() {
        // arrange
        final ExtendedMyClassWithOverride object = new ExtendedMyClassWithOverride();
        final ExtendedMyClassWithOverride proxyObject = ProxyMethodInterceptor.wrapObject(object, ExtendedMyClassWithOverride.class);
        final ExtendedMyClassWithOverride secondLevelProxyObject = ProxyMethodInterceptor.wrapObject(proxyObject, ExtendedMyClassWithOverride.class);

        // act
        final Optional<ParameterAnnotation> annotation = AnnotationUtils.findParameterAnnotation(
                getMethodParameterWithAnnotation(secondLevelProxyObject), ParameterAnnotation.class);

        // assert
        assertThat(annotation).isPresent();
    }

    @Test
    public void multipleLevelsOfProxyingAndClassExtensionForMethodWithoutParameterAnnotationReturnsEmptyOptional() {
        // arrange
        final ExtendedMyClassWithOverride object = new ExtendedMyClassWithOverride();
        final ExtendedMyClassWithOverride proxyObject = ProxyMethodInterceptor.wrapObject(object, ExtendedMyClassWithOverride.class);
        final ExtendedMyClassWithOverride secondLevelProxyObject = ProxyMethodInterceptor.wrapObject(proxyObject, ExtendedMyClassWithOverride.class);

        // act
        final Optional<ParameterAnnotation> annotation = AnnotationUtils.findParameterAnnotation(
                getMethodParameterWithoutAnnotation(secondLevelProxyObject), ParameterAnnotation.class);

        // assert
        assertThat(annotation).isEmpty();
    }

    private <T extends MyClass> MethodParameter getMethodParameterWithAnnotation(final T object) {
        return getMethodParameter(object, 0);
    }

    private <T extends MyClass> MethodParameter getMethodParameterWithoutAnnotation(final T object) {
        return getMethodParameter(object, 1);
    }

    private <T extends MyClass> MethodParameter getMethodParameter(final T object, int parameterIndex) {
        final Method method;
        try {
            method = object.getClass().getMethod("method", String.class, String.class);
        } catch (final NoSuchMethodException noSuchMethodException) {
            throw new RuntimeException(noSuchMethodException);
        }

        return DefaultMethodParameter.builder()
                .method(method)
                .parameter(method.getParameters()[parameterIndex])
                .parameterIndex(parameterIndex)
                .build();
    }

    static class MyClass {
        public void method(@ParameterAnnotation final String parameter, final String secondParameter) {

        }
    }

    private static class ExtendedMyClass extends MyClass {

    }

    static class ExtendedMyClassWithOverride extends MyClass {
        @Override
        public void method(final String parameter, final String secondParameter) {
            super.method(parameter, secondParameter);
        }
    }

    @Retention(RUNTIME)
    @Target(PARAMETER)
    @interface ParameterAnnotation {

    }
}