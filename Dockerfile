FROM maven

WORKDIR /usr/src/app

COPY llm-workflows-temporal-java/src src
COPY llm-workflows-temporal-java/pom.xml pom.xml
COPY llm-workflows-temporal-java/config.properties config.properties


RUN mvn compile

CMD []
