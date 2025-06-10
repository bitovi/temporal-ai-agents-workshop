FROM maven

WORKDIR /usr/src/app

COPY 1-intro-to-ai-agents/src src
COPY 1-intro-to-ai-agents/pom.xml pom.xml
COPY 1-intro-to-ai-agents/config.properties config.properties


RUN mvn compile

CMD []
