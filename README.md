# Обзор

Модуль Google-OAuth предоставляет дополнительный функционал по авторизации пользователей в системе через сторонние системы идентификации.

# Начало работы

## Установка Google-OAuth Add-on

Для того, чтобы у пользователя появилась возможность использовать add-on в CUBA.platform приложении
необходимо произвести установку add-on в локальный репозиторий пользователя. Рекомендуется делать эту
процедуру в Cuba Studio, для этого импортируйте add-on в Cuba Studio и выполните `Run` -> `Install app component`.

## Подключение Google-OAuth Add-on в CUBA.platform App

Данную операцию рекомендуется выполнять в Cuba Studio. Откройте проект в Cuba Studio и
перейдите к редактированию его своств (`Project properties` -> `Edit`). Нажмите на `+` расположенный
рядом с надписью `Custom components`. В выподающем спике (второе поле) должен быть доступен компонент
с именем `google-oauth` (в случае если вы выполнили его установку ранее). Выберите компонент, нажмите `ok`,
сохраните настройки проекта. Google-OAuth Add-on подключен.

Для активации REST-API модуля дополнительно убедитесь что в файле `web-app.properties` присутствует запись вида
```
cuba.restSpringContextConfig = com/groupstp/googleoauth/rest-dispatcher-spring.xml
```

## Описание предметной модели

Данный модуль расширяет базовые возможности системы по сторонней авторизации и идентификации пользователей. В любой схеме авторизации
всегда присутствуют как минимум два узла:
* __SP (Service Provider):__ провайдер сервиса. Данный узел представляет собой систему, которая предоставляет пользователю программный функционал.
* __IDP (Identity Provider):__ провайдер идентификации. Данный узел отвечает за авторизацию и идентификацию пользователя.
При успешной процедуре входа, данные передаются SP для осуществлении входа в саму систему.

Функциональные проблемы покрывающиеся данным модулем:
* Невозможность моментального создания всех требуемых пользователей в системе с заранее подготовленными данными.
* Небходимость иметь возможность авторизовывать автоматически новых пользователей из уже существующих внешних систем с получением готовых данных (избавление вводить информацию вновь).
* Идентификация и регистрация пользователей из внешних клиентских приложений (мобилки, вебсайт) при помощи удобного API.

На данный момент в модуль интегрирован IDP от компании Google, но имеется возможность расширить список поддерживаемых IDP.

Таким образом можно суммарно выделить три различных сценария использования данного модуля:
1. Система SP сама является клиентом IDP. Перенаправляет запрос на идентификацию во внешнюю систему, взамен получая обратно результат.
При положительном результате, авторизирует пользователя предоставляя доступ, а в случае отсутствия внутреннего пользователя, система используя внешние данные, создает и авторизирует пользователя автоматически.
```
SP -> IDP -> SP
```
2. Система SP является промежуточным звеном. Получая запросы от внешних клиентов с их базовыми данными на авторизацию, SP перенаправляет запрос на IDP, обрабатывает полученный результат, и, в случае успеха, выдает данные для доступа.
```
EClient -> SP -> IDP -> SP -> EClient
```
3. Система SP является конечной точкой цепочки авторизации. Внешние клиентские системы сами получают все необходимые данные, присылая конечный результат в SP.
Система валидирует полученные данные, и, в случае успеха, производит авторизацию пользователя.
```
EClient -> IDP -> EClient -> SP -> EClient
```

Рассмотрим каждый вариант использования более подробно.

### 1. Система является клиентом внешней системы аутентификации

Этот вариант является самым элементарным. Система при запросе авторизации пересылает запрос во внешнюю IDP. После процедуры идентификации в IDP,
SP получает ответ об успешности процесса. В случае успеха, производит поиск пользователя по данным IDP и производит его авторизацю внутри себя.
В случае отсутствия пользователя во внутренней системе выполняется алгоритм по его созданию и заполнению базовыми данными из IDP.

При использовании этого сценария, при подключении модуля, необходимо будет расширить экран входа для добавления кнопки по внешней авторизации:
```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<window xmlns="http://schemas.haulmont.com/cuba/window.xsd"
        class="....ExtLoginWindow"
        extends="com/haulmont/cuba/web/app/loginwindow/loginwindow.xml"
        messagesPack="..."
        xmlns:ext="http://schemas.haulmont.com/cuba/window-ext.xsd">
    <layout>
        <vbox id="loginWrapper">
            <vbox id="loginMainBox">
                <hbox id="loginTitleBox">
                    <label id="welcomeLabel"
                           visible="false"/>
                </hbox>
                <label id="neWelcomeLabel"
                       align="MIDDLE_CENTER"
                       ext:index="1"
                       stylename="c-login-caption"
                       value="Добро пожаловать!"/>
                <vbox id="loginForm"
                      align="MIDDLE_CENTER"
                      margin="true,false,false,false">
                    <button id="googleBtn"
                            align="MIDDLE_CENTER"
                            caption="Войти через Google"
                            icon="font-icon:GOOGLE"
                            invoke="signInGoogle"
                            width="100%"/>
                </vbox>
            </vbox>
        </vbox>
    </layout>
</window>
```
```java

import com.groupstp.googleoauth.data.GoogleUserData;
import com.groupstp.googleoauth.service.GoogleService;
import com.groupstp.googleoauth.service.SocialRegistrationService;
import com.groupstp.googleoauth.web.login.ExtAppLoginWindow;
import com.haulmont.cuba.core.global.GlobalConfig;
import com.haulmont.cuba.gui.executors.BackgroundWorker;
import com.haulmont.cuba.gui.executors.UIAccessor;
import com.haulmont.cuba.security.entity.User;
import com.haulmont.cuba.web.app.loginwindow.AppLoginWindow;
import com.haulmont.cuba.web.controllers.ControllerUtils;
import com.haulmont.cuba.web.security.ExternalUserCredentials;
import com.vaadin.server.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;


public class ExtLoginWindow extends AppLoginWindow {
    private final Logger log = LoggerFactory.getLogger(ExtAppLoginWindow.class);

    @Inject
    private BackgroundWorker backgroundWorker;
    @Inject
    private SocialRegistrationService socialRegistrationService;//сервис регистрации и нахождения пользователей
    @Inject
    private GoogleService googleService;//сервис IDP Google

    @Inject
    private GlobalConfig globalConfig;

    private URI redirectUri;
    private UIAccessor uiAccessor;
    private RequestHandler googleCallBackRequestHandler = this::handleGoogleCallBackRequest;

    @Override
    public void init(Map<String, Object> params) {
        super.init(params);

        this.uiAccessor = backgroundWorker.getUIAccessor();
    }

    //при нажатии на кнопку регистрируем слушателя на возврат указывая ссылку на перенаправление IDP
    public void signInGoogle() {
        VaadinSession.getCurrent().addRequestHandler(googleCallBackRequestHandler);

        redirectUri = Page.getCurrent().getLocation();

        String loginUrl = googleService.getLoginUrl(globalConfig.getWebAppUrl());
        Page.getCurrent().setLocation(loginUrl);
    }

    private boolean handleGoogleCallBackRequest(VaadinSession session, VaadinRequest request, VaadinResponse response) throws IOException {
        if (request.getParameter("code") != null) {
            uiAccessor.accessSynchronously(() -> {
                try {
                    String code = request.getParameter("code");//считываем полученный код от Google

                    GoogleUserData data = googleService.getUserData(globalConfig.getWebAppUrl(), code);//получаем данные от Google о пользователе
                    User user = socialRegistrationService.findUser(data);//находим пользователя или регистрируем его
                    if (user == null) {
                        throw new RuntimeException(getMessage("userNotFound"));
                    }
                    Locale locale = StringUtils.isEmpty(user.getLanguage()) ? Locale.getDefault() : new Locale(user.getLanguage());

                    app.getConnection().login(new ExternalUserCredentials(user.getLogin(), locale));//авторизовываем найденного пользователя внутри SP
                } catch (Exception e) {
                    showNotification(getMessage("unableToSignIn"), e.getMessage(), NotificationType.WARNING);
                    log.error("Unable to sign in", e);
                } finally {
                    session.removeRequestHandler(googleCallBackRequestHandler);
                }
            });
            ((VaadinServletResponse) response).getHttpServletResponse().sendRedirect(ControllerUtils.getLocationWithoutParams(redirectUri));
        }
        return false;
    }
}
```
Как видно по коду, экран входа SP располагает новую кнопку, по нажатию которой пользователь перенаправляется на страницу идентификации в SP. По возвращению,
в случае успеха, SP получает параметр code, считывает данные из IDP по этому коду, и авторизовывает пользователя у себя.

### 2. Система является промежуточным звеном

В данном контексте система SP является простым транслятором на идентификацию в IDP. Существует два вида трансляции:
- Частичный. На выходе SP предоставляет только конечную ссылку для авторизации в IDP. А для авторизации в самой системе потребует ранее полученные данные.
Шаги:
1. Произввести `GET` запрос на `url_сервера:порт/app/rest/google/get?redirect_url=редирект_страницы_обработчика`.
В данном случае получится адрес страницы IDP, а после успешной авторизации вернется код на указанную страницу редиректа.
2. После получение кода необходимо будет выполнить `POST` со страницы обработчика на `url_сервера:порт/app/rest/google/login` с json телом:
```
{
    "redirect_url": "редирект_страницы_обработчика",
    "code": "полученный_код"
}
```
После валидации SP и успеха входа, система вернет конечные токены доступа.

- Полный.
Данный механизм подразумевает что система SP сама произведет получение всех необходимых данные вплодь до проверки редиректов. С клиентской стороны необходимо только
создать `GET` запрос на адрес `url_сервера:порт/app/google/login?redirect_uri=редирект_адрес_куда_придут_конечные_данные_для_входа`.
После удачной полной авторизации на указанный uri придут данные для входа:
```
{
    "access_token": "b44261d3-77f5-4582-880d-6730eeb9f5d3",
    "token_type": "bearer",
    "refresh_token": "8fe5c457-ccdb-44c5-9f79-c5d1baadd774",
    "expires_in": 43199,
    "scope": "rest-api"
}
```

### 3. Система является валидатором конечных данных
Данная схема подразумевает что все операции с IDP будут проводится непосредственно самими внешними клиентами, а для входа в SP лишь
потребуется конечные данные для дополнительной валидации в IDP и выдачи доступа в самой системе SP. Для получения последнего необходимо будет направить `POST` запрос на *url_сервера:порт/app/rest/google/login* с json телом:
```
{
   "access_token":"токен_доступа",
}
```
или
```
{
   "id_token":"идентификационный_токен_внешней_системы",
}
```
в зависимости от типа используемой авторизации. Далее SP произведет валидацию и процесс внутренней авторизации, в случае успеха, так же в результате будут выданы конечные данные для входа.


## Описание основных классов бизнес логики.

В системе присутствует множество способов идентификации начиная от REST интерфейсов и заканчивая сервлетами.
Преимущественно вся логика обработки данных для входа извне находится в классах

```
com.groupstp.googleoauth.restapiюGoogleAuthenticationController
```
и
```
com.groupstp.googleoauth.web.GoogleAuthenticationFilter
```

Логика обработки приходящих данных (валидация, считывание внешних данных о пользователях) для IDP Google находится в классеЖ
```
com.groupstp.googleoauth.serviceюGoogleServiceBean
```

Логика по поиску и созданию пользователя расположена в классе:
```
com.groupstp.googleoauth.service.SocialRegistrationService
```
# Дополнение A: Свойства модуля.

## Основные свойства

### google.appId

* __Description:__ Идентификатор приложения SP в системе Google. Идентификатор необходим для распознания в Google работающей системы.

* __Type:__ Используется на middleware уровне

* __Interface:__ *GoogleConfig*

### google.appSecret

* __Description:__ Секретный ключ приложения SP в системе Google использующийся в паре с `google.appId`

* __Type:__ Используется на middleware уровне

* __Interface:__ *GoogleConfig*

### google.scope

* __Description:__ Количество собираемых данных при авторизации через Google. Хранится в виде json массива с перечислением требуемых данных.

* __Default value:__ `["https://www.googleapis.com/auth/plus.me","https://www.googleapis.com/auth/userinfo.email"]`

* __Type:__ Используется на middleware уровне

* __Interface:__ *GoogleConfig*

### google.createUser.domains

* __Description:__ Домены аккаунтов разрешенных для создания пользователей в случае их отсутствия в системе. При пустом значении пользователи не будут созданы. Для того чтобы создавать всегда пользователей
достаточно прописать *gmail.com*

* __Type:__ Используется на middleware уровне

* __Interface:__ *GoogleConfig*

### google.external.appId

* __Description:__ Внешний Google идентификатор клиентских приложений, который используется при валидации данных от клиентских приложений.

* __Type:__ Используется на middleware уровне

* __Interface:__ *GoogleConfig*

### google.external.accessTokenLoginEnabled

* __Description:__ Разрешена ли идентификация клиентских приложений через access token

* __Default value:__ *true*

* __Type:__ Используется на middleware уровне

* __Interface:__ *GoogleConfig*

### google.external.idTokenLoginEnabled

* __Description:__ Разрешена ли идентификация клиентских приложений через id token

* __Default value:__ *true*

* __Type:__ Используется на middleware уровне

* __Interface:__ *GoogleConfig*

### google.createUser.group

* __Description:__ Группа пользователей по умолчанию назначаемая новым пользователям, созданным при первой авторизации в системе через данный модуль.

* __Default value:__ *0fa2b1a5-1d68-4d69-9fbd-dff348347f93*

* __Type:__ Используется на middleware уровне

* __Interface:__ *GoogleConfig*
