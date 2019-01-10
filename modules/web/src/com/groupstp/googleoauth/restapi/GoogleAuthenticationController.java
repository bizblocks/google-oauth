package com.groupstp.googleoauth.restapi;

import com.groupstp.googleoauth.data.GoogleUserData;
import com.groupstp.googleoauth.data.OAuth2ResponseType;
import com.groupstp.googleoauth.service.GoogleService;
import com.groupstp.googleoauth.service.SocialRegistrationService;
import com.haulmont.cuba.core.global.Configuration;
import com.haulmont.cuba.core.global.MessageTools;
import com.haulmont.cuba.core.sys.AppContext;
import com.haulmont.cuba.core.sys.SecurityContext;
import com.haulmont.cuba.security.app.TrustedClientService;
import com.haulmont.cuba.security.entity.User;
import com.haulmont.cuba.security.global.LoginException;
import com.haulmont.cuba.security.global.UserSession;
import com.haulmont.cuba.web.auth.WebAuthConfig;
import com.haulmont.restapi.auth.OAuthTokenIssuer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Collections;
import java.util.function.Supplier;

/**
 * Google authentication rest controller
 *
 * @author adiatullin
 */
@RestController
@RequestMapping("/google")
public class GoogleAuthenticationController {

    private static GoogleAuthenticationController instance;

    @Inject
    private Configuration configuration;
    @Inject
    private MessageTools messageTools;
    @Inject
    private OAuthTokenIssuer oAuthTokenIssuer;
    @Inject
    private TrustedClientService trustedClientService;
    @Inject
    private GoogleService googleService;
    @Inject
    private SocialRegistrationService socialRegistrationService;

    public static GoogleAuthenticationController get() {
        return instance;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public MessageTools getMessageTools() {
        return messageTools;
    }

    public OAuthTokenIssuer getOAuthTokenIssuer() {
        return oAuthTokenIssuer;
    }

    public TrustedClientService getTrustedClientService() {
        return trustedClientService;
    }

    public GoogleService getGoogleService() {
        return googleService;
    }

    public SocialRegistrationService getSocialRegistrationService() {
        return socialRegistrationService;
    }

    @PostConstruct
    private void init() {
        instance = this;
    }

    /**
     * @param url url of redirection
     * @return Redirecting google login url
     */
    @RequestMapping(method = RequestMethod.GET, value = "/get")
    public ResponseEntity get(@RequestParam(value = "redirect_url") String url) {
        String loginUrl = getAsPrivilegedUser(() -> googleService.getLoginUrl(url, OAuth2ResponseType.CODE_TOKEN));

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.LOCATION, loginUrl);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    /**
     * Authenticate the user from google service by provided code
     *
     * @param code google code
     * @return token information
     */
    @RequestMapping(method = RequestMethod.POST, value = "/login")
    public ResponseEntity<OAuth2AccessToken> login(@RequestParam(value = "redirect_url") String url, @RequestParam("code") String code) {
        User user = getAsPrivilegedUser(() -> {
            GoogleUserData userData = googleService.getUserData(url, code);
            if (userData == null) {
                throw new RuntimeException("User data not found");
            }
            User currentUser = socialRegistrationService.findUser(userData);
            if (currentUser == null) {
                throw new RuntimeException("User not found with email: " + userData.getEmail());
            }
            return currentUser;
        });

        OAuthTokenIssuer.OAuth2AccessTokenResult tokenResult = oAuthTokenIssuer.issueToken(user.getLogin(),
                messageTools.getDefaultLocale(), Collections.emptyMap());

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        return new ResponseEntity<>(tokenResult.getAccessToken(), headers, HttpStatus.OK);
    }

    private <T> T getAsPrivilegedUser(Supplier<T> supplier) {
        UserSession session;
        try {
            WebAuthConfig webAuthConfig = configuration.getConfig(WebAuthConfig.class);
            session = trustedClientService.getSystemSession(webAuthConfig.getTrustedClientPassword());
        } catch (LoginException e) {
            throw new RuntimeException("Failed to get system session", e);
        }
        return AppContext.withSecurityContext(new SecurityContext(session), supplier::get);
    }
}
