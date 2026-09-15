package demo.springcore;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TraceAspect {
    @Around("execution(* demo.springcore.ProductService.names(..))")
    public Object trace(ProceedingJoinPoint call) throws Throwable {
        System.out.println("进入 names");
        try {
            return call.proceed();
        } finally {
            System.out.println("离开 names");
        }
    }
}
