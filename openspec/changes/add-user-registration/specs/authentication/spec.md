## ADDED Requirements

### Requirement: Регистрация пользователя
Система должна принимать запрос на регистрацию с именем пользователя и паролем, создавать учётную запись и отвечать её идентификатором и именем. Имя пользователя должно быть уникальным без учёта регистра. Запрос на регистрацию не должен требовать токена, а ответ не должен содержать присланный пароль.

#### Scenario: Успешная регистрация
- **WHEN** клиент отправляет запрос на регистрацию с непустым именем и паролем, удовлетворяющим правилам, и такого имени ещё нет
- **THEN** система создаёт учётную запись и отвечает `201 Created` с её идентификатором и именем, но без пароля в каком-либо виде

#### Scenario: Регистрация не требует токена
- **WHEN** клиент отправляет запрос на регистрацию без заголовка `Authorization`
- **THEN** система рассматривает запрос по существу, а не отклоняет его из-за отсутствия токена

#### Scenario: Имя уже занято
- **WHEN** клиент отправляет запрос на регистрацию с именем, которое уже занято
- **THEN** система отвечает `409 Conflict`, не создаёт вторую учётную запись и не меняет пароль существующей

#### Scenario: Имя занято в другом регистре
- **WHEN** клиент отправляет запрос на регистрацию с именем, отличающимся от уже занятого только регистром букв
- **THEN** система отвечает `409 Conflict` и не создаёт вторую учётную запись

#### Scenario: Одновременная регистрация одного имени
- **WHEN** два запроса на регистрацию с одним и тем же именем обрабатываются одновременно
- **THEN** ровно один из них отвечает `201 Created`, второй отвечает `409 Conflict`, и в системе оказывается одна учётная запись с этим именем

#### Scenario: Пустое имя или пароль
- **WHEN** клиент отправляет запрос на регистрацию с пустым или отсутствующим именем либо паролем
- **THEN** система отвечает `400 Bad Request` и не создаёт учётную запись

#### Scenario: Слишком длинное имя или пароль
- **WHEN** клиент отправляет запрос на регистрацию, в котором имя или пароль превышает допустимую длину
- **THEN** система отвечает `400 Bad Request` и не создаёт учётную запись

#### Scenario: Присланный пароль нигде не раскрывается
- **WHEN** запрос на регистрацию отклонён по любой причине, при любой настроенной подробности диагностики
- **THEN** ни ответ, ни диагностическая запись не содержат присланный пароль

### Requirement: Пароль должен удовлетворять правилам стойкости
Система должна отклонять регистрацию с паролем, который не удовлетворяет правилам: не короче восьми символов и содержит заглавную букву, строчную букву, цифру и специальный символ. Ответ должен называть невыполненные правила, чтобы клиент понял, что исправить, и не должен повторять сам пароль.

#### Scenario: Пароль короче минимальной длины
- **WHEN** клиент отправляет запрос на регистрацию с паролем короче восьми символов
- **THEN** система отвечает `400 Bad Request`, называет нарушенное правило длины и не создаёт учётную запись

#### Scenario: В пароле нет одного из требуемых видов символов
- **WHEN** клиент отправляет запрос на регистрацию с паролем достаточной длины, в котором нет заглавной буквы, либо строчной, либо цифры, либо специального символа
- **THEN** система отвечает `400 Bad Request`, называет именно то правило, которое не выполнено, и не создаёт учётную запись

#### Scenario: Нарушено несколько правил сразу
- **WHEN** клиент отправляет запрос на регистрацию с паролем, нарушающим несколько правил
- **THEN** ответ перечисляет все нарушенные правила, а не только первое из них

#### Scenario: Пароль ровно на границе допустимого
- **WHEN** клиент отправляет запрос на регистрацию с паролем длиной ровно восемь символов, содержащим все четыре требуемых вида символов
- **THEN** система создаёт учётную запись и отвечает `201 Created`

### Requirement: Пароли хранятся только в виде хеша
Система не должна сохранять пароль в виде, из которого его можно прочитать. Пароль должен храниться результатом односторонней функции с индивидуальной для каждой записи солью, так что одинаковые пароли двух пользователей дают разные сохранённые значения.

#### Scenario: В хранилище нет открытого пароля
- **WHEN** пользователь зарегистрирован
- **THEN** сохранённые данные этого пользователя не содержат присланный пароль ни в открытом виде, ни в виде, обратимом без перебора

#### Scenario: Одинаковые пароли хранятся по-разному
- **WHEN** два пользователя зарегистрированы с одним и тем же паролем
- **THEN** сохранённые для них значения различаются

#### Scenario: Сохранённое значение позволяет проверить пароль
- **WHEN** пользователь входит с тем же паролем, с которым регистрировался
- **THEN** система признаёт пароль верным, сверяя его с сохранённым значением

## MODIFIED Requirements

### Requirement: Log in with username and password
The system shall accept a login request containing a username and password and, when the credential verification service approves them, respond with a bearer token that can be used to authenticate subsequent requests. Credentials shall be approved only when the username identifies a registered user and the password matches the value stored for that user. The system shall bound the accepted length of both fields, and shall never disclose a submitted password.

#### Scenario: Successful login
- **WHEN** a client submits a login request with a non-blank username and non-blank password
- **THEN** the system responds with `200 OK` and a token in the response body

#### Scenario: Missing username or password
- **WHEN** a client submits a login request with a blank or missing username or password
- **THEN** the system responds with `400 Bad Request` and does not issue a token

#### Scenario: Credentials rejected
- **WHEN** a client submits a login request and the credential verification service does not approve the given username and password
- **THEN** the system responds with `401 Unauthorized` and does not issue a token

#### Scenario: Oversized credentials
- **WHEN** a client submits a login request whose username or password exceeds the configured maximum length
- **THEN** the system responds with `400 Bad Request` and does not issue a token

#### Scenario: Submitted password is never disclosed
- **WHEN** a login request is rejected for any reason, at any diagnostic verbosity the system can be configured to use
- **THEN** neither the response nor any diagnostic entry contains the submitted password

#### Scenario: Rejected login is recorded
- **WHEN** the system rejects a login attempt
- **THEN** the system records a diagnostic entry noting the rejection and its reason, without the submitted password

#### Scenario: Вход зарегистрированного пользователя
- **WHEN** зарегистрированный пользователь входит с тем именем и паролем, с которыми регистрировался
- **THEN** система отвечает `200 OK` и выдаёт токен

#### Scenario: Пароль не тот
- **WHEN** клиент входит с именем существующего пользователя и неверным паролем
- **THEN** система отвечает `401 Unauthorized` и не выдаёт токен

#### Scenario: Имя пользователя не зарегистрировано
- **WHEN** клиент входит с именем, под которым никто не зарегистрирован
- **THEN** система отвечает `401 Unauthorized` и не выдаёт токен

#### Scenario: Ответ не выдаёт, существует ли пользователь
- **WHEN** один вход отклонён из-за неверного пароля, а другой — из-за незарегистрированного имени
- **THEN** оба ответа неразличимы по статусу и содержимому, так что по ним нельзя определить, существует ли такое имя

#### Scenario: Имя пользователя при входе не зависит от регистра
- **WHEN** зарегистрированный пользователь входит, написав своё имя в другом регистре букв
- **THEN** система отвечает `200 OK` и выдаёт токен

## REMOVED Requirements

### Requirement: Stub credential verification cannot run in production
Причина: заглушка, одобрявшая любые учётные данные, удалена вместе с профильным запретом на её запуск. Проверка учётных данных теперь настоящая во всех профилях, поэтому запрещать нечего.
