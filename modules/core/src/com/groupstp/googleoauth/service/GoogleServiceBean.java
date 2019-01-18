package com.groupstp.googleoauth.service;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Person;
import com.groupstp.googleoauth.config.GoogleConfig;
import com.groupstp.googleoauth.data.GoogleUserData;
import com.haulmont.cuba.core.global.Configuration;
import com.haulmont.cuba.core.sys.AppContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service(GoogleService.NAME)
public class GoogleServiceBean implements GoogleService {

    private final Logger log = LoggerFactory.getLogger(GoogleService.class);

    @Inject
    private Configuration configuration;

    private GoogleConfig config;
    private final List<String> scopes = new ArrayList<>();
    private JsonFactory jsonFactory;
    private NetHttpTransport transport;
    private AuthorizationCodeFlow flow;

    // TODO rewrite
    // Coded that way because @EventListener annotation don't work
    // https://github.com/cuba-platform/cuba/issues/771
    @PostConstruct
    protected void prepareBean() {
        AppContext.Listener listener = new AppContext.Listener() {
            @Override
            public void applicationStarted() {
                try {
                    jsonFactory = JacksonFactory.getDefaultInstance();
                    config = configuration.getConfig(GoogleConfig.class);
                    jsonFactory.createJsonParser(config.getGoogleScope()).parseArray(scopes, String.class);
                    transport = GoogleNetHttpTransport.newTrustedTransport();
                    File dataStoreFile = new File("google-oauth.store");
                    FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(dataStoreFile);
                    flow = new GoogleAuthorizationCodeFlow.Builder(
                            transport,
                            jsonFactory,
                            config.getGoogleAppId(),
                            config.getGoogleAppSecret(),
                            scopes)
                            .setDataStoreFactory(dataStoreFactory)
                            // commented because google api not used in anywhere else
                            // .setAccessType("offline")
                            .build();
                } catch (Exception e) {
                    log.error("Thrown in prepareBean of GoogleServiceBean: ", e);
                }
            }

            @Override
            public void applicationStopped() {

            }
        };
        AppContext.addListener(listener);
    }

    @Override
    public String getLoginUrl(String appUrl) {
        return flow.newAuthorizationUrl()
                .setClientId(config.getGoogleAppId())
                .setScopes(scopes)
                .setRedirectUri(appUrl)
                .build();
    }

    @Override
    public GoogleUserData getUserData(String appUrl, String code) {
        try {
            TokenResponse tokenResponse = flow.newTokenRequest(code)
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
            Credential credential = new GoogleCredential()
                    .setAccessToken(accessToken);
            Plus plus = new Plus.Builder(transport, jsonFactory, credential)
                    .build();
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
}