package com.groupstp.googleoauth.service;

import com.groupstp.googleoauth.data.GoogleUserData;
import com.groupstp.googleoauth.data.OAuth2ResponseType;

/**
 * Google authentication service
 */
public interface GoogleService {

    String NAME = "googleoauth_GoogleService";

    /**
     * Get Google login page url
     *
     * @param appUrl       current application url.
     * @param responseType type of response login.
     * @return remote Google login url.
     */
    String getLoginUrl(String appUrl, OAuth2ResponseType responseType);

    /**
     * Retrieve Google authentication data by code
     *
     * @param appUrl current application url.
     * @param code   authentication code.
     * @return authenticated user data
     */
    GoogleUserData getUserData(String appUrl, String code);
}