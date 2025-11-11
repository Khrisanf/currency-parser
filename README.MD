# Currency Parser

Учебный многопоточный сервис на Spring Boot, который получает курсы валют с сайта ЦБ РФ и сохраняет их во встроенную БД H2. Результат доступен через REST-эндпоинт контроллера.

## Быстрый старт

Требования

`JDK 17+`

`Maven 3.8+`

## Сборка и запуск (fat-jar)

`mvn -DskipTests package`  
`java -jar target/*-SNAPSHOT.jar`


По умолчанию приложение поднимается на http://localhost:8080.

### Конфигурация

Все ключевые настройки находятся в `application.properties`:

`spring.application.name=currency-parser`  
`server.port=8080`

### H2 + JPA
spring.datasource.url=jdbc:h2:mem:rates;DB_CLOSE_DELAY=-1;MODE=PostgreSQL  
spring.datasource.driver-class-name=org.h2.Driver  
spring.datasource.username=sa  
spring.datasource.password=  
spring.jpa.hibernate.ddl-auto=update  
spring.jpa.open-in-view=false

### H2 console
spring.h2.console.enabled=true  
spring.h2.console.path=/h2-console

# Источник данных

cbr.url=https://www.cbr.ru/scripts/XML_daily.asp

# Планировщик
`scheduler.update-ms=600000`

# Параллельные батчи
`app.rates.batch-threads=3`

# Кастомные экзекьюторы
`app.executors.io.core-size=2`  
`app.executors.io.max-size=4`  
`app.executors.io.queue-capacity=200`  
`app.executors.io.keep-alive-seconds=30`  
`app.executors.io.thread-name-prefix=IO-`

`app.executors.cpu.size=4`  
`app.executors.cpu.thread-name-prefix=CPU-`

### Как пользоваться
- Основной эндпоинт  
  `GET /api/rates`


Запускает сбор данных и возвращает актуальные данные по курсам валют (реализация в RatesController).

Совет: можно воспользоваться Postman для удобства 

#### Пример запроса:

curl -s http://localhost:8080/api/rates

- Консоль H2  
  `GET /h2-console`

`JDBC URL: jdbc:h2:mem:rates`

Пользователь: sa (без пароля)  
Позволяет посмотреть содержимое таблиц во встроенной БД.

### Что внутри

- Парсинг источника: CbrClient — HTTP-клиент, берёт данные по адресу из cbr.url.

- Сервис обновления: RateUpdateService — координация загрузки и обновления данных; поддерживает периодическое выполнение согласно scheduler.update-ms.

- Контроллер: RatesController — REST-точки доступа к данным.

- Сущность/репозиторий: CurrencyRate, CurrencyRateRepository — модель и доступ к H2 через Spring Data JPA.

- Многопоточность: ConcurrencyConfig, IoExecutorProperties, CpuExecutorProperties — кастомные пулы потоков для IO и CPU задач (имена потоков с префиксами IO- и CPU-).

- Инициализация приложения: CurrencyParserApplication.

- Техническая заглушка: NotImplementedYetException — для пометки незавершённых частей (пока не используется).

### Планировщик

Приложение поддерживает периодическое обновление курсов. Период задаётся свойством:

scheduler.update-ms=600000


(по умолчанию — 10 минут). Реализация расписания находится в RateUpdateService.
Покажет обновленные данные по валютам, если такие будут.

### Тесты

Проект содержит набор юнит-тестов:

- RatesControllerTest

- CurrencyParserApplicationTests

- CurrencyRateRepositoryTest

- RateUpdateServiceTest

### Запуск:

`mvn test`

### Логи

Для наглядности в именах потоков используются префиксы:

`IO-задачи: IO-…`

`CPU-задачи: CPU-…`

Это помогает проверить работу кастомных ExecutorService в консоли.

### Назначение

Проект создан для демонстрации:

работающего Spring Boot сервиса,

многопоточности через кастомные ExecutorService,

плановой фоновой задачи обновления данных,

хранения результатов во встроенной БД H2,

доступа к данным через REST-эндпоинт.