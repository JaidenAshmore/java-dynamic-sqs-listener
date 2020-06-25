package com.amazonaws.xray.spring.aop;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Subsegment;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Pointcut;

import java.util.Map;


/**
 * Allows for use of this library without Spring Data JPA being in the classpath.
 * For projects using Spring Data JPA, consider using {@link AbstractXRayInterceptor} instead.
 */
@SuppressWarnings({"unused", "deprecation"})
public abstract class BaseAbstractXRayInterceptor {

    @Around("xrayTracedClasses() || xrayEnabledClasses()")
    public Object traceAroundMethods(ProceedingJoinPoint pjp) throws Throwable {
        return this.processXRayTrace(pjp);
    }

    protected Object processXRayTrace(ProceedingJoinPoint pjp) throws Throwable {
        try {
            Subsegment subsegment = AWSXRay.beginSubsegment(pjp.getSignature().getName());
            if (subsegment != null) {
                subsegment.setMetadata(generateMetadata(pjp, subsegment));
            }
            return XRayInterceptorUtils.conditionalProceed(pjp);
        } catch (Exception exception) {
            AWSXRay.getCurrentSegment().addException(exception);
            throw exception;
        } finally {
            AWSXRay.endSubsegment();
        }
    }

    protected abstract void xrayEnabledClasses();

    @Pointcut("execution(* XRayTraced+.*(..))")
    protected void xrayTracedClasses() {
    }

    protected Map<String, Map<String, Object>> generateMetadata(ProceedingJoinPoint pjp, Subsegment subsegment) {
        return XRayInterceptorUtils.generateMetadata(pjp, subsegment);
    }
}