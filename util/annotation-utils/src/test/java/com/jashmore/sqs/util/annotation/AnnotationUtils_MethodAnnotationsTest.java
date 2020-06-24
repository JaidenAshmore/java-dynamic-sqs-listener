package com.jashmore.sqs.util.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;

import com.jashmore.sqs.util.ProxyMethodInterceptor;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Optional;

@SuppressWarnings("checkstyle:TypeName")
class AnnotationUtils_MethodAnnotationsTest {
    @Test
    void methodAnnotationsCanBeFoundOnBaseClasses() throws Exception {
        // arrange
        final MyClass object = new MyClass();

        // act
        final Optional<MethodAnnotation> annotation = AnnotationUtils.findMethodAnnotation(
                object.getClass().getMethod("methodWithAnnotation"), MethodAnnotation.class);

        // assert
        assertThat(annotation).isPresent();
    }

    @Test
    void methodAnnotationsNotFoundOnBaseClassesReturnsEmptyOptional() throws Exception {
        // arrange
        final MyClass object = new MyClass();

        // act
        final Optional<MethodAnnotation> annotation = AnnotationUtils.findMethodAnnotation(
                object.getClass().getMethod("methodWithoutAnnotation"), MethodAnnotation.class);

        // assert
        assertThat(annotation).isEmpty();
    }

    @Test
    void methodAnnotationsForExtendedClassMethodCanBeFound() throws Exception {
        // arrange
        final ExtendedMyClass object = new ExtendedMyClass();

        // act
        final Optional<MethodAnnotation> annotation = AnnotationUtils.findMethodAnnotation(
                object.getClass().getMethod("methodWithAnnotation"), MethodAnnotation.class);

        // assert
        assertThat(annotation).isPresent();
    }

    @Test
    void methodAnnotationsForExtendedClassMethodNotFoundOnBaseClassReturnsEmptyOptional() throws Exception {
        // arrange
        final ExtendedMyClass object = new ExtendedMyClass();

        // act
        final Optional<MethodAnnotation> annotation = AnnotationUtils.findMethodAnnotation(
                object.getClass().getMethod("otherMethodWithoutAnnotation", String.class), MethodAnnotation.class);

        // assert
        assertThat(annotation).isEmpty();
    }

    @Test
    void methodAnnotationsForExtendedClassMethodWithAnnotationReturnsAnnotation() throws Exception {
        // arrange
        final ExtendedMyClass object = new ExtendedMyClass();

        // act
        final Optional<MethodAnnotation> annotation = AnnotationUtils.findMethodAnnotation(
                object.getClass().getMethod("otherMethodWithAnnotation", String.class), MethodAnnotation.class);

        // assert
        assertThat(annotation).isPresent();
    }

    @Test
    void methodAnnotationForExtendedClassMethodWillReturnEmptyOptionalIfWhenNoMethodAnnotation() throws Exception {
        // arrange
        final ExtendedMyClass object = new ExtendedMyClass();

        // act
        final Optional<MethodAnnotation> annotation = AnnotationUtils.findMethodAnnotation(
                object.getClass().getMethod("methodWithoutAnnotation"), MethodAnnotation.class);

        // assert
        assertThat(annotation).isEmpty();
    }

    @Test
    void methodAnnotationsForExtendedClassWithOverriddenMethodCanBeFound() throws Exception {
        // arrange
        final ExtendedMyClassWithOverride object = new ExtendedMyClassWithOverride();

        // act
        final Optional<MethodAnnotation> annotation = AnnotationUtils.findMethodAnnotation(
                object.getClass().getMethod("methodWithAnnotation"), MethodAnnotation.class);

        // assert
        assertThat(annotation).isPresent();
    }

    @Test
    void methodAnnotationsForExtendedClassWithOverriddenMethodWithoutAnnotationReturnsEmptyOptional() throws Exception {
        // arrange
        final ExtendedMyClassWithOverride object = new ExtendedMyClassWithOverride();

        // act
        final Optional<MethodAnnotation> annotation = AnnotationUtils.findMethodAnnotation(
                object.getClass().getMethod("methodWithoutAnnotation"), MethodAnnotation.class);

        // assert
        assertThat(annotation).isEmpty();
    }

    @Test
    void classExtendedByCgLibCanFindAnnotationOnBaseClass() throws Exception {
        // arrange
        final MyClass object = new MyClass();
        final MyClass proxyObject = ProxyMethodInterceptor.wrapObject(object, MyClass.class);

        // act
        final Optional<MethodAnnotation> annotation = AnnotationUtils.findMethodAnnotation(
                proxyObject.getClass().getMethod("methodWithAnnotation"), MethodAnnotation.class);

        // assert
        assertThat(annotation).isPresent();
    }

    @Test
    void classExtendedByCgLibForBaseClassWithNoMethodAnnotationReturnsEmptyOptional() throws Exception {
        // arrange
        final MyClass object = new MyClass();
        final MyClass proxyObject = ProxyMethodInterceptor.wrapObject(object, MyClass.class);

        // act
        final Optional<MethodAnnotation> annotation = AnnotationUtils.findMethodAnnotation(
                proxyObject.getClass().getMethod("methodWithoutAnnotation"), MethodAnnotation.class);

        // assert
        assertThat(annotation).isEmpty();
    }

    @Test
    void multipleLevelsOfProxyingCanStillFindOriginalClassesMethodAnnotations() throws Exception {
        // arrange
        final MyClass object = new MyClass();
        final MyClass proxyObject = ProxyMethodInterceptor.wrapObject(object, MyClass.class);
        final MyClass secondLevelProxyObject = ProxyMethodInterceptor.wrapObject(proxyObject, MyClass.class);

        // act
        final Optional<MethodAnnotation> annotation = AnnotationUtils.findMethodAnnotation(
                secondLevelProxyObject.getClass().getMethod("methodWithAnnotation"), MethodAnnotation.class);

        // assert
        assertThat(annotation).isPresent();
    }


    @Test
    void multipleLevelsOfProxyingForMethodWithNoAnnotationReturnsEmptyOptional() throws Exception {
        // arrange
        final MyClass object = new MyClass();
        final MyClass proxyObject = ProxyMethodInterceptor.wrapObject(object, MyClass.class);
        final MyClass secondLevelProxyObject = ProxyMethodInterceptor.wrapObject(proxyObject, MyClass.class);

        // act
        final Optional<MethodAnnotation> annotation = AnnotationUtils.findMethodAnnotation(
                secondLevelProxyObject.getClass().getMethod("methodWithoutAnnotation"), MethodAnnotation.class);

        // assert
        assertThat(annotation).isEmpty();
    }

    @Test
    void multipleLevelsOfProxyingAndClassExtensionCanStillFindOriginalClassesMethodAnnotations() throws Exception {
        // arrange
        final ExtendedMyClassWithOverride object = new ExtendedMyClassWithOverride();
        final ExtendedMyClassWithOverride proxyObject = ProxyMethodInterceptor.wrapObject(object, ExtendedMyClassWithOverride.class);
        final ExtendedMyClassWithOverride secondLevelProxyObject = ProxyMethodInterceptor.wrapObject(proxyObject, ExtendedMyClassWithOverride.class);

        // act
        final Optional<MethodAnnotation> annotation = AnnotationUtils.findMethodAnnotation(
                secondLevelProxyObject.getClass().getMethod("methodWithAnnotation"), MethodAnnotation.class);

        // assert
        assertThat(annotation).isPresent();
    }

    @Test
    void multipleLevelsOfProxyingAndClassExtensionForMethodWithoutAnnotationReturnsEmptyOptional() throws Exception {
        // arrange
        final ExtendedMyClassWithOverride object = new ExtendedMyClassWithOverride();
        final ExtendedMyClassWithOverride proxyObject = ProxyMethodInterceptor.wrapObject(object, ExtendedMyClassWithOverride.class);
        final ExtendedMyClassWithOverride secondLevelProxyObject = ProxyMethodInterceptor.wrapObject(proxyObject, ExtendedMyClassWithOverride.class);

        // act
        final Optional<MethodAnnotation> annotation = AnnotationUtils.findMethodAnnotation(
                secondLevelProxyObject.getClass().getMethod("methodWithoutAnnotation"), MethodAnnotation.class);

        // assert
        assertThat(annotation).isEmpty();
    }

    static class MyClass {
        @MethodAnnotation
        public void methodWithAnnotation() {

        }

        public void methodWithoutAnnotation() {

        }
    }

    @SuppressWarnings( {"WeakerAccess", "unused"})
    private static class ExtendedMyClass extends MyClass {
        @MethodAnnotation
        public void otherMethodWithAnnotation(final String payload) {

        }

        public void otherMethodWithoutAnnotation(final String payload) {

        }
    }

    static class ExtendedMyClassWithOverride extends MyClass {
        @Override
        public void methodWithAnnotation() {
            super.methodWithAnnotation();
        }

        @Override
        public void methodWithoutAnnotation() {
            super.methodWithoutAnnotation();
        }
    }

    @Retention(RUNTIME)
    @Target(METHOD)
    @interface MethodAnnotation {

    }
}