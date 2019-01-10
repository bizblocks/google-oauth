package com.groupstp.googleoauth.web;

import com.groupstp.googleoauth.data.GoogleUserData;
import com.groupstp.googleoauth.data.OAuth2ResponseType;
import com.groupstp.googleoauth.restapi.GoogleAuthenticationController;
import com.haulmont.cuba.core.global.GlobalConfig;
import com.haulmont.cuba.core.sys.AppContext;
import com.haulmont.cuba.core.sys.SecurityContext;
import com.haulmont.cuba.security.entity.User;
import com.haulmont.cuba.security.global.LoginException;
import com.haulmont.cuba.security.global.UserSession;
import com.haulmont.cuba.web.auth.WebAuthConfig;
import com.haulmont.restapi.auth.OAuthTokenIssuer;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.RequestContextFilter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Collections;
import java.util.function.Supplier;

/**
 * Google authentication server filter
 *
 * @author adiatullin
 */
public class GoogleAuthenticationFilter extends RequestContextFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(GoogleAuthenticationFilter.class);

    public static final String URL_PATH = "/google/login";

    private static final String REDIRECT_URI_ATTRIBUTE = "cuba.google.redirect";
    private static final String PARAMETER_CODE = "code";
    private static final String PARAMETER_REDIRECT_URI = "redirect_uri";

    @Override
    protected void doFilterInternal(HttpServletRequest httpRequest, HttpServletResponse httpResponse, FilterChain filterChain) throws ServletException, IOException {
        super.doFilterInternal(httpRequest, httpResponse, (req, res) -> {
            HttpSession httpSession = httpRequest.getSession();

            final String code = httpRequest.getParameter(PARAMETER_CODE);
            if (!StringUtils.isEmpty(code)) {
                String uri = (String) httpSession.getAttribute(REDIRECT_URI_ATTRIBUTE);
                if (StringUtils.isEmpty(uri)) {
                    log.error("Missing redirect uri from session");
                    throw new ServletException("Redirect uri not found");
                }

                User user;
                try {
                    user = getAsPrivilegedUser(() -> {
                        GoogleUserData userData = GoogleAuthenticationController.get().getGoogleService().getUserData(getRedirectUrl(), code);
                        if (userData == null) {
                            throw new RuntimeException("User data not found");
                        }
                        User currentUser = GoogleAuthenticationController.get().getSocialRegistrationService().findUser(userData);
                        if (currentUser == null) {
                            throw new RuntimeException("User not found with email: " + userData.getEmail());
                        }
                        return currentUser;
                    });
                } catch (Exception e) {
                    log.error("Failed to process google user data", e);
                    throw new ServletException("Failed to process google user data", e);
                }

                try {
                    URIBuilder builder = new URIBuilder(uri);

                    OAuthTokenIssuer.OAuth2AccessTokenResult tokenResult = GoogleAuthenticationController.get().getOAuthTokenIssuer().issueToken(user.getLogin(),
                            GoogleAuthenticationController.get().getMessageTools().getDefaultLocale(), Collections.emptyMap());

                    uri = builder.addParameter(PARAMETER_CODE, tokenResult.getAccessToken().getValue())
                            .build()
                            .toString();
                    httpResponse.setStatus(HttpStatus.FOUND.value());
                    httpResponse.sendRedirect(uri);
                } catch (Exception e) {
                    log.error("Processing failed", e);
                    throw new ServletException("Processing failed", e);
                }
                return;
            }

            String uri = httpRequest.getParameter(PARAMETER_REDIRECT_URI);
            if (!StringUtils.isEmpty(uri)) {
                String loginUrl = getAsPrivilegedUser(() -> GoogleAuthenticationController.get().getGoogleService().getLoginUrl(getRedirectUrl(), OAuth2ResponseType.CODE_TOKEN));
                httpSession.setAttribute(REDIRECT_URI_ATTRIBUTE, uri);
                httpResponse.setStatus(HttpStatus.FOUND.value());
                httpResponse.sendRedirect(loginUrl);
            }
        });
    }

    private <T> T getAsPrivilegedUser(Supplier<T> supplier) {
        UserSession session;
        try {
            WebAuthConfig webAuthConfig = GoogleAuthenticationController.get().getConfiguration().getConfig(WebAuthConfig.class);
            session = GoogleAuthenticationController.get().getTrustedClientService().getSystemSession(webAuthConfig.getTrustedClientPassword());
        } catch (LoginException e) {
            throw new RuntimeException("Failed to get system session", e);
        }
        return AppContext.withSecurityContext(new SecurityContext(session), supplier::get);
    }

    private String getRedirectUrl() {
        GlobalConfig globalConfig = GoogleAuthenticationController.get().getConfiguration().getConfig(GlobalConfig.class);
        String appUrl = globalConfig.getWebAppUrl();
        if (appUrl.endsWith("/")) {
            appUrl = appUrl.substring(0, appUrl.length() - 1);
        }
        return appUrl + URL_PATH;
    }
}
