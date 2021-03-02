# Подготовка хранилища для ключей.

## Первый запуск 

В `WORK_DIR` нужно поместить `keystore.p12`

`
scp  keystore.p12 user@office.dev:/home/user/crm-ldap/keystore.p12 
`

Запускаем на сервере `prepare.sh`

# cron
Добавим в cron - запуск раз в месяц

Продление сертификатов и создание хранилища ключей

`
0 0 1 * *   ./path-to/prepare.sh
`
