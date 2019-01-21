package com.groupstp.googleoauth.service;

import com.groupstp.googleoauth.data.GoogleUserData;

/**
 * Google authentication service
 */
public interface GoogleService {

    String NAME = "googleoauth_GoogleService";

    /**
     * Get Google login page url
     *
     * @return remote Google login url.
     */
    String getLoginUrl(String appUrl);

    /**
     * Retrieve Google authentication data by code
     *
     * @param appUrl current application url.
     * @param code   authentication code.
     * @return authenticated user data
     */
    GoogleUserData getUserData(String appUrl, String code);

    /**
     * Retrieve Google authentication data by already defined access token
     *
     * @param accessToken direct access token
     * @return authenticated user data
     */
    GoogleUserData getUserDataByAccessToken(String accessToken);

    /**
     * Retrieve Google authentication data by already defined id token
     *
     * @param idToken direct id token
     * @return authenticated user data
     */
    GoogleUserData getUserDataByIdToken(String idToken);
}