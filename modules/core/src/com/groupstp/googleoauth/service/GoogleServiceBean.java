package com.groupstp.googleoauth.service;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Person;
import com.groupstp.googleoauth.config.GoogleConfig;
import com.groupstp.googleoauth.data.GoogleUserData;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@Service(GoogleService.NAME)
public class GoogleServiceBean implements GoogleService {

    private static final Logger log = LoggerFactory.getLogger(GoogleServiceBean.class);

    @Inject
    protected GoogleConfig config;


    @Override
    public String getLoginUrl(String appUrl) {
        return getFlow()
                .newAuthorizationUrl()
                .setRedirectUri(appUrl)
                .build();
    }

    @Override
    public GoogleUserData getUserData(String appUrl, String code) {
        try {
            TokenResponse tokenResponse = getFlow()
                    .newTokenRequest(code)
                    .setRedirectUri(appUrl)
                    .execute();
            return getUserData(tokenResponse.getAccessToken());
        } catch (Exception e) {
            log.error("Can't get user data", e);
            return null;
        }
    }

    @Override
    public GoogleUserData getUserData(String accessToken) {
        if (StringUtils.isEmpty(accessToken)) {
            log.warn("Access token is empty");
            return null;
        }

        Person person;
        try {
            Plus plus = getUserService(accessToken);
            person = plus.people().get("me").execute();
        } catch (Exception e) {
            log.error("Can't get user data", e);
            return null;
        }

        List<Person.Emails> emails = person.getEmails();
        if (emails.size() <= 0) {
            log.error("Emails size 0");
            return null;
        }

        String email = emails.get(0).getValue();
        String id = person.getId();
        String name = person.getDisplayName();
        String firstName = person.getName() == null ? null : person.getName().getGivenName();
        String middleName = person.getName() == null ? null : person.getName().getMiddleName();
        String lastName = person.getName() == null ? null : person.getName().getFamilyName();
        String domain = person.getDomain();

        return new GoogleUserData(id, name, firstName, middleName, lastName, email, domain);
    }

    protected AuthorizationCodeFlow getFlow() {
        try {
            List<String> scopes = new ArrayList<>();
            JacksonFactory.getDefaultInstance().createJsonParser(config.getGoogleScope()).parseArray(scopes, String.class);

            return new GoogleAuthorizationCodeFlow.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JacksonFactory.getDefaultInstance(),
                    config.getGoogleAppId(),
                    config.getGoogleAppSecret(),
                    scopes)
                    .build();
        } catch (Exception e) {
            log.error("Failed to prepare Google Authorization workflow", e);
            throw new RuntimeException("Google authorization workflow failed");
        }
    }

    protected Plus getUserService(String accessToken) {
        try {
            Credential credential = new GoogleCredential()
                    .setAccessToken(accessToken);
            return new Plus.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JacksonFactory.getDefaultInstance(),
                    credential)
                    .build();
        } catch (Exception e) {
            log.error("Failed to prepare connection to Google Plus", e);
            throw new RuntimeException("Google service connection failed");
        }
    }
}