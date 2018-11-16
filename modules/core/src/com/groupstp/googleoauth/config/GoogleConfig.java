package com.groupstp.googleoauth.config;

import com.haulmont.cuba.core.config.Config;
import com.haulmont.cuba.core.config.Property;
import com.haulmont.cuba.core.config.Source;
import com.haulmont.cuba.core.config.SourceType;
import com.haulmont.cuba.core.config.defaults.Default;
import com.haulmont.cuba.core.config.type.Factory;
import com.haulmont.cuba.core.config.type.UuidTypeFactory;

import java.util.UUID;

@Source(type = SourceType.DATABASE)
public interface GoogleConfig extends Config {

    @Property("google.appId")
    @Default("")
    String getGoogleAppId();

    @Property("google.appSecret")
    @Default("")
    String getGoogleAppSecret();

    @Property("google.scope")
    @Default("[\"https://www.googleapis.com/auth/plus.me\"," +
            "\"https://www.googleapis.com/auth/userinfo.email\"]")
    String getGoogleScope();

    @Property("google.createUser.domains")
    String getAcceptDomains();

    @Property("google.createUser.group")
    @Default("0fa2b1a5-1d68-4d69-9fbd-dff348347f93")
    @Factory(factory = UuidTypeFactory.class)
    UUID getDefaultGroupId();
}