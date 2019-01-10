package com.groupstp.googleoauth.web;

import com.haulmont.cuba.core.sys.servlet.ServletRegistrationManager;
import com.haulmont.cuba.core.sys.servlet.events.ServletContextInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;

/**
 * Application startup servlets/filters initializer
 *
 * @author adiatullin
 */
@Component
public class WebInitializer {
    @Inject
    private ServletRegistrationManager servletRegistrationManager;

    @EventListener
    public void initialize(ServletContextInitializedEvent e) {
        Filter filter = servletRegistrationManager.createFilter(e.getApplicationContext(), "com.groupstp.googleoauth.web.GoogleAuthenticationFilter");
        FilterRegistration.Dynamic dynamic = e.getSource().addFilter("google", filter);
        dynamic.setAsyncSupported(true);
        dynamic.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, GoogleAuthenticationFilter.URL_PATH);
    }
}
