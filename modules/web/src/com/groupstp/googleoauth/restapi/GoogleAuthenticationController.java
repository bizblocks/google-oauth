package com.groupstp.googleoauth.restapi;

import com.groupstp.googleoauth.data.GoogleUserData;
import com.groupstp.googleoauth.restapi.dto.LoginCredential;
import com.groupstp.googleoauth.service.GoogleService;
import com.groupstp.googleoauth.service.SocialRegistrationService;
import com.haulmont.addon.restapi.api.auth.OAuthTokenIssuer;
import com.haulmont.addon.restapi.api.exception.RestAPIException;
import com.haulmont.cuba.core.global.Configuration;
import com.haulmont.cuba.core.global.MessageTools;
import com.haulmont.cuba.core.sys.AppContext;
import com.haulmont.cuba.core.sys.SecurityContext;
import com.haulmont.cuba.security.app.TrustedClientService;
import com.haulmont.cuba.security.entity.User;
import com.haulmont.cuba.security.global.LoginException;
import com.haulmont.cuba.security.global.UserSession;
import com.haulmont.cuba.web.auth.WebAuthConfig;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.web.bind.annotation.*;

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
        try {
            String loginUrl = getAsPrivilegedUser(() -> googleService.getLoginUrl(url));

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.LOCATION, loginUrl);
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (Exception e) {
            throw new RestAPIException("Error", "Failed to process login url", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Authenticate the user from google service by provided code
     *
     * @param dto google authentication data transfer object
     * @return token information
     */
    @RequestMapping(method = RequestMethod.POST, value = "/login")
    public ResponseEntity<OAuth2AccessToken> login(@RequestBody LoginCredential dto) {
        try {
            User user = getAsPrivilegedUser(() -> {
                GoogleUserData userData;
                if (!StringUtils.isEmpty(dto.getAccessToken())) {
                    userData = googleService.getUserDataByAccessToken(dto.getAccessToken());
                } else if (!StringUtils.isEmpty(dto.getIdToken())) {
                    userData = googleService.getUserDataByIdToken(dto.getIdToken());
                } else {
                    if (StringUtils.isEmpty(dto.getRedirectUrl()) || StringUtils.isEmpty(dto.getCode())) {
                        throw new RestAPIException("Error", "Code and original redirect url are required", HttpStatus.BAD_REQUEST);
                    }
                    userData = googleService.getUserData(dto.getRedirectUrl(), dto.getCode());
                }
                if (userData == null) {
                    throw new RestAPIException("Error", "User data not found", HttpStatus.BAD_REQUEST);
                }
                User currentUser = socialRegistrationService.findUser(userData);
                if (currentUser == null) {
                    throw new RestAPIException("Error", "User not found with email: " + userData.getEmail(), HttpStatus.BAD_REQUEST);
                }
                return currentUser;
            });

            OAuthTokenIssuer.OAuth2AccessTokenResult tokenResult = oAuthTokenIssuer.issueToken(user.getLogin(),
                    messageTools.getDefaultLocale(), Collections.emptyMap());

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
            headers.set(HttpHeaders.PRAGMA, "no-cache");
            return new ResponseEntity<>(tokenResult.getAccessToken(), headers, HttpStatus.OK);
        } catch (Exception e) {
            if (e instanceof RestAPIException) {
                throw (RestAPIException) e;
            }
            throw new RestAPIException("Error", "Failed to process login", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
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
