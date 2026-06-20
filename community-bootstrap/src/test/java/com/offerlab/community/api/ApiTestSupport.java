package com.offerlab.community.api;

import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.infra.web.handler.GlobalExceptionHandler;
import com.offerlab.community.infra.web.interceptor.AuthInterceptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

final class ApiTestSupport {
    private ApiTestSupport() {
    }

    static MockMvc mvc(Object controller, JwtService jwtService) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addInterceptors(new AuthInterceptor(jwtService))
                .build();
    }
}
